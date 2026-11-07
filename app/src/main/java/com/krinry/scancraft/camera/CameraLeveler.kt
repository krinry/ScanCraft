package com.krinry.scancraft.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Clean-room camera orientation and horizon leveler.
 * Uses hardware sensors to calculate device tilt (pitch and roll in degrees).
 * When held parallel to a desk/document within +/- 3 degrees, the indicator signals LEVEL.
 */
data class LevelerState(
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val isLevel: Boolean = false,
    val isVisible: Boolean = false
)

class CameraLeveler(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _levelerState = MutableStateFlow(LevelerState())
    val levelerState: StateFlow<LevelerState> = _levelerState.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun start() {
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        _levelerState.value = LevelerState()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            // Pitch: rotation around X axis (-pi to pi)
            val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            // Roll: rotation around Y axis (-pi/2 to pi/2)
            val roll = Math.toDegrees(-orientationAngles[2].toDouble()).toFloat()
            updateAngles(pitch, roll)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val g = event.values
            val pitch = Math.toDegrees(atan2(g[1].toDouble(), sqrt((g[0] * g[0] + g[2] * g[2]).toDouble()))).toFloat()
            val roll = Math.toDegrees(atan2(-g[0].toDouble(), g[2].toDouble())).toFloat()
            updateAngles(pitch, roll)
        }
    }

    private fun updateAngles(pitch: Float, roll: Float) {
        // Leveler is active when the user points camera roughly downward towards a desk/page (within +/- 30 degrees)
        val isLevel = abs(pitch) < 3.0f && abs(roll) < 3.0f
        val isVisible = abs(pitch) < 28.0f && abs(roll) < 28.0f
        _levelerState.value = LevelerState(
            pitch = pitch,
            roll = roll,
            isLevel = isLevel,
            isVisible = isVisible
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
