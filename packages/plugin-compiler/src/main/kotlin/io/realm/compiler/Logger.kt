/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.compiler

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import java.time.Instant

// Logging to a temp file and to console/IDE (Build Output)
lateinit var messageCollector: MessageCollector
private fun logger(message: String, severity: CompilerMessageSeverity = CompilerMessageSeverity.WARNING) {
    val formattedMessage by lazy {
        "[Realm Compiler Plugin] ${Instant.now()} $message\n"
    }
    messageCollector.report(severity, formattedMessage)
}
fun logInfo(message: String) = logger(message, severity = CompilerMessageSeverity.INFO)
fun logWarn(message: String) = logger(message, severity = CompilerMessageSeverity.WARNING)
fun logError(message: String) = logger(message, severity = CompilerMessageSeverity.ERROR) // /!\ This will log and fail the compilation /!\
