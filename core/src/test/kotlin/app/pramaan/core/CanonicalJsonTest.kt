package app.pramaan.core

import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.model.Landmark
import app.pramaan.core.model.PoseFrame
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.io.StringWriter

class CanonicalJsonTest {

    @Serializable
    data class Sample(
        val z: String,
        val a: Int,
        val m: Double
    )

    @Serializable
    data class NestedSample(
        val b: List<String>,
        val a: Inner,
        val c: List<Inner>
    )

    @Serializable
    data class Inner(
        val y: Int,
        val x: String
    )

    @Test
    fun `keys are sorted alphabetically at every level`() {
        val s = Sample(z = "zebra", a = 1, m = 2.5)
        val json = CanonicalJson.encodeToString(s)
        json shouldBe """{"a":1,"m":2.5,"z":"zebra"}"""
    }

    @Test
    fun `same output regardless of map insertion order`() {
        val map1 = buildJsonObject {
            put("z", "val_z")
            put("a", "val_a")
            put("m", "val_m")
        }
        val map2 = buildJsonObject {
            put("a", "val_a")
            put("m", "val_m")
            put("z", "val_z")
        }
        val json1 = CanonicalJson.format(map1)
        val json2 = CanonicalJson.format(map2)

        json1 shouldBe json2
        json1 shouldBe """{"a":"val_a","m":"val_m","z":"val_z"}"""
    }

    @Test
    fun `nested objects have keys sorted recursively`() {
        val nested = NestedSample(
            b = listOf("one", "two"),
            a = Inner(y = 20, x = "twenty"),
            c = listOf(Inner(y = 2, x = "two"), Inner(y = 1, x = "one"))
        )
        val json = CanonicalJson.encodeToString(nested)
        json shouldBe """{"a":{"x":"twenty","y":20},"b":["one","two"],"c":[{"x":"two","y":2},{"x":"one","y":1}]}"""
    }

    @Test
    fun `arrays preserve item order`() {
        val element = Json.parseToJsonElement("""{"arr":[3,1,2]}""")
        CanonicalJson.format(element) shouldBe """{"arr":[3,1,2]}"""
    }

    @Test
    fun `numbers are formatted stably`() {
        val element = Json.parseToJsonElement("""{"pos":42,"neg":-15,"flt":38.2,"zero":0}""")
        CanonicalJson.format(element) shouldBe """{"flt":38.2,"neg":-15,"pos":42,"zero":0}"""
    }

    @Test
    fun `unicode characters and escaping are handled properly`() {
        val element = Json.parseToJsonElement("""{"greeting":"नमस्ते","quote":"say \"hello\"","newline":"line1\nline2"}""")
        CanonicalJson.format(element) shouldBe """{"greeting":"नमस्ते","newline":"line1\nline2","quote":"say \"hello\""}"""
    }

    @Test
    fun `pose frame jsonl writes with 4 decimals and reads correctly`() {
        val landmarks = (0..32).map { i ->
            Landmark(
                x = 0.51234f + i * 0.001f,
                y = 0.31219f,
                visibility = 0.98f
            )
        }
        val frame = PoseFrame(t = 918273645123L, landmarks = landmarks)

        val line = PoseFrameJsonl.writeLine(frame)
        line.startsWith("""{"t":918273645123,"lm":[[""") shouldBe true
        line.contains("[0.5123,0.3122,0.98]") shouldBe true

        val parsed = PoseFrameJsonl.readLine(line)
        parsed.t shouldBe 918273645123L
        parsed.landmarks.size shouldBe 33
        parsed.landmarks[0].x shouldBe 0.5123f
        parsed.landmarks[0].y shouldBe 0.3122f
        parsed.landmarks[0].visibility shouldBe 0.98f

        // Roundtrip multiple frames via Reader/Writer
        val writer = StringWriter()
        PoseFrameJsonl.writeAll(writer, listOf(frame, frame.copy(t = 918273645200L)))
        val readFrames = PoseFrameJsonl.readAll(StringReader(writer.toString()))
        readFrames.size shouldBe 2
        readFrames[1].t shouldBe 918273645200L
    }
}
