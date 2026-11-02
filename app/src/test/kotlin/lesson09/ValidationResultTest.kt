package lesson09

import lesson09.dsl.render
import lesson09.dsl.MismatchCollector
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class ValidationResultTest {
    @Test
    fun `collects mismatches in declaration order`() {
        val collector = MismatchCollector()

        collector.addValue("status", "200", "500")
        collector.addValue("headers.Content-Type", "prefix application/json", "[text/plain]")
        collector.addMissing("body.json.id", "present", "field is absent")

        val result = collector.build()

        assertFalse(result.isSuccess)
        assertEquals(
            listOf("status", "headers.Content-Type", "body.json.id"),
            result.mismatches.map { it.path },
        )
        assertEquals(
            "status: expected 200, actual 500\n" +
                    "headers.Content-Type: expected prefix application/json, actual [text/plain]\n" +
                    "body.json.id: expected present, actual <missing: field is absent>",
            result.render(),
        )
    }
}