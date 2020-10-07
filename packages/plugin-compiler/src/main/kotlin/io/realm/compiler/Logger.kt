package io.realm.compiler

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import java.io.FileWriter
import java.time.Instant

// Logging to a temp file and to console/IDE (Build Output)
lateinit var messageCollector: MessageCollector
private fun logger(message: String, severity: CompilerMessageSeverity = CompilerMessageSeverity.WARNING) {
    val formattedMessage = "[Realm Compiler Plugin] ${Instant.now()} $message\n"
    messageCollector.report(severity, formattedMessage)
    FileWriter("/tmp/kmp.log").use {
        it.append(formattedMessage)
    }
}
fun logInfo(message: String) = logger(message, severity = CompilerMessageSeverity.INFO)
fun logWarn(message: String) = logger(message, severity = CompilerMessageSeverity.WARNING)
fun logError(message: String) = logger(message, severity = CompilerMessageSeverity.ERROR) // /!\ This will log and fail the compilation /!\