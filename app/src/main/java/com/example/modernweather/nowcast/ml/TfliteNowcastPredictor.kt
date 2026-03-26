package com.example.modernweather.nowcast.ml

import android.content.Context
import com.example.modernweather.nowcast.model.RawPressureSample
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel.MapMode

class TfliteNowcastPredictor private constructor(
    private val interpreter: Interpreter
) : Closeable {

    fun predictStormProbability(samples: List<RawPressureSample>): Float {
        if (samples.size < INPUT_SEQUENCE_LENGTH) return 0f

        val input = Array(1) { Array(INPUT_SEQUENCE_LENGTH) { FloatArray(FEATURES_PER_STEP) } }
        val recent = samples.takeLast(INPUT_SEQUENCE_LENGTH)

        val pressures = recent.map { it.pressureHpa }
        val mean = pressures.average().toFloat()
        val std = kotlin.math.sqrt(
            pressures.map { (it - mean) * (it - mean) }.average().toFloat()
        ).coerceAtLeast(0.05f)

        recent.forEachIndexed { index, sample ->
            input[0][index][0] = (sample.pressureHpa - mean) / std
        }

        val output = Array(1) { FloatArray(1) }
        interpreter.run(input, output)
        return output[0][0].coerceIn(0f, 1f)
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        const val MODEL_ASSET_PATH = "models/pressure_lstm_nowcast.tflite"
        private const val INPUT_SEQUENCE_LENGTH = 36
        private const val FEATURES_PER_STEP = 1

        fun tryCreate(context: Context): TfliteNowcastPredictor? {
            return try {
                val mapped = mapAssetFile(context, MODEL_ASSET_PATH)
                val interpreter = Interpreter(mapped)
                TfliteNowcastPredictor(interpreter)
            } catch (_: Throwable) {
                null
            }
        }

        private fun mapAssetFile(context: Context, assetPath: String): MappedByteBuffer {
            context.assets.openFd(assetPath).use { fileDescriptor ->
                FileInputStream(fileDescriptor.fileDescriptor).channel.use { channel ->
                    return channel.map(
                        MapMode.READ_ONLY,
                        fileDescriptor.startOffset,
                        fileDescriptor.declaredLength
                    )
                }
            }
        }
    }
}
