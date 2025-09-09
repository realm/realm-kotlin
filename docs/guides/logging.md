# Logging - Kotlin SDK
> Version added: 1.8.0

You can set or change your app's log level when developing or debugging
your application. You might want to change the log level to log different
amounts of data depending on your development needs.

## Set the Realm Log Level
You can set your app's log level using the global `RealmLog`
singleton. You can set the `RealmLog.level` property to an entry in the
`LogLevel`
enum to specify the level of data you want to receive. If the log level
priority is equal to or higher than the priority defined in `RealmLog.level`,
Realm logs the event.

You can change the log level at any point during the app's lifecycle from this
global singleton.

```kotlin
// Set a log level using the global RealmLog singleton
RealmLog.level = LogLevel.TRACE

// Access your app and use realm
val app: App = App.create(YOUR_APP_ID) // Replace this with your App ID
val user = app.login(Credentials.emailPassword(email, password))
val config = RealmConfiguration.Builder(setOf(Toad::class))
    .build()
val realm = Realm.open(config)

// You can change the log level at any point in your app's lifecycle as needed
RealmLog.level = LogLevel.INFO

```

By default, all logs go to a default system logger that varies by system:

- Android logs to Logcat.
- JVM logs to stdout.
- MacOS logs to NSLog.
- iOS logs to NSLog.

> **TIP:**
> To diagnose and troubleshoot errors while developing your application, set the
> log level to `debug` or `trace`. For production deployments, decrease the
> log level for improved performance.
>

### Set a Custom Logger
You can create a custom logger that implements the `RealmLogger`
interface. You might want to customize logging to add specific tags or set
specific log levels during development, testing, or debugging.

```kotlin
class MyLogger() : RealmLogger {
    override val tag: String = "CUSTOM_LOG_ENTRY"
    override val level: LogLevel = LogLevel.DEBUG
    override fun log(
        level: LogLevel,
        throwable: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        println(message) // Custom handling
    }
}

```

Then, you can initialize your custom logger and call the
`RealmLog.add()`
function to set it as a logger for your app.

You can also remove a specific logger or remove all loggers, including the system logger.

```kotlin
// Set an instance of a custom logger
val myCustomLogger = MyLogger()
RealmLog.add(myCustomLogger)

// You can remove a specific logger
RealmLog.remove(myCustomLogger)

// Or remove all loggers, including the default system logger
RealmLog.removeAll()

```
