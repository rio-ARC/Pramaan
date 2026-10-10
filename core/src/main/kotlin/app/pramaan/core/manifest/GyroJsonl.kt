package app.pramaan.core.manifest

import app.pramaan.core.model.GyroSample
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.BufferedReader
import java.io.Reader
import java.io.Writer
import java.util.Locale

/**
 * Reader and writer for GyroSample lines in gyro.jsonl files.
 * Line format: {"t":918273645123,"x":0.0012,"y":0.0023,"z":-0.0008}
 */
object GyroJsonl {
    private val json = Json { ignoreUnknownKeys = true }

    fun writeLine(sample: GyroSample): String {
        return buildString {
            append("{\"t\":").append(sample.t)
            append(",\"x\":").append(formatDouble(sample.x))
            append(",\"y\":").append(formatDouble(sample.y))
            append(",\"z\":").append(formatDouble(sample.z))
            append("}")
        }
    }

    fun readLine(line: String): GyroSample {
        val root = json.parseToJsonElement(line.trim()).jsonObject
        val t = root["t"]?.jsonPrimitive?.long
            ?: throw IllegalArgumentException("Missing 't' in GyroSample JSON: $line")
        val x = root["x"]?.jsonPrimitive?.double ?: 0.0
        val y = root["y"]?.jsonPrimitive?.double ?: 0.0
        val z = root["z"]?.jsonPrimitive?.double ?: 0.0
        return GyroSample(t = t, x = x, y = y, z = z)
    }

    fun writeAll(writer: Writer, samples: Iterable<GyroSample>) {
        for (sample in samples) {
            writer.write(writeLine(sample))
            writer.write("\n")
        }
        writer.flush()
    }

    fun readAll(reader: Reader): List<GyroSample> {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        return bufferedReader.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { readLine(it) }
            .toList()
    }

    private fun formatDouble(v: Double): String {
        val rounded = Math.round(v * 10000.0) / 10000.0
        if (rounded == rounded.toLong().toDouble()) {
            return rounded.toLong().toString()
        }
        val formatted = String.format(Locale.US, "%.4f", rounded)
        return formatted.trimEnd('0').trimEnd('.')
    }
}
