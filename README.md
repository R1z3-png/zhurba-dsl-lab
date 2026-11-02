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