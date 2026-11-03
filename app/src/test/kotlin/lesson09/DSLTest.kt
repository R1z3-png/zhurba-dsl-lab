package lesson09

import lesson09.dsl.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import lesson09.dsl.render

class DSLTest {
    @Test
    fun `satisfies accepts lambda literal`() {
        val response = HttpResponse(
            status = 200,
            headers = mapOf("X-Trace" to listOf("trace-1")),
            body = null,
        )

        val result = validate(response) {
            header("X-Trace") {
                satisfies("non-blank value") { value ->
                    value.isNotBlank()
                }
            }
        }

        assertTrue(result.isSuccess)
    }

    @Test
    fun `satisfies accepts function type variable`() {
        val isNonBlank: (String) -> Boolean = { value ->
            value.isNotBlank()
        }

        val response = HttpResponse(
            status = 200,
            headers = mapOf("X-Trace" to listOf("trace-1")),
            body = null,
        )

        val result = validate(response) {
            header("X-Trace") {
                satisfies("non-blank value", isNonBlank)
            }
        }

        assertTrue(result.isSuccess)
    }

    private fun isNonBlank(value: String): Boolean =
        value.isNotBlank()

    @Test
    fun `satisfies accepts function reference`() {
        val response = HttpResponse(
            status = 200,
            headers = mapOf("X-Trace" to listOf("trace-1")),
            body = null,
        )

        val result = validate(response) {
            header("X-Trace") {
                satisfies("non-blank value", ::isNonBlank)
            }
        }

        assertTrue(result.isSuccess)
    }

    private fun nonBlankTrace(value: String): Boolean =
        value.isNotBlank()

    @Test
    fun `satisfies produces the same result for all predicate forms`() {
        val response = HttpResponse(
            status = 200,
            headers = mapOf("X-Trace" to listOf("")),
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
                satisfies("non-blank value", ::nonBlankTrace)
            }
        }

        assertEquals(lambdaResult, variableResult)
        assertEquals(lambdaResult, referenceResult)
        assertEquals(
            "headers.X-Trace: expected non-blank value, actual []",
            lambdaResult.render(),
        )
    }
}