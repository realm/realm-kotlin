package io.realm.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import java.io.FileWriter
import java.time.Instant

@AutoService(ComponentRegistrar::class)
class Registrar : ComponentRegistrar {

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        SyntheticResolveExtension.registerExtension(project, RealmModelSyntheticCompanionExtension())
        IrGenerationExtension.registerExtension(project, RealmModelLoweringExtension())
    }

}

// Logging to a temp file and to console/IDE (Build Output)
lateinit var messageCollector: MessageCollector
fun logger(message: String, severity: CompilerMessageSeverity = CompilerMessageSeverity.WARNING) {
    val formattedMessage = "[Realm Compiler Plugin] ${Instant.now()} $message\n"
    messageCollector.report(severity, formattedMessage)
    FileWriter("/tmp/kmp.log").use {
        it.append(formattedMessage)
    }
}