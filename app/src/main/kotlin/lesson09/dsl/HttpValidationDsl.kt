package lesson09.dsl

import lesson09.HttpResponse
import lesson09.support.json.JsonParseResult
import lesson09.support.json.parseJsonObject


fun validate(
    response: HttpResponse,
    block: HttpResponseValidation.() -> Unit,
): ValidationResult {
    val collector = MismatchCollector()
    val validation = HttpResponseValidation(response, collector)

    validation.block()

    return collector.build()
}

@DslMarker
annotation class HttpValidationDsl

@HttpValidationDsl
class HttpResponseValidation(
    private val response: HttpResponse,
    private val collector: MismatchCollector,
) {
    fun status(block: StatusValidation.() -> Unit) {
        StatusValidation(response.status, collector).block()
    }

    fun header(
        name: String,
        block: HeaderValidation.() -> Unit,
    ) {
        val matchingEntries = response.headers.entries
            .filter { it.key.equals(name, ignoreCase = true) }

        val values = matchingEntries.flatMap { it.value }

        HeaderValidation(
            name = name,
            values = values,
            isPresent = matchingEntries.isNotEmpty(),
            collector = collector,
        ).block()
    }

    fun body(block: BodyValidation.() -> Unit) {
        BodyValidation(response.body, collector).block()
    }
}

@HttpValidationDsl
class StatusValidation(
    private val actualStatus: Int,
    private val collector: MismatchCollector,
) {
    fun equalTo(expected: Int) {
        if (actualStatus != expected) {
            collector.addValue(
                path = "status",
                expected = expected.toString(),
                actualText = actualStatus.toString(),
            )
        }
    }
}

@HttpValidationDsl
class HeaderValidation(
    private val name: String,
    private val values: List<String>,
    private val isPresent: Boolean,
    private val collector: MismatchCollector,
) {
    private val path: String
        get() = "headers.$name"

    fun contains(expected: String) {
        if (values.none { it == expected }) {
            collector.addValue(
                path = path,
                expected = "value $expected",
                actualText = actualHeaderText(),
            )
        }
    }

    fun startsWith(prefix: String) {
        if (values.none { it.startsWith(prefix) }) {
            collector.addValue(
                path = path,
                expected = "prefix $prefix",
                actualText = actualHeaderText(),
            )
        }
    }

    fun satisfies(
        description: String,
        predicate: (String) -> Boolean,
    ) {
        if (values.none(predicate)) {
            collector.addValue(
                path = path,
                expected = description,
                actualText = actualHeaderText(),
            )
        }
    }



    private fun actualHeaderText(): String =
        if (!isPresent) {
            "<missing>"
        } else {
            values.toString()
        }
}


@HttpValidationDsl
class BodyValidation(
    private val body: String?,
    private val collector: MismatchCollector,
) {
    fun absent() {
        if (body != null && body.isNotEmpty()) {
            collector.addValue(
                path = "body",
                expected = "absent or empty",
                actualText = body,
            )
        }
    }

    fun present() {
        if (body == null || body.isEmpty()) {
            collector.addMissing(
                path = "body",
                expected = "non-empty string",
                reason = if (body == null) "body is null" else "body is empty",
            )
        }
    }

    fun json(block: JsonValidation.() -> Unit) {
        val fields = when (val parseResult = parseJsonObject(body ?: "")) {
            is JsonParseResult.Success -> parseResult.fields
            is JsonParseResult.Failure -> {
                collector.addMissing(
                    path = "body.json",
                    expected = "valid JSON object",
                    reason = parseResult.reason,
                )
                return
            }
        }

        JsonValidation(fields, collector).block()
    }
}

@HttpValidationDsl
class JsonValidation(
    private val fields: Map<String, Any?>,
    private val collector: MismatchCollector,
) {
    fun field(
        name: String,
        block: JsonFieldValidation.() -> Unit,
    ) {
        JsonFieldValidation(
            path = "body.json.$name",
            value = fields[name],
            isPresent = fields.containsKey(name),
            collector = collector,
        ).block()
    }
}

@HttpValidationDsl
class JsonFieldValidation(
    private val path: String,
    private val value: Any?,
    private val isPresent: Boolean,
    private val collector: MismatchCollector,
) {
    fun present() {
        if (!isPresent) {
            collector.addMissing(
                path = path,
                expected = "present",
                reason = "field is absent",
            )
        }
    }

    fun equalTo(expected: String) {
        if (!isPresent) {
            reportMissing(expected = "string $expected")
        } else if (value != expected) {
            reportValueMismatch(expected = "string $expected")
        }
    }

    fun equalTo(expected: Long) {
        if (!isPresent) {
            reportMissing(expected = "integer $expected")
        } else if (value != expected) {
            reportValueMismatch(expected = "integer $expected")
        }
    }

    fun equalTo(expected: Boolean) {
        if (!isPresent) {
            reportMissing(expected = "boolean $expected")
        } else if (value != expected) {
            reportValueMismatch(expected = "boolean $expected")
        }
    }

    private fun reportMissing(expected: String) {
        collector.addMissing(
            path = path,
            expected = expected,
            reason = "field is absent",
        )
    }

    private fun reportValueMismatch(expected: String) {
        collector.addValue(
            path = path,
            expected = expected,
            actualText = value.toString(),
        )
    }
}

