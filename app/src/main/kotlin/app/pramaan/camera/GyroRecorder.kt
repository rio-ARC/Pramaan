package app.pramaan.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.pramaan.core.integrity.Checks
import app.pramaan.core.manifest.GyroJsonl
import app.pramaan.core.model.GyroSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class GyroRecorder(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val samples = mutableListOf<GyroSample>()
    private val rollingWindow = ArrayDeque<Double>()

    private val _currentRms = MutableStateFlow(0.0)
    val currentRms: StateFlow<Double> = _currentRms.asStateFlow()

    private var isRecording = false

    fun startPreflightMonitoring() {
        samples.clear()
        rollingWindow.clear()
        gyroSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun startRecording() {
        samples.clear()
        rollingWindow.clear()
        isRecording = true
        gyroSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopRecording(targetFile: File): List<GyroSample> {
        isRecording = false
        sensorManager?.unregisterListener(this)
        val recordedSamples = samples.toList()
        if (targetFile.parentFile?.exists() == false) {
            targetFile.parentFile?.mkdirs()
        }
        targetFile.writer().use { writer ->
            GyroJsonl.writeAll(writer, recordedSamples)
        }
        return recordedSamples
    }

    fun stopPreflightMonitoring() {
        if (!isRecording) {
            sensorManager?.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val magSq = x * x + y * y + z * z

        rollingWindow.addLast(magSq)
        if (rollingWindow.size > 20) rollingWindow.removeFirst()
        val currentRmsValue = Math.sqrt(rollingWindow.average())
        _currentRms.value = currentRmsValue

        if (isRecording) {
            samples.add(GyroSample(t = event.timestamp, x = x, y = y, z = z))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
