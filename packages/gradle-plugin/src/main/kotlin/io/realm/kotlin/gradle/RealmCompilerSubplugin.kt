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

package io.realm.kotlin.gradle

import com.android.build.gradle.BaseExtension
import io.realm.kotlin.gradle.analytics.AnalyticsService
import io.realm.kotlin.gradle.analytics.BuilderId
import io.realm.kotlin.gradle.analytics.ComputerId
import io.realm.kotlin.gradle.analytics.HOST_ARCH
import io.realm.kotlin.gradle.analytics.HOST_OS
import io.realm.kotlin.gradle.analytics.ProjectConfiguration
import io.realm.kotlin.gradle.analytics.TargetInfo
import io.realm.kotlin.gradle.analytics.hexStringify
import io.realm.kotlin.gradle.analytics.sha256Hash
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildServiceSpec
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.COCOAPODS_EXTENSION_NAME
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinCommonCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinSharedNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

class RealmCompilerSubplugin : KotlinCompilerPluginSupportPlugin {

    private lateinit var anonymizedBundleId: String
    private var analyticsServiceProvider: Provider<AnalyticsService>? = null

    companion object {
        // TODO LATER Consider embedding these from the build.gradle's versionConstants task just
        //  as with the version. But leave it for now as they should be quite stable.
        // Modules has to match ${project.group}:${project.name} to make composite build work
        const val groupId = "io.realm.kotlin"
        const val artifactId = "plugin-compiler"
        const val version = PLUGIN_VERSION
        const val coreVersion = CORE_VERSION

        // The id used for passing compiler options from command line
        const val compilerPluginId = "io.realm.kotlin"

        // Must match io.realm.kotlin.compiler.bundleIdKey
        const val bundleIdKey = "bundleId"

        // Must match io.realm.kotlin.compiler.
        const val featureListPathKey = "featureListPath"
    }

    @Suppress("NestedBlockDepth")
    override fun apply(target: Project) {
        super.apply(target)

        // We build the anonymized bundle id here and pass it to the compiler plugin to ensure
        // that the metrics and sync connection parameters are aligned.
        val bundleId = target.rootProject.name + ":" + target.name
        anonymizedBundleId = hexStringify(sha256Hash(bundleId.toByteArray()))

        val disableAnalytics: Boolean = target.gradle.startParameter.isOffline || "true".equals(
            System.getenv()["REALM_DISABLE_ANALYTICS"],
            ignoreCase = true
        )
        if (!disableAnalytics) {
            // Identify if project is using sync by inspecting dependencies.
            // We cannot use resolved configurations here as this code is called in
            // afterEvaluate, and resolving it prevents other plugins from modifying
            // them. E.g the KMP plugin will crash if we resolve the configurations
            // in `afterEvaluate`. This means we can only see dependencies directly set,
            // and not their transitive dependencies. This should be fine as we only
            // want to track builds directly using Realm.
            var usesSync = false
            outer@
            for (conf in target.configurations) {
                for (dependency in conf.dependencies) {
                    if (dependency.group == "io.realm.kotlin" && dependency.name == "library-sync") {
                        // In Java we can detect Sync through a Gradle configuration closure.
                        // In Kotlin, this choice is currently determined by which dependency
                        // people include
                        usesSync = true
                        break@outer
                    }
                }
            }

            val userId = target.providers.of(ComputerId::class.java) {}.get()
            val builderId = target.providers.of(BuilderId::class.java) {}.get()
            val verbose = "true".equals(System.getenv()["REALM_PRINT_ANALYTICS"], ignoreCase = true)

            analyticsServiceProvider = target.gradle.sharedServices.registerIfAbsent(
                "Realm Analytics",
                AnalyticsService::class.java
            ) { spec: BuildServiceSpec<ProjectConfiguration> ->
                spec.parameters.run {
                    this.appId.set(anonymizedBundleId)
                    this.userId.set(userId)
                    this.builderId.set(builderId)
                    this.hostOsType.set(HOST_OS.serializedName)
                    this.hostOsVersion.set(System.getProperty("os.version"))
                    this.hostCpuArch.set(HOST_ARCH)
                    this.usesSync.set(usesSync)
                    this.verbose.set(verbose)
                }
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.target.project.plugins.findPlugin(RealmCompilerSubplugin::class.java) != null
    }

    override fun getCompilerPluginId(): String {
        return compilerPluginId
    }

    override fun getPluginArtifact(): SubpluginArtifact {
        // Modules has to match ${project.group}:${project.name} to make composite build work
        return SubpluginArtifact(groupId, artifactId, version)
    }

    override fun getPluginArtifactForNative(): SubpluginArtifact {
        return SubpluginArtifact(groupId, artifactId, version)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        // Compiler plugin options
        val options = mutableListOf(
            SubpluginOption(key = bundleIdKey, anonymizedBundleId),
        )
        // Only bother collecting info if the analytics service is registered
        analyticsServiceProvider?.let { provider ->
            // Enable feature collection in compiler plugin by setting a path for the feature list
            // file and pass it to the compiler plugin as a compiler plugin option
            val featureListPath = listOf(
                project.buildDir.path,
                "outputs",
                "realm-features",
                kotlinCompilation.defaultSourceSet.name
            ).joinToString(File.separator)
            options.add(SubpluginOption(key = featureListPathKey, featureListPath))

            // Gather target specific information
            val targetInfo: TargetInfo? = gatherTargetInfo(kotlinCompilation)
            // If we have something to submit register it for submission after the compilation has
            // gathered the feature list information
            targetInfo?.let {
                kotlinCompilation.compileTaskProvider.get().doLast {
                    analyticsServiceProvider!!.get().submit(targetInfo)
                }
            }
        }
        return project.provider {
            options
        }
    }
}

@Suppress("ComplexMethod", "NestedBlockDepth")
private fun gatherTargetInfo(kotlinCompilation: KotlinCompilation<*>): TargetInfo? {
    val project = kotlinCompilation.target.project
    return when (kotlinCompilation) {
        // We don't send metrics for common targets but still collect features as the
        // target specific features is a union of common and target specific features
        is KotlinCommonCompilation,
        is KotlinSharedNativeCompilation ->
            null

        is KotlinJvmAndroidCompilation -> {
            val androidExtension =
                project.extensions.findByName("android") as BaseExtension?
            val defaultConfig = androidExtension?.defaultConfig
            val minSDK = defaultConfig?.minSdkVersion?.apiString
            val targetSDK = defaultConfig?.targetSdkVersion?.apiString
            val targetCpuArch: String =
                defaultConfig?.ndk?.abiFilters?.singleOrNull()?.let { androidArch(it) }
                    ?: "Universal"
            TargetInfo("Android", targetCpuArch, targetSDK, minSDK)
        }

        is KotlinJvmCompilation -> {
            val jvmTarget = kotlinCompilation.kotlinOptions.jvmTarget
            TargetInfo("JVM", "Universal", jvmTarget, jvmTarget)
        }

        is KotlinNativeCompilation -> {
            // We currently only support Darwin targets, so assume that we can pull minSdk
            // from the given deploymentTarget. Non-CocoaPod Xcode project have this in its
            // pdxproj-file as <X>OS_DEPLOYMENT_TARGET, but assuming that most people use
            // CocoaPods as it is the default. Reevaluate if we see too many missing values.
            val kotlinExtension: KotlinMultiplatformExtension =
                project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val cocoapodsExtension =
                (kotlinExtension as ExtensionAware).extensions.getByName(
                    COCOAPODS_EXTENSION_NAME
                ) as CocoapodsExtension?
            val minSdk = cocoapodsExtension?.let { cocoapods ->
                when (kotlinCompilation.konanTarget.family) {
                    Family.OSX -> cocoapods.osx.deploymentTarget
                    Family.IOS -> cocoapods.ios.deploymentTarget
                    Family.TVOS -> cocoapods.tvos.deploymentTarget
                    Family.WATCHOS -> cocoapods.watchos.deploymentTarget
                    Family.LINUX,
                    Family.MINGW,
                    Family.ANDROID,
                    Family.WASM,
                    Family.ZEPHYR -> null // Not supported yet
                }
            }
            TargetInfo(
                nativeTarget(kotlinCompilation.konanTarget),
                nativeArch(kotlinCompilation.konanTarget),
                null,
                minSdk
            )
        }
        // Not supported yet so don't try to gather target information
        is KotlinJsCompilation,
        is KotlinWithJavaCompilation<*, *> -> null

        else -> {
            null
        }
    }
}

// Helper method to ensure that we align target type string for native builds
fun nativeTarget(target: KonanTarget) = when (target.family) {
    Family.OSX -> "macOS"
    Family.IOS -> "iOS"
    Family.TVOS -> "tvOS"
    Family.WATCHOS -> "watchOS"
    Family.LINUX -> "Linux"
    Family.MINGW -> "MinGW"
    Family.ANDROID -> "Android(native)"
    Family.WASM -> "Wasm"
    Family.ZEPHYR -> "Zephyr"
    else -> "Unknown[${target.family}]"
}

// Helper method to ensure that we align architecture strings for Kotlin native builds
fun nativeArch(target: KonanTarget) = when (target.architecture) {
    Architecture.X64 -> io.realm.kotlin.gradle.analytics.Architecture.X64.serializedName
    Architecture.X86 -> io.realm.kotlin.gradle.analytics.Architecture.X86.serializedName
    Architecture.ARM64 -> io.realm.kotlin.gradle.analytics.Architecture.ARM64.serializedName
    Architecture.ARM32 -> io.realm.kotlin.gradle.analytics.Architecture.ARM.serializedName
    Architecture.MIPS32 -> "Mips"
    Architecture.MIPSEL32 -> "MipsEL32"
    Architecture.WASM32 -> "Wasm"
    else -> "Unknown[${target.architecture}]"
}

// Helper method to ensure that we align architecture strings for Android platforms
fun androidArch(target: String): String = when (target) {
    "armeabi-v7a" -> io.realm.kotlin.gradle.analytics.Architecture.ARM.serializedName
    "arm64-v8a" -> io.realm.kotlin.gradle.analytics.Architecture.ARM64.serializedName
    "x86" -> io.realm.kotlin.gradle.analytics.Architecture.X86.serializedName
    "x86_64" -> io.realm.kotlin.gradle.analytics.Architecture.X64.serializedName
    else -> "Unknown[$target]"
}
