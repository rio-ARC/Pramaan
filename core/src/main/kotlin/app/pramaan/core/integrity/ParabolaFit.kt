package app.pramaan.core.integrity

data class Parabola(
    val a: Double,
    val b: Double,
    val c: Double
) {
    /**
     * Estimated gravity g = -2a from y(t) = a*t^2 + b*t + c.
     */
    val gravity: Double get() = -2.0 * a
}

/**
 * Least-squares quadratic polynomial fitting for hip trajectory during flight.
 * Fits y(t) = a*t^2 + b*t + c to estimate gravity g_est = -2a.
 */
object ParabolaFit {
    /**
     * Fits a parabola to points (t[i], y[i]) using ordinary least squares.
     * @param t Time in seconds
     * @param y Height in metres
     * @return Parabola parameters (a, b, c) or null if underdetermined / collinear
     */
    fun fit(t: DoubleArray, y: DoubleArray): Parabola? {
        val n = t.size
        if (n < 3 || n != y.size) return null

        var sumT = 0.0
        var sumT2 = 0.0
        var sumT3 = 0.0
        var sumT4 = 0.0
        var sumY = 0.0
        var sumTY = 0.0
        var sumT2Y = 0.0

        for (i in 0 until n) {
            val ti = t[i]
            val yi = y[i]
            val ti2 = ti * ti
            sumT += ti
            sumT2 += ti2
            sumT3 += ti2 * ti
            sumT4 += ti2 * ti2
            sumY += yi
            sumTY += ti * yi
            sumT2Y += ti2 * yi
        }

        // 3x3 normal equations matrix:
        // [ sumT4  sumT3  sumT2 ] [ a ]   [ sumT2Y ]
        // [ sumT3  sumT2  sumT  ] [ b ] = [ sumTY  ]
        // [ sumT2  sumT   N     ] [ c ]   [ sumY   ]

        val m00 = sumT4; val m01 = sumT3; val m02 = sumT2
        val m10 = sumT3; val m11 = sumT2; val m12 = sumT
        val m20 = sumT2; val m21 = sumT;  val m22 = n.toDouble()

        val det = m00 * (m11 * m22 - m12 * m21) -
                  m01 * (m10 * m22 - m12 * m20) +
                  m02 * (m10 * m21 - m11 * m20)

        if (Math.abs(det) < 1e-12) return null

        val v0 = sumT2Y
        val v1 = sumTY
        val v2 = sumY

        val detA = v0 * (m11 * m22 - m12 * m21) -
                   m01 * (v1 * m22 - m12 * v2) +
                   m02 * (v1 * m21 - m11 * v2)

        val detB = m00 * (v1 * m22 - m12 * v2) -
                   v0 * (m10 * m22 - m12 * m20) +
                   m02 * (m10 * v2 - v1 * m20)

        val detC = m00 * (m11 * v2 - v1 * m21) -
                   m01 * (m10 * v2 - v1 * m20) +
                   v0 * (m10 * m21 - m11 * m20)

        val a = detA / det
        val b = detB / det
        val c = detC / det

        return Parabola(a, b, c)
    }
}
