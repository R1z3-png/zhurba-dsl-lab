package lesson09.dsl

/**
 * Path notation used by the DSL:
 * - `status`
 * - `headers.Content-Type`
 * - `body`
 * - `body.json`
 * - `body.json.id`
 */
data class ValidationResult(val mismatches: List<Mismatch>) {
    val isSuccess: Boolean
        get() = mismatches.isEmpty()

    companion object {
        val success: ValidationResult = ValidationResult(emptyList())

        fun of(mismatches: List<Mismatch>): ValidationResult =
            ValidationResult(mismatches.toList())
    }
}

data class Mismatch(
    val path: String,
    val expected: String,
    val actual: ActualValue,
)

sealed interface ActualValue {
    data class Value(val text: String) : ActualValue
    data class Missing(val reason: String) : ActualValue
}

fun ValidationResult.render(): String =
    if (isSuccess) {
        "No mismatches"
    } else {
        mismatches.joinToString(separator = "\n") { mismatch ->
            "${mismatch.path}: expected ${mismatch.expected}, actual ${mismatch.actual.render()}"
        }
    }

private fun ActualValue.render(): String = when (this) {
    is ActualValue.Value -> text
    is ActualValue.Missing -> "<missing: $reason>"
}

class MismatchCollector {
    private val items = mutableListOf<Mismatch>()

    fun add(
        path: String,
        expected: String,
        actual: ActualValue,
    ) {
        items += Mismatch(path, expected, actual)
    }

    fun addValue(
        path: String,
        expected: String,
        actualText: String,
    ) {
        add(path, expected, ActualValue.Value(actualText))
    }

    fun addMissing(
        path: String,
        expected: String,
        reason: String,
    ) {
        add(path, expected, ActualValue.Missing(reason))
    }

    fun build(): ValidationResult =
        ValidationResult.of(items)
}