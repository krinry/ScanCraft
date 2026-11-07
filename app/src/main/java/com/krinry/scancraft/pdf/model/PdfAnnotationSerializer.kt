package com.krinry.scancraft.pdf.model

import org.json.JSONArray
import org.json.JSONObject

object PdfAnnotationSerializer {

    fun serialize(annotations: List<PdfAnnotation>): String {
        if (annotations.isEmpty()) return ""
        try {
            val root = JSONArray()
            for (item in annotations) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("zIndex", item.zIndex)

                when (item) {
                    is PdfAnnotation.InkStroke -> {
                        obj.put("type", "stroke")
                        val ptsArray = JSONArray()
                        for (pt in item.points) {
                            val ptObj = JSONObject()
                            ptObj.put("x", pt.x.toDouble())
                            ptObj.put("y", pt.y.toDouble())
                            ptsArray.put(ptObj)
                        }
                        obj.put("points", ptsArray)
                        obj.put("color", item.colorArgb)
                        obj.put("strokeWidth", item.strokeWidthNorm.toDouble())
                        obj.put("opacity", item.opacity.toDouble())
                        obj.put("isHighlighter", item.isHighlighter)
                        obj.put("isSmudge", item.isSmudge)
                    }

                    is PdfAnnotation.ShapeOverlay -> {
                        obj.put("type", "shape")
                        obj.put("shapeType", item.shapeType.name)
                        val bObj = JSONObject()
                        bObj.put("l", item.bounds.left.toDouble())
                        bObj.put("t", item.bounds.top.toDouble())
                        bObj.put("r", item.bounds.right.toDouble())
                        bObj.put("b", item.bounds.bottom.toDouble())
                        obj.put("bounds", bObj)
                        obj.put("borderColor", item.borderColorArgb)
                        obj.put("strokeWidth", item.strokeWidthNorm.toDouble())
                        obj.put("borderOpacity", item.borderOpacity.toDouble())
                        obj.put("fillType", item.fillType.name)
                        obj.put("fillColor1", item.fillColor1Argb)
                        obj.put("fillColor2", item.fillColor2Argb)
                        obj.put("fillOpacity", item.fillOpacity.toDouble())
                        obj.put("dashStyle", item.dashStyle.name)
                        obj.put("cornerRadius", item.cornerRadiusNorm.toDouble())
                        obj.put("rotation", item.rotationDegrees.toDouble())
                    }

                    is PdfAnnotation.TextNote -> {
                        obj.put("type", "text")
                        obj.put("text", item.text)
                        val posObj = JSONObject()
                        posObj.put("x", item.position.x.toDouble())
                        posObj.put("y", item.position.y.toDouble())
                        obj.put("pos", posObj)
                        obj.put("color", item.colorArgb)
                        obj.put("fontSize", item.fontSizeNorm.toDouble())
                        if (item.backgroundColorArgb != null) {
                            obj.put("bgColor", item.backgroundColorArgb)
                        }
                        obj.put("isBold", item.isBold)
                    }

                    is PdfAnnotation.SignatureStamp -> {
                        obj.put("type", "signature")
                        val bObj = JSONObject()
                        bObj.put("l", item.bounds.left.toDouble())
                        bObj.put("t", item.bounds.top.toDouble())
                        bObj.put("r", item.bounds.right.toDouble())
                        bObj.put("b", item.bounds.bottom.toDouble())
                        obj.put("bounds", bObj)
                        obj.put("inkColor", item.inkColorArgb)
                        obj.put("rotation", item.rotationDegrees.toDouble())

                        val strokesArr = JSONArray()
                        for (stroke in item.strokes) {
                            val singleArr = JSONArray()
                            for (pt in stroke) {
                                val ptObj = JSONObject()
                                ptObj.put("x", pt.x.toDouble())
                                ptObj.put("y", pt.y.toDouble())
                                singleArr.put(ptObj)
                            }
                            strokesArr.put(singleArr)
                        }
                        obj.put("strokes", strokesArr)
                    }
                }
                root.put(obj)
            }
            return root.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    fun deserialize(json: String): List<PdfAnnotation> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<PdfAnnotation>()
        try {
            val root = JSONArray(json)
            for (i in 0 until root.length()) {
                val obj = root.getJSONObject(i)
                val id = obj.optString("id", "")
                val zIndex = obj.optInt("zIndex", 0)
                val type = obj.optString("type", "")

                when (type) {
                    "stroke" -> {
                        val ptsArray = obj.optJSONArray("points") ?: JSONArray()
                        val points = mutableListOf<OffsetNormalized>()
                        for (j in 0 until ptsArray.length()) {
                            val ptObj = ptsArray.getJSONObject(j)
                            points.add(
                                OffsetNormalized(
                                    x = ptObj.optDouble("x", 0.0).toFloat(),
                                    y = ptObj.optDouble("y", 0.0).toFloat()
                                )
                            )
                        }
                        list.add(
                            PdfAnnotation.InkStroke(
                                id = if (id.isNotBlank()) id else java.util.UUID.randomUUID().toString(),
                                zIndex = zIndex,
                                points = points,
                                colorArgb = obj.optInt("color", -1),
                                strokeWidthNorm = obj.optDouble("strokeWidth", 0.005).toFloat(),
                                opacity = obj.optDouble("opacity", 1.0).toFloat(),
                                isHighlighter = obj.optBoolean("isHighlighter", false),
                                isSmudge = obj.optBoolean("isSmudge", false)
                            )
                        )
                    }

                    "shape" -> {
                        val shapeTypeName = obj.optString("shapeType", AnnotationShapeType.RECTANGLE.name)
                        val shapeType = try {
                            AnnotationShapeType.valueOf(shapeTypeName)
                        } catch (_: Exception) {
                            AnnotationShapeType.RECTANGLE
                        }

                        val bObj = obj.optJSONObject("bounds")
                        val bounds = if (bObj != null) {
                            RectNormalized(
                                left = bObj.optDouble("l", 0.0).toFloat(),
                                top = bObj.optDouble("t", 0.0).toFloat(),
                                right = bObj.optDouble("r", 0.1).toFloat(),
                                bottom = bObj.optDouble("b", 0.1).toFloat()
                            )
                        } else {
                            RectNormalized(0.1f, 0.1f, 0.3f, 0.3f)
                        }

                        val fillTypeName = obj.optString("fillType", ShapeFillType.NONE.name)
                        val fillType = try { ShapeFillType.valueOf(fillTypeName) } catch (_: Exception) { ShapeFillType.NONE }

                        val dashStyleName = obj.optString("dashStyle", ShapeDashStyle.SOLID.name)
                        val dashStyle = try { ShapeDashStyle.valueOf(dashStyleName) } catch (_: Exception) { ShapeDashStyle.SOLID }

                        val borderColor = obj.optInt("borderColor", -1)
                        list.add(
                            PdfAnnotation.ShapeOverlay(
                                id = if (id.isNotBlank()) id else java.util.UUID.randomUUID().toString(),
                                zIndex = zIndex,
                                shapeType = shapeType,
                                bounds = bounds,
                                borderColorArgb = borderColor,
                                strokeWidthNorm = obj.optDouble("strokeWidth", 0.004).toFloat(),
                                borderOpacity = obj.optDouble("borderOpacity", 1.0).toFloat(),
                                fillType = fillType,
                                fillColor1Argb = obj.optInt("fillColor1", borderColor),
                                fillColor2Argb = obj.optInt("fillColor2", borderColor),
                                fillOpacity = obj.optDouble("fillOpacity", 1.0).toFloat(),
                                dashStyle = dashStyle,
                                cornerRadiusNorm = obj.optDouble("cornerRadius", 0.012).toFloat(),
                                rotationDegrees = obj.optDouble("rotation", 0.0).toFloat()
                            )
                        )
                    }

                    "text" -> {
                        val textStr = obj.optString("text", "")
                        val posObj = obj.optJSONObject("pos")
                        val pos = if (posObj != null) {
                            OffsetNormalized(
                                x = posObj.optDouble("x", 0.1).toFloat(),
                                y = posObj.optDouble("y", 0.1).toFloat()
                            )
                        } else {
                            OffsetNormalized(0.1f, 0.1f)
                        }
                        val bgColor = if (obj.has("bgColor")) obj.getInt("bgColor") else null

                        list.add(
                            PdfAnnotation.TextNote(
                                id = if (id.isNotBlank()) id else java.util.UUID.randomUUID().toString(),
                                zIndex = zIndex,
                                text = textStr,
                                position = pos,
                                colorArgb = obj.optInt("color", -1),
                                fontSizeNorm = obj.optDouble("fontSize", 0.024).toFloat(),
                                backgroundColorArgb = bgColor,
                                isBold = obj.optBoolean("isBold", true)
                            )
                        )
                    }

                    "signature" -> {
                        val bObj = obj.optJSONObject("bounds")
                        val bounds = if (bObj != null) {
                            RectNormalized(
                                left = bObj.optDouble("l", 0.0).toFloat(),
                                top = bObj.optDouble("t", 0.0).toFloat(),
                                right = bObj.optDouble("r", 0.3).toFloat(),
                                bottom = bObj.optDouble("b", 0.2).toFloat()
                            )
                        } else {
                            RectNormalized(0.5f, 0.7f, 0.85f, 0.85f)
                        }

                        val strokesArr = obj.optJSONArray("strokes") ?: JSONArray()
                        val strokesList = mutableListOf<List<OffsetNormalized>>()
                        for (sIdx in 0 until strokesArr.length()) {
                            val singleArr = strokesArr.getJSONArray(sIdx)
                            val pts = mutableListOf<OffsetNormalized>()
                            for (pIdx in 0 until singleArr.length()) {
                                val pObj = singleArr.getJSONObject(pIdx)
                                pts.add(
                                    OffsetNormalized(
                                        x = pObj.optDouble("x", 0.0).toFloat(),
                                        y = pObj.optDouble("y", 0.0).toFloat()
                                    )
                                )
                            }
                            strokesList.add(pts)
                        }

                        list.add(
                            PdfAnnotation.SignatureStamp(
                                id = if (id.isNotBlank()) id else java.util.UUID.randomUUID().toString(),
                                zIndex = zIndex,
                                bounds = bounds,
                                inkColorArgb = obj.optInt("inkColor", -16777216),
                                strokes = strokesList,
                                rotationDegrees = obj.optDouble("rotation", 0.0).toFloat()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
