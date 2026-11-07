package com.krinry.scancraft.pdf

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import com.krinry.scancraft.pdf.model.*

class PdfStudioSerializationTest {

    @Test
    fun testOffsetNormalizedConversion() {
        val screenOffset = Offset(300f, 600f)
        val pageWidth = 1000f
        val pageHeight = 2000f

        val norm = OffsetNormalized.fromScreenOffset(screenOffset, pageWidth, pageHeight)
        assertEquals(0.3f, norm.x, 0.0001f)
        assertEquals(0.3f, norm.y, 0.0001f)

        val projected = norm.toScreenOffset(pageWidth, pageHeight)
        assertEquals(300f, projected.x, 0.001f)
        assertEquals(600f, projected.y, 0.001f)
    }

    @Test
    fun testRectNormalizedBounds() {
        val p1 = Offset(100f, 200f)
        val p2 = Offset(500f, 800f)
        val pageWidth = 1000f
        val pageHeight = 1000f

        val rectNorm = RectNormalized.fromScreenPoints(p1, p2, pageWidth, pageHeight)
        assertEquals(0.1f, rectNorm.left, 0.0001f)
        assertEquals(0.2f, rectNorm.top, 0.0001f)
        assertEquals(0.5f, rectNorm.right, 0.0001f)
        assertEquals(0.8f, rectNorm.bottom, 0.0001f)

        assertTrue(rectNorm.contains(OffsetNormalized(0.3f, 0.5f)))
        assertFalse(rectNorm.contains(OffsetNormalized(0.05f, 0.5f)))
        assertFalse(rectNorm.contains(OffsetNormalized(0.3f, 0.9f)))
    }

    @Test
    fun testInkStrokeSerializationCycle() {
        val original = listOf(
            PdfAnnotation.InkStroke(
                id = "stroke-1",
                points = listOf(OffsetNormalized(0.1f, 0.2f), OffsetNormalized(0.3f, 0.4f)),
                colorArgb = -65536,
                strokeWidthNorm = 0.01f,
                opacity = 0.85f,
                isHighlighter = true,
                isSmudge = false
            )
        )

        val json = PdfAnnotationSerializer.serialize(original)
        assertTrue(json.isNotBlank())

        val deserialized = PdfAnnotationSerializer.deserialize(json)
        assertEquals(1, deserialized.size)
        val item = deserialized[0] as PdfAnnotation.InkStroke
        assertEquals("stroke-1", item.id)
        assertEquals(2, item.points.size)
        assertEquals(0.1f, item.points[0].x, 0.001f)
        assertEquals(0.2f, item.points[0].y, 0.001f)
        assertEquals(-65536, item.colorArgb)
        assertEquals(0.01f, item.strokeWidthNorm, 0.001f)
        assertEquals(0.85f, item.opacity, 0.001f)
        assertTrue(item.isHighlighter)
        assertFalse(item.isSmudge)
    }

    @Test
    fun testShapeOverlaySerializationCycle() {
        val original = listOf(
            PdfAnnotation.ShapeOverlay(
                id = "shape-1",
                shapeType = AnnotationShapeType.ARROW,
                bounds = RectNormalized(0.15f, 0.25f, 0.75f, 0.85f),
                borderColorArgb = -16777216,
                strokeWidthNorm = 0.008f,
                borderOpacity = 1.0f,
                fillType = ShapeFillType.LINEAR_GRADIENT,
                fillColor1Argb = -16711936,
                fillColor2Argb = -256,
                fillOpacity = 0.65f,
                dashStyle = ShapeDashStyle.DASHED
            )
        )

        val json = PdfAnnotationSerializer.serialize(original)
        val deserialized = PdfAnnotationSerializer.deserialize(json)

        assertEquals(1, deserialized.size)
        val item = deserialized[0] as PdfAnnotation.ShapeOverlay
        assertEquals("shape-1", item.id)
        assertEquals(AnnotationShapeType.ARROW, item.shapeType)
        assertEquals(0.15f, item.bounds.left, 0.001f)
        assertEquals(0.25f, item.bounds.top, 0.001f)
        assertEquals(0.75f, item.bounds.right, 0.001f)
        assertEquals(0.85f, item.bounds.bottom, 0.001f)
        assertEquals(ShapeFillType.LINEAR_GRADIENT, item.fillType)
        assertEquals(ShapeDashStyle.DASHED, item.dashStyle)
        assertEquals(0.65f, item.fillOpacity, 0.001f)
    }

    @Test
    fun testTextNoteSerializationCycle() {
        val original = listOf(
            PdfAnnotation.TextNote(
                id = "text-1",
                text = "APPROVED BY MANAGER",
                position = OffsetNormalized(0.4f, 0.6f),
                colorArgb = -16776961,
                fontSizeNorm = 0.035f,
                backgroundColorArgb = -1,
                isBold = true
            )
        )

        val json = PdfAnnotationSerializer.serialize(original)
        val deserialized = PdfAnnotationSerializer.deserialize(json)

        assertEquals(1, deserialized.size)
        val item = deserialized[0] as PdfAnnotation.TextNote
        assertEquals("APPROVED BY MANAGER", item.text)
        assertEquals(0.4f, item.position.x, 0.001f)
        assertEquals(0.6f, item.position.y, 0.001f)
        assertEquals(-16776961, item.colorArgb)
        assertEquals(0.035f, item.fontSizeNorm, 0.001f)
        assertEquals(-1, item.backgroundColorArgb)
        assertTrue(item.isBold)
    }

    @Test
    fun testSignatureStampSerializationCycle() {
        val original = listOf(
            PdfAnnotation.SignatureStamp(
                id = "sig-1",
                bounds = RectNormalized(0.5f, 0.7f, 0.85f, 0.85f),
                inkColorArgb = -16777216,
                strokes = listOf(
                    listOf(OffsetNormalized(0.51f, 0.71f), OffsetNormalized(0.6f, 0.75f)),
                    listOf(OffsetNormalized(0.61f, 0.72f), OffsetNormalized(0.8f, 0.82f))
                )
            )
        )

        val json = PdfAnnotationSerializer.serialize(original)
        val deserialized = PdfAnnotationSerializer.deserialize(json)

        assertEquals(1, deserialized.size)
        val item = deserialized[0] as PdfAnnotation.SignatureStamp
        assertEquals("sig-1", item.id)
        assertEquals(2, item.strokes.size)
        assertEquals(2, item.strokes[0].size)
        assertEquals(0.51f, item.strokes[0][0].x, 0.001f)
    }

    @Test
    fun testCorruptedJsonHandledGracefully() {
        val emptyList = PdfAnnotationSerializer.deserialize("")
        assertTrue(emptyList.isEmpty())

        val corruptedList = PdfAnnotationSerializer.deserialize("{invalid_json: true}")
        assertTrue(corruptedList.isEmpty())
    }
}
