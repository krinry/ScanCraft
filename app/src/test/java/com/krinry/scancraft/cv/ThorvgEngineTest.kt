package com.krinry.scancraft.cv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for ThorvgEngine ShapeType constants and bindings.
 */
class ThorvgEngineTest {

    @Test
    fun testThorvgShapeTypeEnumIntegrity() {
        assertEquals(0, ThorvgEngine.ShapeType.RECTANGLE.id)
        assertEquals(1, ThorvgEngine.ShapeType.FILLED_BOX.id)
        assertEquals(2, ThorvgEngine.ShapeType.CIRCLE.id)
        assertEquals(3, ThorvgEngine.ShapeType.LINE.id)
        assertEquals(4, ThorvgEngine.ShapeType.ARROW.id)
    }
}
