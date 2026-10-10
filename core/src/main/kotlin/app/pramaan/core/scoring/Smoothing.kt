package app.pramaan.core.scoring

/**
 * 5-frame smoothing utility (PRD 10.2 step 2).
 * Provides both 5-frame moving average and Savitzky-Golay 5-point quadratic filter.
 */
object Smoothing {
    /**
     * Savitzky-Golay 5-point quadratic filter.
     * Weights: [-3, 12, 17, 12, -3] / 35.
     * Preserves quadratic trajectories and sharp transitions with zero phase distortion.
     */
    fun savitzkyGolay5(values: FloatArray): FloatArray {
        if (values.size < 5) return values.copyOf()
        val result = FloatArray(values.size)
        result[0] = values[0]
        result[1] = (values[0] + values[1] + values[2]) / 3.0f
        result[values.size - 2] = (values[values.size - 3] + values[values.size - 2] + values[values.size - 1]) / 3.0f
        result[values.size - 1] = values[values.size - 1]

        for (i in 2 until values.size - 2) {
            val v = (-3f * values[i - 2] + 12f * values[i - 1] + 17f * values[i] + 12f * values[i + 1] - 3f * values[i + 2]) / 35.0f
            result[i] = v
        }
        return result
    }

    /**
     * 5-frame moving average.
     */
    fun movingAverage5(values: FloatArray): FloatArray {
        if (values.isEmpty()) return FloatArray(0)
        val result = FloatArray(values.size)
        for (i in values.indices) {
            val start = maxOf(0, i - 2)
            val end = minOf(values.size - 1, i + 2)
            var sum = 0.0
            for (j in start..end) sum += values[j]
            result[i] = (sum / (end - start + 1)).toFloat()
        }
        return result
    }

    /**
     * Stateful running 5-frame moving average for causal streaming.
     */
    class RunningAverage(private val windowSize: Int = 5) {
        private val buffer = FloatArray(windowSize)
        private var count = 0
        private var head = 0
        private var sum = 0.0

        fun next(value: Float): Float {
            if (count < windowSize) {
                buffer[count] = value
                sum += value
                count++
                return (sum / count).toFloat()
            } else {
                sum -= buffer[head]
                buffer[head] = value
                sum += value
                head = (head + 1) % windowSize
                return (sum / windowSize).toFloat()
            }
        }

        fun reset() {
            count = 0
            head = 0
            sum = 0.0
        }
    }
}
