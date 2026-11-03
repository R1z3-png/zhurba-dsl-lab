package lesson09.dsl

import lesson09.HttpResponse
import lesson09.legacy.check01 as check01Legacy
import lesson09.legacy.check02 as check02Legacy
import lesson09.legacy.check03 as check03Legacy
import lesson09.legacy.check04 as check04Legacy
import lesson09.legacy.check05 as check05Legacy
import lesson09.legacy.check06 as check06Legacy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun assertSameOutcome(
    legacyResult: Boolean,
    dslResult: ValidationResult,
) {
    assertEquals(legacyResult, dslResult.isSuccess)
}

private fun assertMismatches(
    result: ValidationResult,
    vararg expected: Mismatch,
) {
    assertEquals(expected.toList(), result.mismatches)
}

class HttpChecksDslTest {
    @Test
    fun `check01 passes for no-content response`() {
        val response = HttpResponse(
            status = 204,
            headers = emptyMap(),
            body = null,
        )

        val result = check01(response)

        assertSameOutcome(check01Legacy(response), result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `check01 reports status and body independently`() {
        val response = HttpResponse(
            status = 200,
            headers = emptyMap(),
            body = "content",
        )

        val result = check01(response)

        assertSameOutcome(check01Legacy(response), result)
        assertMismatches(
            result,
            Mismatch("status", "204", ActualValue.Value("200")),
            Mismatch("body", "absent or empty", ActualValue.Value("content")),
        )
    }

    @Test
    fun `check01 accepts empty body and rejects whitespace body`() {
        val emptyBody = HttpResponse(204, emptyMap(), "")
        val whitespaceBody = HttpResponse(204, emptyMap(), " ")

        assertTrue(check01(emptyBody).isSuccess)
        assertSameOutcome(check01Legacy(whitespaceBody), check01(whitespaceBody))
        assertTrue(check01(whitespaceBody).mismatches.isNotEmpty())
    }

    @Test
    fun `check02 passes for json response`() {
        val response = HttpResponse(
            status = 200,
            headers = mapOf("Content-Type" to listOf("application/json")),
            body = "not-json",
        )

        val result = check02(response)

        assertSameOutcome(check02Legacy(response), result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `check02 does not parse invalid json`() {
        val response = HttpResponse(
            status = 200,
            headers = mapOf("Content-Type" to listOf("application/json")),
            body = "not json",
        )

        val result = check02(response)

        assertSameOutcome(check02Legacy(response), result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `check02 reports all independent mismatches`() {
        val response = HttpResponse(
            status = 500,
            headers = mapOf("content-type" to listOf("text/plain")),
            body = "",
        )

        val result = check02(response)

        assertSameOutcome(check02Legacy(response), result)
        assertMismatches(
            result,
            Mismatch("status", "200", ActualValue.Value("500")),
            Mismatch("headers.Content-Type", "prefix application/json", ActualValue.Value("[text/plain]")),
            Mismatch("body", "non-empty string", ActualValue.Missing("body is empty")),
        )
    }

    @Test
    fun `check03 accepts values in differently cased keys`() {
        val response = HttpResponse(
            status = 200,
            headers = mapOf(
                "accept" to listOf("application/json"),
                "ACCEPT" to listOf("text/html"),
            ),
            body = null,
        )

        val result = check03(response)

        assertSameOutcome(check03Legacy(response), result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `check03 rejects comma-joined value`() {
        val response = HttpResponse(
            status = 200,
            headers = mapOf("Accept" to listOf("application/json,text/html")),
            body = null,
        )

        val result = check03(response)

        assertSameOutcome(check03Legacy(response), result)
        assertMismatches(
            result,
            Mismatch("headers.Accept", "value application/json", ActualValue.Value("[application/json,text/html]")),
            Mismatch("headers.Accept", "value text/html", ActualValue.Value("[application/json,text/html]")),
        )
    }

    @Test
    fun `check03 distinguishes missing header and empty values`() {
        val missing = HttpResponse(200, emptyMap(), null)
        val emptyValues = HttpResponse(
            status = 200,
            headers = mapOf("Accept" to emptyList()),
            body = null,
        )

        assertSameOutcome(check03Legacy(missing), check03(missing))
        assertSameOutcome(check03Legacy(emptyValues), check03(emptyValues))

        assertEquals(
            "headers.Accept: expected value application/json, actual <missing>\n" +
                    "headers.Accept: expected value text/html, actual <missing>",
            check03(missing).render(),
        )
        assertEquals(
            "headers.Accept: expected value application/json, actual []\n" +
                    "headers.Accept: expected value text/html, actual []",
            check03(emptyValues).render(),
        )
    }

    @Test
    fun `check04 accepts null id and ignores extra fields`() {
        val response = HttpResponse(
            status = 200,
            headers = mapOf("X-Extra" to listOf("ignored")),
            body = """{"id":null,"name":"Ivanov","extra":1}""",
        )

        val result = check04(response)

        assertSameOutcome(check04Legacy(response), result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `check04 reports json and field problems independently`() {
        val response = HttpResponse(
            status = 500,
            headers = emptyMap(),
            body = "{",
        )

        val result = check04(response)

        assertSameOutcome(check04Legacy(response), result)
        assertMismatches(
            result,
            Mismatch("status", "200", ActualValue.Value("500")),
            Mismatch("body.json", "valid JSON object", ActualValue.Missing("Expected a field name")),
        )
    }

    @Test
    fun `check04 reports missing id and wrong name`() {
        val response = HttpResponse(
            status = 200,
            headers = emptyMap(),
            body = """{"name":"Petrov"}""",
        )

        val result = check04(response)

        assertSameOutcome(check04Legacy(response), result)
        assertMismatches(
            result,
            Mismatch("body.json.id", "present", ActualValue.Missing("field is absent")),
            Mismatch("body.json.name", "string Ivanov", ActualValue.Value("Petrov")),
        )
    }

    @Test
    fun `check05 passes for expected fields`() {
        val response = HttpResponse(
            status = 200,
            headers = emptyMap(),
            body = """{"id":42,"active":true}""",
        )

        val result = check05(response)

        assertSameOutcome(check05Legacy(response), result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `check05 reports type mismatches`() {
        val response = HttpResponse(
            status = 200,
            headers = emptyMap(),
            body = """{"id":"42","active":"true"}""",
        )

        val result = check05(response)

        assertSameOutcome(check05Legacy(response), result)
        assertMismatches(
            result,
            Mismatch("body.json.id", "integer 42", ActualValue.Value("42")),
            Mismatch("body.json.active", "boolean true", ActualValue.Value("true")),
        )
    }

    @Test
    fun `check06 passes for validation error response`() {
        val response = HttpResponse(
            status = 422,
            headers = mapOf("Content-Type" to listOf("application/json")),
            body = """{"error":"invalid","retry":false}""",
        )

        val result = check06(response)

        assertSameOutcome(check06Legacy(response), result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `check06 reports header and json errors together`() {
        val response = HttpResponse(
            status = 422,
            headers = mapOf("Content-Type" to listOf("text/plain")),
            body = """{"error":"other","retry":true}""",
        )

        val result = check06(response)

        assertSameOutcome(check06Legacy(response), result)
        assertMismatches(
            result,
            Mismatch("headers.Content-Type", "prefix application/json", ActualValue.Value("[text/plain]")),
            Mismatch("body.json.error", "string invalid", ActualValue.Value("other")),
            Mismatch("body.json.retry", "boolean false", ActualValue.Value("true")),
        )
    }

    @Test
    fun `repeated validation runs are independent`() {
        val validation: HttpResponseValidation.() -> Unit = {
            status {
                equalTo(200)
            }
            header("X-Trace") {
                satisfies("non-blank value") { value ->
                    value.isNotBlank()
                }
            }
        }

        val goodResponse = HttpResponse(
            status = 200,
            headers = mapOf("X-Trace" to listOf("trace-1")),
            body = null,
        )

        val badResponse = HttpResponse(
            status = 500,
            headers = mapOf("X-Trace" to listOf("")),
            body = null,
        )

        val first = validate(goodResponse, validation)
        val second = validate(badResponse, validation)
        val third = validate(goodResponse, validation)

        assertTrue(first.isSuccess)
        assertTrue(second.mismatches.isNotEmpty())
        assertTrue(third.isSuccess)

        assertEquals(first, third)
        assertEquals(
            listOf(
                Mismatch("status", "200", ActualValue.Value("500")),
                Mismatch(
                    "headers.X-Trace",
                    "non-blank value",
                    ActualValue.Value("[]"),
                ),
            ),
            second.mismatches,
        )
    }

    @Test
    fun `validation does not modify input response`() {
        val headers = mapOf(
            "Content-Type" to listOf("application/json"),
            "content-type" to listOf("text/html"),
        )

        val response = HttpResponse(
            status = 200,
            headers = headers,
            body = """{"id":42,"active":true}""",
        )

        val before = response.copy()

        check02(response)
        check03(response)
        check05(response)

        assertEquals(before, response)
    }

    @Test
    fun `duplicate failing checks produce duplicate mismatches`() {
        val response = HttpResponse(
            status = 500,
            headers = emptyMap(),
            body = null,
        )

        val result = validate(response) {
            status {
                equalTo(200)
            }
            status {
                equalTo(200)
            }
        }

        assertMismatches(
            result,
            Mismatch("status", "200", ActualValue.Value("500")),
            Mismatch("status", "200", ActualValue.Value("500")),
        )
    }

    private fun isNonBlankTrace(value: String): Boolean =
        value.isNotBlank()

    @Test
    fun `satisfies accepts predicate in three forms`() {
        val response = HttpResponse(
            status = 200,
            headers = mapOf("X-Trace" to listOf("trace-1")),
            body = null,
        )

        val isNonBlank: (String) -> Boolean = { value ->
            value.isNotBlank()
        }

        val lambdaResult = validate(response) {
            header("X-Trace") {
                satisfies("non-blank value") { value ->
                    value.isNotBlank()
                }
            }
        }

        val variableResult = validate(response) {
            header("X-Trace") {
                satisfies("non-blank value", isNonBlank)
            }
        }

        val referenceResult = validate(response) {
            header("X-Trace") {
                satisfies("non-blank value", ::isNonBlankTrace)
            }
        }

        assertEquals(lambdaResult, variableResult)
        assertEquals(lambdaResult, referenceResult)
        assertTrue(lambdaResult.isSuccess)
    }

}