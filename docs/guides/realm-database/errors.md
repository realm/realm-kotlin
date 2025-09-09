# Handle Realm Errors - Kotlin SDK
The Kotlin SDK uses a hierarchy of exceptions to help developers manage
API call failures.

## Realm Errors
Realm errors occur when a read or write to Realm fails.
These errors generate a RealmException.

When possible, the SDK uses existing platform exceptions, like
[IllegalArgumentException](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-illegal-argument-exception/index.html) or
[IllegalStateException](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-illegal-state-exception/index.html).

Typically, these errors result from bad database logic, such as a query
missing an argument, attempting to write outside of a write transaction, or
deleting an object that does not exist.

However, some errors are **ephemeral**: they occur because of failures outside of
the client or SDK's control. When an ephemeral error occurs, you should retry the operation that
caused the error.
If the operation still fails when you retry it, investigate logic fixes.

You can handle errors in the SDK with Kotlin's built-in
[runCatching](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/run-catching.html)
API. Use the `onSuccess` and `onFailure` callbacks of the returned
[Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/)
to handle successful SDK API calls and error cases.
