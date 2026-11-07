package com.krinry.scancraft.cv

import android.graphics.Bitmap
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object EdgeDetector {

    /**
     * Fast, robust document boundary detection on an Android Bitmap.
     * Uses downsampling, 5x5 morphological closing for text erasure,
     * Gaussian smoothing, Sobel edge tensors, central blob isolation,
     * and inscribed maximum-area 4-corner quad fitting.
     */
    fun detectInBitmap(bitmap: Bitmap): DocumentQuadrilateral {
        val width = bitmap.width
        val height = bitmap.height
        val fallback = DocumentQuadrilateral.defaultForDimensions(width.toFloat(), height.toFloat())
        if (width < 64 || height < 64) return fallback

        // Scale down bitmap to max 240px for fast computation
        val maxDim = max(width, height)
        val scale = 240f / maxDim
        val sw = (width * scale).toInt().coerceAtLeast(32)
        val sh = (height * scale).toInt().coerceAtLeast(32)

        val scaledBmp = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val pixels = IntArray(sw * sh)
        scaledBmp.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (scaledBmp != bitmap) {
            scaledBmp.recycle()
        }

        // Grayscale conversion
        val gray = IntArray(sw * sh)
        for (i in 0 until sw * sh) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 77 + g * 150 + b * 29) shr 8
        }

        return detectPipeline(gray, sw, sh, width.toFloat() / sw, height.toFloat() / sh, fallback)
    }

    /**
     * Fast real-time 60fps edge detection on raw CameraX YUV luma plane.
     */
    fun detectInYuv(yuvData: ByteArray, width: Int, height: Int): DocumentQuadrilateral {
        val fallback = DocumentQuadrilateral.defaultForDimensions(width.toFloat(), height.toFloat())
        if (width < 64 || height < 64 || yuvData.isEmpty()) {
            return fallback
        }

        val maxDim = max(width, height)
        val step = max(1, maxDim / 180)
        val sw = width / step
        val sh = height / step

        if (sw < 32 || sh < 32) return fallback

        val gray = IntArray(sw * sh)
        for (y in 0 until sh) {
            val srcY = y * step
            for (x in 0 until sw) {
                val srcX = x * step
                gray[y * sw + x] = yuvData[srcY * width + srcX].toInt() and 0xFF
            }
        }

        return detectPipeline(gray, sw, sh, step.toFloat(), step.toFloat(), fallback)
    }

    private fun detectPipeline(
        gray: IntArray,
        sw: Int,
        sh: Int,
        scaleX: Float,
        scaleY: Float,
        fallback: DocumentQuadrilateral
    ): DocumentQuadrilateral {
        // 1. Morphological Closing (5x5 Dilation + 5x5 Erosion) to erase dark interior text
        val closed = erode5x5(dilate5x5(gray, sw, sh), sw, sh)
        val smooth = gaussianBlur3x3(closed, sw, sh)

        // 2. Directional Sobel Gradients
        val gx = IntArray(sw * sh)
        val gy = IntArray(sw * sh)
        val mag = IntArray(sw * sh)
        var gradSum = 0L
        var gradCount = 0

        for (y in 2 until sh - 2) {
            val yOffset = y * sw
            val prev = (y - 1) * sw
            val next = (y + 1) * sw
            for (x in 2 until sw - 2) {
                val vx = -smooth[prev + x - 1] + smooth[prev + x + 1] -
                         2 * smooth[yOffset + x - 1] + 2 * smooth[yOffset + x + 1] -
                         smooth[next + x - 1] + smooth[next + x + 1]

                val vy = -smooth[prev + x - 1] - 2 * smooth[prev + x] - smooth[prev + x + 1] +
                         smooth[next + x - 1] + 2 * smooth[next + x] + smooth[next + x + 1]

                val m = hypot(vx.toFloat(), vy.toFloat()).toInt()
                val idx = yOffset + x
                gx[idx] = vx
                gy[idx] = vy
                mag[idx] = m
                gradSum += m
                gradCount++
            }
        }

        val meanGrad = (gradSum / max(1, gradCount)).toInt()
        val noiseFloor = max(12, meanGrad)

        // 3. Center vs Border Mean
        var centerSum = 0L; var centerCnt = 0L
        var borderSum = 0L; var borderCnt = 0L
        for (y in 2 until sh - 2) {
            val row = y * sw
            for (x in 2 until sw - 2) {
                val v = smooth[row + x]
                if (x > sw * 3 / 10 && x < sw * 7 / 10 && y > sh * 3 / 10 && y < sh * 7 / 10) {
                    centerSum += v
                    centerCnt++
                } else if (x < sw / 8 || x > sw * 7 / 8 || y < sh / 8 || y > sh * 7 / 8) {
                    borderSum += v
                    borderCnt++
                }
            }
        }

        val centerMean = (centerSum / max(1L, centerCnt)).toInt()
        val borderMean = (borderSum / max(1L, borderCnt)).toInt()

        var bestQuad: List<PointF>? = null
        var bestScore = -1.0f

        val alphas = floatArrayOf(0.20f, 0.32f, 0.45f, 0.58f, 0.70f, 0.82f)
        val pageBrighterOptions = booleanArrayOf(centerMean >= borderMean, centerMean < borderMean)

        for (mode in 0 until 2) {
            val pageIsBrighter = pageBrighterOptions[mode]
            if (mode == 1 && abs(centerMean - borderMean) > 25) continue

            for (alpha in alphas) {
                val t = (borderMean + alpha * (centerMean - borderMean)).toInt().coerceIn(15, 240)
                val boundary = extractCentralBlobBoundary(smooth, sw, sh, t, pageIsBrighter)
                if (boundary.size < 16) continue

                val hull = computeConvexHull(boundary)
                val candidate = extractBestQuadFromHull(hull) ?: continue
                val score = scoreQuadEdgeSupport(candidate, gx, gy, sw, sh, noiseFloor)
                if (score > bestScore) {
                    bestScore = score
                    bestQuad = candidate
                }
            }
        }

        // 4. Fallback Candidate: Radial Edge Raycasting
        for (mult in intArrayOf(12, 16, 22)) {
            val radThresh = max(20, (meanGrad * mult) / 10)
            val radialPts = extractRadialEdgePoints(gx, gy, mag, sw, sh, radThresh)
            if (radialPts.size >= 12) {
                val hull = computeConvexHull(radialPts)
                val candidate = extractBestQuadFromHull(hull) ?: continue
                val score = scoreQuadEdgeSupport(candidate, gx, gy, sw, sh, noiseFloor)
                if (score > bestScore) {
                    bestScore = score
                    bestQuad = candidate
                }
            }
        }

        // 5. Fallback Candidate: Approximate from all threshold edge pixels if no blob found
        if (bestQuad == null) {
            val threshold = max(20, (meanGrad * 1.2f).toInt())
            val edgePoints = mutableListOf<PointF>()
            val borderMargin = 5
            for (y in borderMargin until sh - borderMargin step 2) {
                val yOffset = y * sw
                for (x in borderMargin until sw - borderMargin step 2) {
                    if (mag[yOffset + x] > threshold) {
                        edgePoints.add(PointF(x.toFloat(), y.toFloat()))
                    }
                }
            }
            if (edgePoints.size >= 12) {
                val hull = computeConvexHull(edgePoints)
                val candidate = extractBestQuadFromHull(hull)
                if (candidate != null && isValidDocumentQuad(candidate, sw.toFloat(), sh.toFloat())) {
                    bestQuad = candidate
                } else {
                    bestQuad = approximateQuadrilateral(hull, sw.toFloat(), sh.toFloat())
                }
            }
        }

        val q = bestQuad ?: return fallback

        return DocumentQuadrilateral(
            topLeft = PointF(q[0].x * scaleX, q[0].y * scaleY),
            topRight = PointF(q[1].x * scaleX, q[1].y * scaleY),
            bottomRight = PointF(q[2].x * scaleX, q[2].y * scaleY),
            bottomLeft = PointF(q[3].x * scaleX, q[3].y * scaleY)
        )
    }

    private fun dilate5x5(src: IntArray, w: Int, h: Int): IntArray {
        val tmp = IntArray(w * h)
        val dst = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var m = 0
                for (k in -2..2) {
                    val nx = (x + k).coerceIn(0, w - 1)
                    val v = src[row + nx]
                    if (v > m) m = v
                }
                tmp[row + x] = m
            }
        }
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var m = 0
                for (k in -2..2) {
                    val ny = (y + k).coerceIn(0, h - 1)
                    val v = tmp[ny * w + x]
                    if (v > m) m = v
                }
                dst[row + x] = m
            }
        }
        return dst
    }

    private fun erode5x5(src: IntArray, w: Int, h: Int): IntArray {
        val tmp = IntArray(w * h)
        val dst = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var m = 255
                for (k in -2..2) {
                    val nx = (x + k).coerceIn(0, w - 1)
                    val v = src[row + nx]
                    if (v < m) m = v
                }
                tmp[row + x] = m
            }
        }
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var m = 255
                for (k in -2..2) {
                    val ny = (y + k).coerceIn(0, h - 1)
                    val v = tmp[ny * w + x]
                    if (v < m) m = v
                }
                dst[row + x] = m
            }
        }
        return dst
    }

    private fun gaussianBlur3x3(src: IntArray, w: Int, h: Int): IntArray {
        val dst = src.clone()
        for (y in 1 until h - 1) {
            val prev = (y - 1) * w
            val curr = y * w
            val next = (y + 1) * w
            for (x in 1 until w - 1) {
                val sum = src[prev + x - 1] + 2 * src[prev + x] + src[prev + x + 1] +
                          2 * src[curr + x - 1] + 4 * src[curr + x] + 2 * src[curr + x + 1] +
                          src[next + x - 1] + 2 * src[next + x] + src[next + x + 1]
                dst[curr + x] = (sum + 8) shr 4
            }
        }
        return dst
    }

    private fun extractCentralBlobBoundary(
        img: IntArray,
        w: Int,
        h: Int,
        thresh: Int,
        pageIsBrighter: Boolean
    ): List<PointF> {
        val mask = BooleanArray(w * h)
        for (y in 2 until h - 2) {
            val row = y * w
            for (x in 2 until w - 2) {
                val fg = if (pageIsBrighter) img[row + x] >= thresh else img[row + x] <= thresh
                if (fg) mask[row + x] = true
            }
        }

        val visited = BooleanArray(w * h)
        var bestComponent = IntArray(0)
        val q = ArrayDeque<Int>()
        val currentComp = mutableListOf<Int>()

        val x0 = w * 2 / 10; val x1 = w * 8 / 10
        val y0 = h * 2 / 10; val y1 = h * 8 / 10

        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)

        for (sy in y0..y1 step 4) {
            for (sx in x0..x1 step 4) {
                val seedIdx = sy * w + sx
                if (!mask[seedIdx] || visited[seedIdx]) continue

                currentComp.clear()
                q.add(seedIdx)
                visited[seedIdx] = true

                while (!q.isEmpty()) {
                    val curr = q.removeFirst()
                    currentComp.add(curr)
                    val cx = curr % w
                    val cy = curr / w

                    for (d in 0 until 4) {
                        val nx = cx + dx[d]
                        val ny = cy + dy[d]
                        if (nx in 1 until w - 1 && ny in 1 until h - 1) {
                            val nIdx = ny * w + nx
                            if (mask[nIdx] && !visited[nIdx]) {
                                visited[nIdx] = true
                                q.add(nIdx)
                            }
                        }
                    }
                }

                if (currentComp.size > bestComponent.size) {
                    bestComponent = currentComp.toIntArray()
                }
            }
        }

        if (bestComponent.size < (w * h) / 10) return emptyList()

        val inBest = BooleanArray(w * h)
        for (idx in bestComponent) inBest[idx] = true

        val boundary = mutableListOf<PointF>()
        for (idx in bestComponent) {
            val x = idx % w
            val y = idx / w
            if (x <= 1 || x >= w - 2 || y <= 1 || y >= h - 2 ||
                !inBest[idx - 1] || !inBest[idx + 1] || !inBest[idx - w] || !inBest[idx + w]) {
                boundary.add(PointF(x.toFloat(), y.toFloat()))
            }
        }
        return boundary
    }

    private fun extractBestQuadFromHull(hull: List<PointF>): List<PointF>? {
        val n = hull.size
        if (n < 4) return null

        val candidates = if (n <= 28) hull else {
            (0 until 28).map { i -> hull[(i * n) / 28] }
        }
        val m = candidates.size
        var bestArea = -1.0f
        var bestIdx = intArrayOf(0, 1, 2, 3)

        for (i in 0 until m) {
            for (j in i + 1 until m) {
                for (k in j + 1 until m) {
                    for (l in k + 1 until m) {
                        val a = quadArea(candidates[i], candidates[j], candidates[k], candidates[l])
                        if (a > bestArea) {
                            bestArea = a
                            bestIdx = intArrayOf(i, j, k, l)
                        }
                    }
                }
            }
        }

        if (bestArea <= 0f) return null
        val corners = listOf(candidates[bestIdx[0]], candidates[bestIdx[1]], candidates[bestIdx[2]], candidates[bestIdx[3]])
        return orderCornersClockwise(corners)
    }

    private fun quadArea(p0: PointF, p1: PointF, p2: PointF, p3: PointF): Float {
        return 0.5f * abs(
            (p0.x * p1.y - p1.x * p0.y) +
            (p1.x * p2.y - p2.x * p1.y) +
            (p2.x * p3.y - p3.x * p2.y) +
            (p3.x * p0.y - p0.x * p3.y)
        )
    }

    private fun scoreQuadEdgeSupport(
        q: List<PointF>,
        gx: IntArray,
        gy: IntArray,
        w: Int,
        h: Int,
        noiseFloor: Int
    ): Float {
        if (!isValidDocumentQuad(q, w.toFloat(), h.toFloat())) return -1.0f

        var totalDirectionalGrad = 0f
        var validEdgeHits = 0
        var totalSamples = 0
        var borderPenaltyHits = 0
        val samplesPerSide = 32

        for (side in 0 until 4) {
            val a = q[side]
            val b = q[(side + 1) % 4]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = hypot(dx, dy)
            if (len < 5.0f) return -1.0f

            val nx = -dy / len
            val ny = dx / len

            for (s in 2 until samplesPerSide - 2) {
                val t = s.toFloat() / samplesPerSide
                val px = a.x + t * dx
                val py = a.y + t * dy

                if (px <= 2f || px >= w - 3f || py <= 2f || py >= h - 3f) {
                    borderPenaltyHits++
                }

                var bestLocalGrad = 0f
                for (r in -2..2) {
                    val sx = (px + r * nx).roundToInt()
                    val sy = (py + r * ny).roundToInt()
                    if (sx in 2 until w - 2 && sy in 2 until h - 2) {
                        val idx = sy * w + sx
                        val align = abs(gx[idx] * nx + gy[idx] * ny)
                        if (align > bestLocalGrad) {
                            bestLocalGrad = align
                        }
                    }
                }

                totalDirectionalGrad += bestLocalGrad
                if (bestLocalGrad > noiseFloor) {
                    validEdgeHits++
                }
                totalSamples++
            }
        }

        val coverage = validEdgeHits.toFloat() / max(1, totalSamples)
        if (coverage < 0.10f) return -1.0f

        val borderRatio = borderPenaltyHits.toFloat() / max(1, totalSamples)
        if (borderRatio > 0.50f) return -1.0f

        val avgGrad = totalDirectionalGrad / max(1, totalSamples)
        val areaRatio = quadArea(q[0], q[1], q[2], q[3]) / (w * h).toFloat()
        return (avgGrad * coverage * Math.pow(areaRatio.toDouble(), 0.25).toFloat() * (1.0f - 0.5f * borderRatio))
    }

    private fun extractRadialEdgePoints(
        gx: IntArray,
        gy: IntArray,
        mag: IntArray,
        w: Int,
        h: Int,
        minGrad: Int
    ): List<PointF> {
        val pts = mutableListOf<PointF>()
        val cx = w * 0.5f
        val cy = h * 0.5f
        val maxRadius = hypot(cx, cy)
        val numRays = 72

        for (i in 0 until numRays) {
            val angle = (i * 2.0 * Math.PI) / numRays
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()

            var bestPt: PointF? = null
            var bestScore = 0f

            var r = min(w, h) * 0.15f
            while (r < maxRadius) {
                val x = (cx + r * dx).roundToInt()
                val y = (cy + r * dy).roundToInt()
                if (x < 3 || x >= w - 3 || y < 3 || y >= h - 3) break

                val idx = y * w + x
                if (mag[idx] > minGrad) {
                    val align = abs(gx[idx] * dx + gy[idx] * dy)
                    if (align >= bestScore * 0.85f && align > minGrad) {
                        bestScore = align
                        bestPt = PointF(x.toFloat(), y.toFloat())
                    }
                }
                r += 1.0f
            }

            if (bestPt != null) {
                pts.add(bestPt)
            }
        }
        return pts
    }

    fun computeConvexHull(points: List<PointF>): List<PointF> {
        val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))
        if (sorted.size <= 3) return sorted

        fun cross(o: PointF, a: PointF, b: PointF): Float {
            return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        }

        val lower = mutableListOf<PointF>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }

        val upper = mutableListOf<PointF>()
        for (i in sorted.indices.reversed()) {
            val p = sorted[i]
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }

        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    private fun approximateQuadrilateral(hull: List<PointF>, width: Float, height: Float): List<PointF>? {
        if (hull.size < 4) return null
        val perimeter = computePerimeter(hull)
        if (perimeter <= 0f) return null

        // Sweep epsilon from fine to coarse to extract the best 4-corner polygon
        for (fraction in listOf(0.020f, 0.025f, 0.030f, 0.038f, 0.048f, 0.060f, 0.075f)) {
            val epsilon = perimeter * fraction
            val simplified = rdpSimplify(hull, epsilon)
            if (simplified.size == 4) {
                val ordered = orderCornersClockwise(simplified)
                if (isValidDocumentQuad(ordered, width, height)) {
                    return ordered
                }
            }
        }

        // Extremal fallback from outer convex hull envelope
        var minSum = Float.MAX_VALUE; var maxSum = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE; var maxDiff = -Float.MAX_VALUE
        var tl = hull[0]; var tr = hull[0]; var br = hull[0]; var bl = hull[0]
        for (p in hull) {
            val sum = p.x + p.y
            val diff = p.x - p.y
            if (sum < minSum) { minSum = sum; tl = p }
            if (sum > maxSum) { maxSum = sum; br = p }
            if (diff > maxDiff) { maxDiff = diff; tr = p }
            if (diff < minDiff) { minDiff = diff; bl = p }
        }
        val candidate = listOf(tl, tr, br, bl)
        if (isValidDocumentQuad(candidate, width, height)) {
            return candidate
        }

        return null
    }

    private fun rdpSimplify(points: List<PointF>, epsilon: Float): List<PointF> {
        if (points.size < 3) return points

        fun perpendicularDistance(pt: PointF, lineStart: PointF, lineEnd: PointF): Float {
            val dx = lineEnd.x - lineStart.x
            val dy = lineEnd.y - lineStart.y
            val mag = hypot(dx, dy)
            if (mag == 0f) return hypot(pt.x - lineStart.x, pt.y - lineStart.y)
            return abs(dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x) / mag
        }

        var dmax = 0f
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > dmax) {
                index = i
                dmax = d
            }
        }

        return if (dmax > epsilon) {
            val rec1 = rdpSimplify(points.subList(0, index + 1), epsilon)
            val rec2 = rdpSimplify(points.subList(index, points.size), epsilon)
            rec1.dropLast(1) + rec2
        } else {
            listOf(points[0], points[end])
        }
    }

    private fun computePerimeter(polygon: List<PointF>): Float {
        var p = 0f
        for (i in polygon.indices) {
            val next = (i + 1) % polygon.size
            p += hypot(polygon[next].x - polygon[i].x, polygon[next].y - polygon[i].y)
        }
        return p
    }

    private fun orderCornersClockwise(pts: List<PointF>): List<PointF> {
        val cx = pts.map { it.x }.average().toFloat()
        val cy = pts.map { it.y }.average().toFloat()

        val sorted = pts.sortedBy { atan2(it.y - cy, it.x - cx) }
        val tlIdx = sorted.indices.minByOrNull { sorted[it].x + sorted[it].y } ?: 0
        return List(4) { i -> sorted[(tlIdx + i) % 4] }
    }

    private fun isValidDocumentQuad(quad: List<PointF>, width: Float, height: Float): Boolean {
        if (quad.size != 4) return false
        val (p0, p1, p2, p3) = quad

        val area = quadArea(p0, p1, p2, p3)
        val frameArea = width * height
        if (area < frameArea * 0.10f || area > frameArea * 0.96f) return false

        fun cross(a: PointF, b: PointF, c: PointF): Float {
            return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        }

        val cp1 = cross(p0, p1, p2)
        val cp2 = cross(p1, p2, p3)
        val cp3 = cross(p2, p3, p0)
        val cp4 = cross(p3, p0, p1)

        val convex = (cp1 > 0 && cp2 > 0 && cp3 > 0 && cp4 > 0) || (cp1 < 0 && cp2 < 0 && cp3 < 0 && cp4 < 0)
        if (!convex) return false

        val pts = arrayOf(p0, p1, p2, p3)
        for (i in 0 until 4) {
            val prev = pts[(i + 3) % 4]
            val curr = pts[i]
            val next = pts[(i + 1) % 4]
            val v1x = prev.x - curr.x; val v1y = prev.y - curr.y
            val v2x = next.x - curr.x; val v2y = next.y - curr.y
            val len1 = hypot(v1x, v1y)
            val len2 = hypot(v2x, v2y)
            if (len1 < min(width, height) * 0.12f || len2 < min(width, height) * 0.12f) return false
            val cosTheta = (v1x * v2x + v1y * v2y) / (len1 * len2)
            if (cosTheta > 0.77f || cosTheta < -0.82f) return false
        }
        return true
    }
}
