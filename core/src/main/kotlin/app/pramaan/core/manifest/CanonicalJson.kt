package app.pramaan.core.manifest

import app.pramaan.core.model.Landmark
import app.pramaan.core.model.PoseFrame
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.serializer
import java.io.BufferedReader
import java.io.Reader
import java.io.Writer
import java.util.Locale

/**
 * Encodes any @Serializable object to canonical JSON:
 * - Keys sorted alphabetically at every object level
 * - No whitespace outside string literals
 * - Stable number and string formatting
 * - Deterministic UTF-8 byte output
 */
object CanonicalJson {
    @PublishedApi
    internal val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    inline fun <reified T> encodeToString(value: T): String {
        return encodeToString(serializer<T>(), value)
    }

    fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        val element = json.encodeToJsonElement(serializer, value)
        return format(element)
    }

    inline fun <reified T> encodeToByteArray(value: T): ByteArray {
        return encodeToString(value).toByteArray(Charsets.UTF_8)
    }

    fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        return encodeToString(serializer, value).toByteArray(Charsets.UTF_8)
    }

    inline fun <reified T> decodeFromString(string: String): T {
        return json.decodeFromString(string)
    }

    fun <T> decodeFromString(deserializer: kotlinx.serialization.DeserializationStrategy<T>, string: String): T {
        return json.decodeFromString(deserializer, string)
    }

    inline fun <reified T> decodeFromByteArray(bytes: ByteArray): T {
        return json.decodeFromString(bytes.decodeToString())
    }

    fun <T> decodeFromByteArray(deserializer: kotlinx.serialization.DeserializationStrategy<T>, bytes: ByteArray): T {
        return json.decodeFromString(deserializer, bytes.decodeToString())
    }

    fun format(element: JsonElement): String = buildString {
        appendCanonical(element, this)
    }

    private fun appendCanonical(element: JsonElement, sb: StringBuilder) {
        when (element) {
            is JsonObject -> {
                sb.append('{')
                val sortedKeys = element.keys.sorted()
                for (i in sortedKeys.indices) {
                    if (i > 0) sb.append(',')
                    val key = sortedKeys[i]
                    escapeString(key, sb)
                    sb.append(':')
                    appendCanonical(element.getValue(key), sb)
                }
                sb.append('}')
            }
            is JsonArray -> {
                sb.append('[')
                for (i in element.indices) {
                    if (i > 0) sb.append(',')
                    appendCanonical(element[i], sb)
                }
                sb.append(']')
            }
            is JsonPrimitive -> {
                if (element.isString) {
                    escapeString(element.content, sb)
                } else {
                    sb.append(element.content)
                }
            }
        }
    }

    private fun escapeString(str: String, sb: StringBuilder) {
        sb.append('"')
        for (ch in str) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch < ' ') {
                        sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }
}

/**
 * Reader and writer for PoseFrame lines in JSONL files (matching PRD 9.2).
 * Line format: {"t":918273645123,"lm":[[0.5123,0.3121,0.98],[0.5101,0.2950,0.97]]}
 * lm coordinates are rounded to at most 4 decimal places.
 */
object PoseFrameJsonl {
    private val json = Json { ignoreUnknownKeys = true }

    fun writeLine(frame: PoseFrame): String {
        val sb = StringBuilder()
        sb.append("{\"t\":").append(frame.t).append(",\"lm\":[")
        val landmarks = frame.landmarks
        for (i in landmarks.indices) {
            if (i > 0) sb.append(',')
            val lm = landmarks[i]
            sb.append('[')
                .append(formatCoordinate(lm.x)).append(',')
                .append(formatCoordinate(lm.y)).append(',')
                .append(formatCoordinate(lm.visibility))
                .append(']')
        }
        sb.append("]}")
        return sb.toString()
    }

    fun readLine(line: String): PoseFrame {
        val root = json.parseToJsonElement(line.trim()).jsonObject
        val t = root["t"]?.jsonPrimitive?.long
            ?: throw IllegalArgumentException("Missing 't' in PoseFrame JSON: $line")
        val lmArray = root["lm"]?.jsonArray
            ?: throw IllegalArgumentException("Missing 'lm' in PoseFrame JSON: $line")

        val landmarks = lmArray.map { item ->
            val arr = item.jsonArray
            val x = arr[0].jsonPrimitive.float
            val y = arr[1].jsonPrimitive.float
            val vis = if (arr.size > 2) arr[2].jsonPrimitive.float else 1.0f
            Landmark(x, y, vis)
        }
        return PoseFrame(t = t, landmarks = landmarks)
    }

    fun writeAll(writer: Writer, frames: Iterable<PoseFrame>) {
        for (frame in frames) {
            writer.write(writeLine(frame))
            writer.write("\n")
        }
        writer.flush()
    }

    fun readAll(reader: Reader): List<PoseFrame> {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        return bufferedReader.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { readLine(it) }
            .toList()
    }

    private fun formatCoordinate(v: Float): String {
        val rounded = Math.round(v * 10000.0) / 10000.0
        if (rounded == rounded.toLong().toDouble()) {
            return rounded.toLong().toString()
        }
        val formatted = String.format(Locale.US, "%.4f", rounded)
        return formatted.trimEnd('0').trimEnd('.')
    }
}
