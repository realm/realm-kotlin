package io.realm

// Work-around for https://youtrack.jetbrains.com/issue/KT-31066?_ga=2.235017729.1815498384.1604908458-898145758.1597650375
inline fun Realm.use(block: (realm: Realm) -> Unit) {
    try {
        block(this)
    } finally {
        try {
            close()
        } catch (e: Exception) {
            // Log or rethrow? We might end up here if `block()` also threw an exception which should take priority?
        }
    }
}
