package io.realm.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.utils.Printer
import java.io.FileWriter
import java.time.Instant

@AutoService(ComponentRegistrar::class)
class Registrar : ComponentRegistrar {

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

        PackageFragmentProviderExtension.registerExtension(project,
                object : PackageFragmentProviderExtension {
                    override fun getPackageFragmentProvider(project: Project, module: ModuleDescriptor, storageManager: StorageManager, trace: BindingTrace, moduleInfo: ModuleInfo?, lookupTracker: LookupTracker): PackageFragmentProvider? {
                        logger("Analyzing: ${project.name} ${module.name} ${storageManager} $moduleInfo")
                        val packages = mutableMapOf<FqName, () -> PackageFragmentDescriptor>()

                        return object: PackageFragmentProvider {
                            override fun collectPackageFragments(fqName: FqName, packageFragments: MutableCollection<PackageFragmentDescriptor>) {
                                logger("collect packages: $fqName $packageFragments")
//                                packageFragments.add(FqName("io.realm.fake"), PredefinedPackageFramentDesciptor)
                                packageFragments.add(object: PackageFragmentDescriptorImpl(module, FqName("io.realm.fake")) {
                                    override fun getMemberScope(): MemberScope {
                                        return object: MemberScopeImpl() {
                                            override fun printScopeStructure(p: Printer) {
                                                TODO("Not yet implemented")
                                            }

                                        }
                                    }

                                })
                            }

                            override fun getSubPackagesOf(fqName: FqName, nameFilter: (Name) -> Boolean): Collection<FqName> {
                                TODO("Not yet implemented")
                            }

                        }
                    }

                }
        )

        SyntheticResolveExtension.registerExtension(project, RealmModelSyntheticCompanionExtension())

        IrGenerationExtension.registerExtension(project, RealmModelLoweringExtension())
        IrGenerationExtension.registerExtension(project, MediatorIrExtension())
    }

}

// Logging to a temp file and to console/IDE (Build Output)
lateinit var messageCollector: MessageCollector
fun logger(message: String, severity: CompilerMessageSeverity = CompilerMessageSeverity.WARNING) {
    val formattedMessage = "[Kotlin Compiler] ${Instant.now()} $message\n"
    messageCollector.report(severity, formattedMessage)
    FileWriter("/tmp/kmp.log").use {
        it.append(formattedMessage)
    }
}
