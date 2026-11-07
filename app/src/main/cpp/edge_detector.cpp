#include "native_scanner.h"
#include <vector>
#include <cmath>
#include <algorithm>
#include <cstdint>
#include <queue>

namespace {

static float cross2D(const Point2D& o, const Point2D& a, const Point2D& b) {
    return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
}

static float distSq(const Point2D& a, const Point2D& b) {
    float dx = a.x - b.x;
    float dy = a.y - b.y;
    return dx * dx + dy * dy;
}

static float distPointToSegment(const Point2D& p, const Point2D& a, const Point2D& b) {
    float vx = b.x - a.x;
    float vy = b.y - a.y;
    float lenSq = vx * vx + vy * vy;
    if (lenSq < 1e-6f) return std::sqrt(distSq(p, a));
    float t = ((p.x - a.x) * vx + (p.y - a.y) * vy) / lenSq;
    t = std::max(0.0f, std::min(1.0f, t));
    Point2D proj = {a.x + t * vx, a.y + t * vy};
    return std::sqrt(distSq(p, proj));
}

// Monotone Chain 2D Convex Hull
static std::vector<Point2D> computeConvexHull(std::vector<Point2D> pts) {
    if (pts.size() <= 3) return pts;
    std::sort(pts.begin(), pts.end(), [](const Point2D& a, const Point2D& b) {
        return a.x < b.x || (a.x == b.x && a.y < b.y);
    });

    std::vector<Point2D> h;
    h.reserve(pts.size() * 2);

    for (const auto& p : pts) {
        while (h.size() >= 2 && cross2D(h[h.size() - 2], h[h.size() - 1], p) <= 0) {
            h.pop_back();
        }
        h.push_back(p);
    }

    size_t lowerSize = h.size();
    for (int i = static_cast<int>(pts.size()) - 2; i >= 0; --i) {
        const auto& p = pts[i];
        while (h.size() > lowerSize && cross2D(h[h.size() - 2], h[h.size() - 1], p) <= 0) {
            h.pop_back();
        }
        h.push_back(p);
    }

    if (!h.empty()) h.pop_back();
    return h;
}

// Morphological Dilation (Max Filter) - Separable 5x5 for erasing dark text
static std::vector<uint8_t> dilate5x5(const std::vector<uint8_t>& src, int w, int h) {
    std::vector<uint8_t> tmp(w * h);
    std::vector<uint8_t> dst(w * h);

    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            uint8_t m = 0;
            for (int k = -2; k <= 2; ++k) {
                int nx = std::max(0, std::min(w - 1, x + k));
                m = std::max(m, src[y * w + nx]);
            }
            tmp[y * w + x] = m;
        }
    }
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            uint8_t m = 0;
            for (int k = -2; k <= 2; ++k) {
                int ny = std::max(0, std::min(h - 1, y + k));
                m = std::max(m, tmp[ny * w + x]);
            }
            dst[y * w + x] = m;
        }
    }
    return dst;
}

// Morphological Erosion (Min Filter) - Separable 5x5
static std::vector<uint8_t> erode5x5(const std::vector<uint8_t>& src, int w, int h) {
    std::vector<uint8_t> tmp(w * h);
    std::vector<uint8_t> dst(w * h);

    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            uint8_t m = 255;
            for (int k = -2; k <= 2; ++k) {
                int nx = std::max(0, std::min(w - 1, x + k));
                m = std::min(m, src[y * w + nx]);
            }
            tmp[y * w + x] = m;
        }
    }
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            uint8_t m = 255;
            for (int k = -2; k <= 2; ++k) {
                int ny = std::max(0, std::min(h - 1, y + k));
                m = std::min(m, tmp[ny * w + x]);
            }
            dst[y * w + x] = m;
        }
    }
    return dst;
}

// 3x3 Gaussian Blur to smooth sensor and compression noise
static std::vector<uint8_t> gaussianBlur3x3(const std::vector<uint8_t>& src, int w, int h) {
    std::vector<uint8_t> dst = src;
    for (int y = 1; y < h - 1; ++y) {
        for (int x = 1; x < w - 1; ++x) {
            int sum = src[(y - 1) * w + (x - 1)] + 2 * src[(y - 1) * w + x] + src[(y - 1) * w + (x + 1)]
                    + 2 * src[y * w + (x - 1)]   + 4 * src[y * w + x]       + 2 * src[y * w + (x + 1)]
                    + src[(y + 1) * w + (x - 1)] + 2 * src[(y + 1) * w + x] + src[(y + 1) * w + (x + 1)];
            dst[y * w + x] = static_cast<uint8_t>((sum + 8) >> 4);
        }
    }
    return dst;
}

// Compute area of a 4-point polygon
static float quadArea(const Point2D& p0, const Point2D& p1, const Point2D& p2, const Point2D& p3) {
    return 0.5f * std::abs(
        (p0.x * p1.y - p1.x * p0.y) +
        (p1.x * p2.y - p2.x * p1.y) +
        (p2.x * p3.y - p3.x * p2.y) +
        (p3.x * p0.y - p0.x * p3.y)
    );
}

// Order 4 points clockwise: Top-Left, Top-Right, Bottom-Right, Bottom-Left
static Quad orderQuadCorners(std::vector<Point2D> pts) {
    Point2D center = {0.0f, 0.0f};
    for (const auto& p : pts) {
        center.x += p.x * 0.25f;
        center.y += p.y * 0.25f;
    }

    // Sort clockwise around center
    std::sort(pts.begin(), pts.end(), [&center](const Point2D& a, const Point2D& b) {
        return std::atan2(a.y - center.y, a.x - center.x) <
               std::atan2(b.y - center.y, b.x - center.x);
    });

    // Rotate so Top-Left (minimum x + y) is first
    int tlIdx = 0;
    float minSum = 1e9f;
    for (int i = 0; i < 4; ++i) {
        float s = pts[i].x + pts[i].y;
        if (s < minSum) {
            minSum = s;
            tlIdx = i;
        }
    }

    return {
        pts[tlIdx],
        pts[(tlIdx + 1) % 4],
        pts[(tlIdx + 2) % 4],
        pts[(tlIdx + 3) % 4]
    };
}

// Check if Quad has valid geometry (convex, reasonable internal angles)
static bool isValidQuadGeometry(const Quad& q, int w, int h) {
    Point2D p[4] = {q.topLeft, q.topRight, q.bottomRight, q.bottomLeft};

    float area = quadArea(p[0], p[1], p[2], p[3]);
    float frameArea = static_cast<float>(w * h);
    if (area < frameArea * 0.10f || area > frameArea * 0.96f) {
        return false;
    }

    float cp1 = cross2D(p[0], p[1], p[2]);
    float cp2 = cross2D(p[1], p[2], p[3]);
    float cp3 = cross2D(p[2], p[3], p[0]);
    float cp4 = cross2D(p[3], p[0], p[1]);

    bool convex = (cp1 > 0 && cp2 > 0 && cp3 > 0 && cp4 > 0) ||
                  (cp1 < 0 && cp2 < 0 && cp3 < 0 && cp4 < 0);
    if (!convex) return false;

    // Ensure no interior angle is too sharp (< 40 deg) or too flat (> 145 deg)
    for (int i = 0; i < 4; ++i) {
        Point2D prev = p[(i + 3) % 4];
        Point2D curr = p[i];
        Point2D next = p[(i + 1) % 4];

        float v1x = prev.x - curr.x, v1y = prev.y - curr.y;
        float v2x = next.x - curr.x, v2y = next.y - curr.y;
        float len1 = std::sqrt(v1x * v1x + v1y * v1y);
        float len2 = std::sqrt(v2x * v2x + v2y * v2y);
        if (len1 < std::min(w, h) * 0.12f || len2 < std::min(w, h) * 0.12f) {
            return false;
        }
        float cosTheta = (v1x * v2x + v1y * v2y) / (len1 * len2);
        // cos(40 deg) ~ 0.766, cos(145 deg) ~ -0.819
        if (cosTheta > 0.77f || cosTheta < -0.82f) {
            return false;
        }
    }
    return true;
}

// Extract best 4 corners from a Convex Hull (handles rotation & perspective)
static bool extractBestQuadFromHull(const std::vector<Point2D>& hull, Quad& outQuad) {
    int n = static_cast<int>(hull.size());
    if (n < 4) return false;

    // Subsample hull if it has too many vertices so 4-corner search stays O(1)
    std::vector<Point2D> candidates;
    int maxCandidates = 28;
    if (n <= maxCandidates) {
        candidates = hull;
    } else {
        candidates.reserve(maxCandidates);
        for (int i = 0; i < maxCandidates; ++i) {
            candidates.push_back(hull[(i * n) / maxCandidates]);
        }
    }

    int m = static_cast<int>(candidates.size());
    float bestArea = -1.0f;
    int bestIdx[4] = {0, 1, 2, 3};

    // Find the inscribed quadrilateral on the hull that maximizes area
    for (int i = 0; i < m; ++i) {
        for (int j = i + 1; j < m; ++j) {
            for (int k = j + 1; k < m; ++k) {
                for (int l = k + 1; l < m; ++l) {
                    float a = quadArea(candidates[i], candidates[j], candidates[k], candidates[l]);
                    if (a > bestArea) {
                        bestArea = a;
                        bestIdx[0] = i;
                        bestIdx[1] = j;
                        bestIdx[2] = k;
                        bestIdx[3] = l;
                    }
                }
            }
        }
    }

    if (bestArea <= 0.0f) return false;

    std::vector<Point2D> corners = {
        candidates[bestIdx[0]],
        candidates[bestIdx[1]],
        candidates[bestIdx[2]],
        candidates[bestIdx[3]]
    };
    outQuad = orderQuadCorners(corners);
    return true;
}

// Score how strongly a candidate Quad aligns with actual Sobel edges
static float scoreQuadEdgeSupport(
    const Quad& q,
    const std::vector<int>& gx,
    const std::vector<int>& gy,
    const std::vector<int>& mag,
    int w, int h,
    int noiseFloor
) {
    if (!isValidQuadGeometry(q, w, h)) return -1.0f;

    Point2D pts[4] = {q.topLeft, q.topRight, q.bottomRight, q.bottomLeft};
    float totalDirectionalGrad = 0.0f;
    int validEdgeHits = 0;
    int totalSamples = 0;
    int borderPenaltyHits = 0;

    const int samplesPerSide = 32;

    for (int side = 0; side < 4; ++side) {
        Point2D a = pts[side];
        Point2D b = pts[(side + 1) % 4];
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float len = std::sqrt(dx * dx + dy * dy);
        if (len < 5.0f) return -1.0f;

        // Unit normal perpendicular to the edge
        float nx = -dy / len;
        float ny = dx / len;

        for (int s = 2; s < samplesPerSide - 2; ++s) {
            float t = static_cast<float>(s) / samplesPerSide;
            float px = a.x + t * dx;
            float py = a.y + t * dy;

            // Penalize quads hugging the extreme camera border
            if (px <= 2.0f || px >= w - 3.0f || py <= 2.0f || py >= h - 3.0f) {
                borderPenaltyHits++;
            }

            // Search +/- 2 pixels along the normal for peak edge response
            float bestLocalGrad = 0.0f;
            for (int r = -2; r <= 2; ++r) {
                int sx = static_cast<int>(std::round(px + r * nx));
                int sy = static_cast<int>(std::round(py + r * ny));
                if (sx >= 2 && sx < w - 2 && sy >= 2 && sy < h - 2) {
                    int idx = sy * w + sx;
                    // Gradient alignment with edge normal
                    float align = std::abs(gx[idx] * nx + gy[idx] * ny);
                    if (align > bestLocalGrad) {
                        bestLocalGrad = align;
                    }
                }
            }

            totalDirectionalGrad += bestLocalGrad;
            if (bestLocalGrad > noiseFloor) {
                validEdgeHits++;
            }
            totalSamples++;
        }
    }

    float coverage = static_cast<float>(validEdgeHits) / std::max(1, totalSamples);
    if (coverage < 0.10f) return -1.0f; // Accept candidates with at least 10% clear edge hits

    float borderRatio = static_cast<float>(borderPenaltyHits) / std::max(1, totalSamples);
    if (borderRatio > 0.50f) return -1.0f; // Reject full-frame border false positives

    float avgGrad = totalDirectionalGrad / std::max(1, totalSamples);
    float areaRatio = quadArea(pts[0], pts[1], pts[2], pts[3]) / static_cast<float>(w * h);

    // Combine edge coverage, edge contrast, and page area
    return avgGrad * coverage * std::pow(areaRatio, 0.25f) * (1.0f - 0.5f * borderRatio);
}

// Extract connected foreground blob near the center and return its boundary points
static std::vector<Point2D> extractCentralBlobBoundary(
    const std::vector<uint8_t>& img,
    int w, int h,
    uint8_t thresh,
    bool pageIsBrighter
) {
    std::vector<uint8_t> mask(w * h, 0);
    for (int y = 2; y < h - 2; ++y) {
        for (int x = 2; x < w - 2; ++x) {
            bool fg = pageIsBrighter ? (img[y * w + x] >= thresh) : (img[y * w + x] <= thresh);
            if (fg) mask[y * w + x] = 1;
        }
    }

    std::vector<uint8_t> visited(w * h, 0);
    std::vector<int> bestComponent;
    bestComponent.reserve(w * h / 2);

    // Search seeds in the central 60% of the frame to capture off-center pages
    int x0 = w * 2 / 10, x1 = w * 8 / 10;
    int y0 = h * 2 / 10, y1 = h * 8 / 10;

    std::queue<int> q;
    std::vector<int> currentComp;

    for (int sy = y0; sy <= y1; sy += 4) {
        for (int sx = x0; sx <= x1; sx += 4) {
            int seedIdx = sy * w + sx;
            if (!mask[seedIdx] || visited[seedIdx]) continue;

            currentComp.clear();
            q.push(seedIdx);
            visited[seedIdx] = 1;

            while (!q.empty()) {
                int curr = q.front();
                q.pop();
                currentComp.push_back(curr);

                int cx = curr % w;
                int cy = curr / w;

                const int dx[4] = {1, -1, 0, 0};
                const int dy[4] = {0, 0, 1, -1};
                for (int d = 0; d < 4; ++d) {
                    int nx = cx + dx[d];
                    int ny = cy + dy[d];
                    if (nx >= 1 && nx < w - 1 && ny >= 1 && ny < h - 1) {
                        int nIdx = ny * w + nx;
                        if (mask[nIdx] && !visited[nIdx]) {
                            visited[nIdx] = 1;
                            q.push(nIdx);
                        }
                    }
                }
            }

            if (currentComp.size() > bestComponent.size()) {
                bestComponent.swap(currentComp);
            }
        }
    }

    std::vector<Point2D> boundary;
    if (bestComponent.size() < static_cast<size_t>(w * h / 10)) {
        return boundary;
    }

    std::vector<uint8_t> inBest(w * h, 0);
    for (int idx : bestComponent) inBest[idx] = 1;

    // Collect only boundary pixels of the isolated blob
    for (int idx : bestComponent) {
        int x = idx % w;
        int y = idx / w;
        if (x <= 1 || x >= w - 2 || y <= 1 || y >= h - 2 ||
            !inBest[idx - 1] || !inBest[idx + 1] || !inBest[idx - w] || !inBest[idx + w]) {
            boundary.push_back({static_cast<float>(x), static_cast<float>(y)});
        }
    }

    return boundary;
}

// Raycast outward from image center to find strong gradient transitions (fallback for uneven light)
static std::vector<Point2D> extractRadialEdgePoints(
    const std::vector<int>& gx,
    const std::vector<int>& gy,
    const std::vector<int>& mag,
    int w, int h,
    int minGrad
) {
    std::vector<Point2D> pts;
    float cx = w * 0.5f;
    float cy = h * 0.5f;
    float maxRadius = std::sqrt(cx * cx + cy * cy);
    const int numRays = 72; // Every 5 degrees

    for (int i = 0; i < numRays; ++i) {
        float angle = (i * 2.0f * 3.14159265f) / numRays;
        float dx = std::cos(angle);
        float dy = std::sin(angle);

        Point2D bestPt = {-1.0f, -1.0f};
        float bestScore = 0.0f;

        // Walk from 18% radius outward up to frame border
        for (float r = std::min(w, h) * 0.15f; r < maxRadius; r += 1.0f) {
            int x = static_cast<int>(std::round(cx + r * dx));
            int y = static_cast<int>(std::round(cy + r * dy));
            if (x < 3 || x >= w - 3 || y < 3 || y >= h - 3) break;

            int idx = y * w + x;
            if (mag[idx] > minGrad) {
                // Edge gradient should align with ray direction
                float align = std::abs(gx[idx] * dx + gy[idx] * dy);
                if (align >= bestScore * 0.85f && align > minGrad) {
                    bestScore = align;
                    bestPt = {static_cast<float>(x), static_cast<float>(y)};
                }
            }
        }

        if (bestPt.x >= 0.0f) {
            pts.push_back(bestPt);
        }
    }
    return pts;
}

// Refine corner junction using local structure tensor
static Point2D refineCornerJunction(
    const Point2D& pt,
    const std::vector<int>& gx,
    const std::vector<int>& gy,
    const std::vector<int>& mag,
    int w, int h,
    int radius = 8
) {
    int cx = static_cast<int>(std::round(pt.x));
    int cy = static_cast<int>(std::round(pt.y));

    float bestResponse = -1.0f;
    Point2D bestPt = pt;

    for (int dy = -radius; dy <= radius; ++dy) {
        int y = cy + dy;
        if (y < 3 || y >= h - 3) continue;
        for (int dx = -radius; dx <= radius; ++dx) {
            int x = cx + dx;
            if (x < 3 || x >= w - 3) continue;

            float sxx = 0, syy = 0, sxy = 0;
            for (int py = -1; py <= 1; ++py) {
                for (int px = -1; px <= 1; ++px) {
                    int idx = (y + py) * w + (x + px);
                    float lx = static_cast<float>(gx[idx]);
                    float ly = static_cast<float>(gy[idx]);
                    sxx += lx * lx;
                    syy += ly * ly;
                    sxy += lx * ly;
                }
            }
            float det = sxx * syy - sxy * sxy;
            float trace = sxx + syy;
            float harris = det - 0.04f * trace * trace;

            float dist = std::sqrt(static_cast<float>(dx * dx + dy * dy));
            float weight = 1.0f / (1.0f + 0.15f * dist);
            float score = harris * weight;

            if (score > bestResponse) {
                bestResponse = score;
                bestPt = {static_cast<float>(x), static_cast<float>(y)};
            }
        }
    }
    return bestPt;
}

} // namespace

Quad detectDocumentBounds(const uint8_t* grayBuffer, int width, int height) {
    float marginX = width * 0.08f;
    float marginY = height * 0.08f;
    Quad fallback = {
        {marginX, marginY},
        {width - marginX, marginY},
        {width - marginX, height - marginY},
        {marginX, height - marginY}
    };

    if (!grayBuffer || width < 64 || height < 64) {
        return fallback;
    }

    // 1. Dynamic area-averaging downsample to ~180px on longest side
    int maxDim = std::max(width, height);
    int downsample = std::max(1, maxDim / 180);
    int sw = width / downsample;
    int sh = height / downsample;

    if (sw < 32 || sh < 32) return fallback;

    std::vector<uint8_t> small(sw * sh);
    for (int y = 0; y < sh; ++y) {
        int srcY = y * downsample;
        for (int x = 0; x < sw; ++x) {
            int srcX = x * downsample;
            int sum = 0;
            int count = 0;
            for (int dy = 0; dy < downsample; dy += 2) {
                for (int dx = 0; dx < downsample; dx += 2) {
                    sum += grayBuffer[(srcY + dy) * width + (srcX + dx)];
                    count++;
                }
            }
            small[y * sw + x] = static_cast<uint8_t>(sum / std::max(1, count));
        }
    }

    // 2. Morphological Closing (Dilation -> Erosion) to erase dark text inside the document
    std::vector<uint8_t> closed = erode5x5(dilate5x5(small, sw, sh), sw, sh);
    std::vector<uint8_t> smooth = gaussianBlur3x3(closed, sw, sh);

    // 3. Directional Sobel Gradients on the text-erased image
    std::vector<int> gx(sw * sh, 0);
    std::vector<int> gy(sw * sh, 0);
    std::vector<int> mag(sw * sh, 0);
    long long gradSum = 0;
    int gradCount = 0;

    for (int y = 2; y < sh - 2; ++y) {
        for (int x = 2; x < sw - 2; ++x) {
            int vx = -smooth[(y - 1) * sw + (x - 1)] + smooth[(y - 1) * sw + (x + 1)]
                     - 2 * smooth[y * sw + (x - 1)]   + 2 * smooth[y * sw + (x + 1)]
                     - smooth[(y + 1) * sw + (x - 1)] + smooth[(y + 1) * sw + (x + 1)];

            int vy = -smooth[(y - 1) * sw + (x - 1)] - 2 * smooth[(y - 1) * sw + x] - smooth[(y - 1) * sw + (x + 1)]
                     + smooth[(y + 1) * sw + (x - 1)] + 2 * smooth[(y + 1) * sw + x] + smooth[(y + 1) * sw + (x + 1)];

            int m = static_cast<int>(std::sqrt(static_cast<float>(vx * vx + vy * vy)));
            int idx = y * sw + x;
            gx[idx] = vx;
            gy[idx] = vy;
            mag[idx] = m;
            gradSum += m;
            gradCount++;
        }
    }

    int meanGrad = static_cast<int>(gradSum / std::max(1, gradCount));
    int noiseFloor = std::max(12, meanGrad);

    // 4. Compute Center vs Border Intensity to generate Multi-Threshold Blob Candidates
    long long centerSum = 0, centerCnt = 0;
    long long borderSum = 0, borderCnt = 0;
    for (int y = 2; y < sh - 2; ++y) {
        for (int x = 2; x < sw - 2; ++x) {
            uint8_t v = smooth[y * sw + x];
            if (x > sw * 3 / 10 && x < sw * 7 / 10 && y > sh * 3 / 10 && y < sh * 7 / 10) {
                centerSum += v;
                centerCnt++;
            } else if (x < sw / 8 || x > sw * 7 / 8 || y < sh / 8 || y > sh * 7 / 8) {
                borderSum += v;
                borderCnt++;
            }
        }
    }

    int centerMean = static_cast<int>(centerSum / std::max(1LL, centerCnt));
    int borderMean = static_cast<int>(borderSum / std::max(1LL, borderCnt));

    Quad bestQuad = fallback;
    float bestScore = -1.0f;

    // Test multiple segmentation thresholds between center and border intensity
    const float alphas[6] = {0.20f, 0.32f, 0.45f, 0.58f, 0.70f, 0.82f};
    bool pageBrighterOptions[2] = {centerMean >= borderMean, centerMean < borderMean};

    for (int mode = 0; mode < 2; ++mode) {
        bool pageIsBrighter = pageBrighterOptions[mode];
        if (mode == 1 && std::abs(centerMean - borderMean) > 25) continue;

        for (float alpha : alphas) {
            int t = static_cast<int>(borderMean + alpha * (centerMean - borderMean));
            t = std::max(15, std::min(240, t));

            std::vector<Point2D> boundary = extractCentralBlobBoundary(
                smooth, sw, sh, static_cast<uint8_t>(t), pageIsBrighter
            );
            if (boundary.size() < 16) continue;

            std::vector<Point2D> hull = computeConvexHull(boundary);
            Quad candidate;
            if (extractBestQuadFromHull(hull, candidate)) {
                float score = scoreQuadEdgeSupport(candidate, gx, gy, mag, sw, sh, noiseFloor);
                if (score > bestScore) {
                    bestScore = score;
                    bestQuad = candidate;
                }
            }
        }
    }

    // 5. Fallback Candidate: Radial Edge Raycasting (handles heavy shadows or gradients)
    for (int mult : {12, 16, 22}) {
        int radThresh = std::max(20, (meanGrad * mult) / 10);
        std::vector<Point2D> radialPts = extractRadialEdgePoints(gx, gy, mag, sw, sh, radThresh);
        if (radialPts.size() >= 12) {
            // Filter outliers via hull + quad extraction
            std::vector<Point2D> hull = computeConvexHull(radialPts);
            Quad candidate;
            if (extractBestQuadFromHull(hull, candidate)) {
                float score = scoreQuadEdgeSupport(candidate, gx, gy, mag, sw, sh, noiseFloor);
                if (score > bestScore) {
                    bestScore = score;
                    bestQuad = candidate;
                }
            }
        }
    }

    if (bestScore < 0.0f) {
        int edgeThresh = std::max(20, meanGrad * 12 / 10);
        std::vector<Point2D> directPts;
        for (int y = 5; y < sh - 5; y += 2) {
            for (int x = 5; x < sw - 5; x += 2) {
                if (mag[y * sw + x] > edgeThresh) {
                    directPts.push_back({static_cast<float>(x), static_cast<float>(y)});
                }
            }
        }
        if (directPts.size() >= 12) {
            std::vector<Point2D> hull = computeConvexHull(directPts);
            Quad candidate;
            if (extractBestQuadFromHull(hull, candidate) && isValidQuadGeometry(candidate, sw, sh)) {
                bestQuad = candidate;
                bestScore = 1.0f;
            }
        }
    }

    if (bestScore < 0.0f) {
        return fallback;
    }

    // Refine corner junctions with local structure tensor
    bestQuad.topLeft = refineCornerJunction(bestQuad.topLeft, gx, gy, mag, sw, sh, 8);
    bestQuad.topRight = refineCornerJunction(bestQuad.topRight, gx, gy, mag, sw, sh, 8);
    bestQuad.bottomRight = refineCornerJunction(bestQuad.bottomRight, gx, gy, mag, sw, sh, 8);
    bestQuad.bottomLeft = refineCornerJunction(bestQuad.bottomLeft, gx, gy, mag, sw, sh, 8);

    // 6. Scale coordinates back to full resolution
    float scaleX = static_cast<float>( downsample );
    float scaleY = static_cast<float>( downsample );

    Quad finalQuad = {
        {bestQuad.topLeft.x * scaleX,     bestQuad.topLeft.y * scaleY},
        {bestQuad.topRight.x * scaleX,    bestQuad.topRight.y * scaleY},
        {bestQuad.bottomRight.x * scaleX, bestQuad.bottomRight.y * scaleY},
        {bestQuad.bottomLeft.x * scaleX,  bestQuad.bottomLeft.y * scaleY}
    };

    return finalQuad;
}

Point2D snapPointToNearestEdge(
    const uint32_t* argbPixels,
    int width,
    int height,
    float ptX,
    float ptY,
    float searchRadius,
    bool& outSnapped
) {
    outSnapped = false;
    if (!argbPixels || width < 16 || height < 16) {
        return {ptX, ptY};
    }

    int cx = static_cast<int>(std::round(ptX));
    int cy = static_cast<int>(std::round(ptY));
    int r = static_cast<int>(std::ceil(searchRadius));
    r = std::max(4, std::min(r, 48));

    int minX = std::max(2, cx - r);
    int maxX = std::min(width - 3, cx + r);
    int minY = std::max(2, cy - r);
    int maxY = std::min(height - 3, cy + r);

    if (minX >= maxX || minY >= maxY) {
        return {ptX, ptY};
    }

    auto getLum = [&](int x, int y) -> int {
        uint32_t c = argbPixels[y * width + x];
        int red = (c >> 16) & 0xFF;
        int green = (c >> 8) & 0xFF;
        int blue = c & 0xFF;
        return (red * 77 + green * 150 + blue * 29) >> 8;
    };

    float bestScore = -1.0f;
    Point2D bestPt = {ptX, ptY};
    const float rSq = searchRadius * searchRadius;

    for (int y = minY; y <= maxY; ++y) {
        float dy = y - ptY;
        for (int x = minX; x <= maxX; ++x) {
            float dx = x - ptX;
            float dSq = dx * dx + dy * dy;
            if (dSq > rSq) continue;

            int gx = -getLum(x - 1, y - 1) + getLum(x + 1, y - 1)
                     - 2 * getLum(x - 1, y)   + 2 * getLum(x + 1, y)
                     - getLum(x - 1, y + 1) + getLum(x + 1, y + 1);

            int gy = -getLum(x - 1, y - 1) - 2 * getLum(x, y - 1) - getLum(x + 1, y - 1)
                     + getLum(x - 1, y + 1) + 2 * getLum(x, y + 1) + getLum(x + 1, y + 1);

            float mag = std::sqrt(static_cast<float>(gx * gx + gy * gy));
            if (mag < 32.0f) continue; // Noise floor

            float dist = std::sqrt(dSq);
            float score = mag / (1.0f + 0.12f * dist);

            if (score > bestScore) {
                bestScore = score;
                bestPt = {static_cast<float>(x), static_cast<float>(y)};
                outSnapped = true;
            }
        }
    }

    return bestPt;
}

