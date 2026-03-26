package com.example.modernweather.nowcast.analysis

class KalmanPressureFilter(
    private val processNoise: Float = 0.01f,
    private val measurementNoise: Float = 0.12f
) {
    private var estimate: Float? = null
    private var covariance: Float = 1f

    fun reset() {
        estimate = null
        covariance = 1f
    }

    fun update(measurement: Float): Float {
        val currentEstimate = estimate
        if (currentEstimate == null) {
            estimate = measurement
            covariance = 1f
            return measurement
        }

        val predictedEstimate = currentEstimate
        val predictedCovariance = covariance + processNoise

        val kalmanGain = predictedCovariance / (predictedCovariance + measurementNoise)
        val updatedEstimate = predictedEstimate + kalmanGain * (measurement - predictedEstimate)
        covariance = (1f - kalmanGain) * predictedCovariance
        estimate = updatedEstimate

        return updatedEstimate
    }
}
