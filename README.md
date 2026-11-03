# HTTP response validation DSL

This project provides a small Kotlin DSL for validating in-memory `HttpResponse`
objects. Instead of returning only `true` or `false`, `validate` collects all
mismatches found during the check.

## Example

```kotlin
val result = validate(response) {
    status {
        equalTo(200)
    }

    header("Content-Type") {
        startsWith("application/json")
    }

    body {
        json {
            field("id") {
                present()
            }
        }
    }
}

if (!result.isSuccess) {
    println(result.render())
}
```

## Result paths

Each mismatch has a `path` that identifies the checked part of the response:

| Path | Meaning |
|---|---|
| `status` | HTTP response status code |
| `headers.<Name>` | All values of a response header |
| `body` | Raw response body |
| `body.json` | Parsed flat JSON object |
| `body.json.<field>` | A field of the parsed JSON object |

Examples:

- `status`
- `headers.Content-Type`
- `body`
- `body.json`
- `body.json.id`

## Running tests

Run all tests with the Gradle wrapper:

```bash
./gradlew test
```

On Windows:

```bash
gradlew.bat test
```

To run only the DSL test class:

```bash
./gradlew test --tests "lesson09.dsl.HttpChecksDslTest"
```

## Status and body rules

- `status { equalTo(expected) }` succeeds only when the response status is equal
  to `expected`.
- `body { absent() }` succeeds for `null` and `""`.
- `body { present() }` succeeds for any non-empty string, including a string
  containing only whitespace.
- A whitespace-only body is not considered empty.
- JSON is parsed only when `body { json { ... } }` is explicitly used.
- `body { present() }` does not parse or validate the body contents.

## Header rules

- Header names are matched case-insensitively.
- Header values are compared case-sensitively.
- Values are not trimmed and are not split by commas.
- If the response contains several header keys that differ only by letter case,
  their values are combined for validation.
- Combined values keep the iteration order of the `headers` map. Within each
  key, values keep their original order.
- Each header operation succeeds if at least one combined value satisfies it.
- Different operations may succeed on different values.

For example, these checks both succeed for the values
`["application/json", "text/html"]`:

```kotlin
header("Accept") {
    contains("application/json")
    contains("text/html")
}
```

The single value `"application/json,text/html"` does not satisfy either check,
because it is not split into separate values.

Prefix matching uses `String.startsWith`. Therefore,
`"application/jsonp"` starts with `"application/json"`.

## Failed header checks

For a failed header check, the actual value is rendered as:

- `<missing>` when no header key matching the requested name exists;
- `[]` when a matching key exists but its value list is empty;
- `[value1, value2]` when one or more values were found.

## JSON rules

- `body { json { ... } }` requires the body to be a valid flat JSON object.
- If parsing fails, one mismatch is reported for `body.json`, and field checks
  are not executed.
- `field(name) { present() }` succeeds when the JSON object contains the key,
  even if its value is `null`.
- `field(name) { equalTo(...) }` supports `String`, `Long`, and `Boolean`
  values, matching the supported JSON parser value types.

## DSL nesting

The DSL uses `@DslMarker` to prevent implicit use of an outer receiver inside
a nested block. For example, `status { ... }` cannot be called implicitly inside
`body { ... }`.

An explicit labeled receiver is still allowed:

```kotlin
validate(response) {
    body {
        this@validate.status {
            equalTo(200)
        }
    }
}
```

## Legacy checks

The original `check01`–`check06` functions remain the reference implementation.
Their DSL-based counterparts have the same success/failure outcome.

| Function | Passes when |
|---|---|
| `check01` | Status is `204`; body is `null` or `""` |
| `check02` | Status is `200`; at least one `Content-Type` value starts with `application/json`; body is non-null and non-empty |
| `check03` | Status is `200`; `Accept` contains both exact values `application/json` and `text/html` |
| `check04` | Status is `200`; body is a valid flat JSON object; `id` is present, including `null`; `name` equals `"Ivanov"` |
| `check05` | Status is `200`; body is a valid flat JSON object; `id` equals `42`; `active` equals `true` |
| `check06` | Status is `422`; at least one `Content-Type` value starts with `application/json`; body is a valid flat JSON object; `error` equals `"invalid"`; `retry` equals `false` |

Additional headers and JSON fields do not affect the result. `check02` does not
parse JSON, so a non-empty body with invalid JSON can still pass when the status
and header conditions are satisfied.

## Mismatch example

For a response with status `500`, header `Content-Type: text/plain`, and body
`{}`, this specification reports three independent mismatches:

```text
status: expected 200, actual 500
headers.Content-Type: expected prefix application/json, actual [text/plain]
body.json.id: expected present, actual <missing: field is absent>
```

A failed status check does not stop header or body checks.

## How it works

- `validate` creates a new `MismatchCollector` for every run and returns an
  immutable `ValidationResult`.
- Each DSL block has its own receiver: response, status, header, body, JSON
  object, and JSON field. A receiver exposes only operations for its level.
- `@DslMarker` prevents implicit calls to an outer block from a nested block.
- A custom header check uses `satisfies(description) { value -> ... }`. It
  succeeds if at least one header value satisfies the predicate.
- Reusing a stored specification is safe: every `validate` call gets its own
  collector, so previous results remain unchanged.