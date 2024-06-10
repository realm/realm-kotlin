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
import io.realm.kotlin.gradle.analytics.AnalyticsErrorCatcher
import io.realm.kotlin.gradle.analytics.AnalyticsService
import io.realm.kotlin.gradle.analytics.AnalyticsService.Companion.UNKNOWN
import io.realm.kotlin.gradle.analytics.AnalyticsService.Companion.unknown
import io.realm.kotlin.gradle.analytics.BuilderId
import io.realm.kotlin.gradle.analytics.ComputerId
import io.realm.kotlin.gradle.analytics.HOST_ARCH_NAME
import io.realm.kotlin.gradle.analytics.HOST_OS_NAME
import io.realm.kotlin.gradle.analytics.ProjectConfiguration
import io.realm.kotlin.gradle.analytics.TargetInfo
import io.realm.kotlin.gradle.analytics.hexStringify
import io.realm.kotlin.gradle.analytics.sha256Hash
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildServiceSpec
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.COCOAPODS_EXTENSION_NAME
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
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

internal val gradleVersion: GradleVersion = GradleVersion.current().baseVersion
internal val gradle70: GradleVersion = GradleVersion.version("7.0")
internal val gradle75: GradleVersion = GradleVersion.version("7.5")

class RealmCompilerSubplugin : KotlinCompilerPluginSupportPlugin, AnalyticsErrorCatcher {

    /**
     * Flag indicating whether we should submit analytics data to the remote endpoint
     */
    private var submitAnalytics: Boolean = true
    /**
     * Flag indicating whether we should submit analytics data to the remote endpoint
     */
    private var printAnalytics: Boolean = false

    /**
     * Flag to control if an exception in analytics collection should be causing the whole build to
     * fail or just be logged and submitted as [UNKNOWN].
     */
    override var failOnAnalyticsError: Boolean = false

    private var analyticsServiceProvider: Provider<AnalyticsService>? = null

    private lateinit var anonymizedBundleId: String

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

        printAnalytics = target.providers.environmentVariable("REALM_PRINT_ANALYTICS").getBoolean()
        submitAnalytics = !target.gradle.startParameter.isOffline && !target.providers.environmentVariable("REALM_DISABLE_ANALYTICS").getBoolean()
        // We never want to break a user's build if collecting/submitting some data fail, so by
        // default we are suppressing errors. This flag can control if errors are suppressed or will
        // break the build. This allows us to catch errors during development and on CI builds.
        failOnAnalyticsError = target.providers.environmentVariable("REALM_FAIL_ON_ANALYTICS_ERRORS").getBoolean()

        // Only register analytics service provider if we either want to submit or print the info
        if (submitAnalytics || printAnalytics) {
            analyticsServiceProvider = provider(target)
        }
    }

    private fun provider(target: Project) =
        target.gradle.sharedServices.registerIfAbsent(
            "Realm Analytics",
            AnalyticsService::class.java
        ) { spec: BuildServiceSpec<ProjectConfiguration> ->
            // Identify if project is using sync by inspecting dependencies.
            // We cannot use resolved configurations here as this code is called in
            // afterEvaluate, and resolving it prevents other plugins from modifying
            // them. E.g the KMP plugin will crash if we resolve the configurations
            // in `afterEvaluate`. This means we can only see dependencies directly set,
            // and not their transitive dependencies. This should be fine as we only
            // want to track builds directly using Realm.
            var usesSync: Boolean = withDefaultOnError("Uses Sync", false) {
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
                usesSync
            }

            // Host identifiers collects information through exec/file operations. failOnError
            // option is propagated to the actual tasks to ensure that the don't break build if
            // collection fail.
            val userId: String = target.providers.of(ComputerId::class.java) {
                it.parameters.failOnAnalyticsError.set(failOnAnalyticsError)
            }.safeProvider().get()
            val builderId: String = target.providers.of(BuilderId::class.java) {
                it.parameters.failOnAnalyticsError.set(failOnAnalyticsError)
            }.safeProvider().get()

            val languageVersion =
                withDefaultOnError("Language version", UNKNOWN) { target.kotlinToolingVersion }
            val hostOsType = withDefaultOnError("Host Os Type", UNKNOWN) { HOST_OS_NAME }
            val hostOsVersion = withDefaultOnError(
                "Host Os Version",
                UNKNOWN
            ) { target.providers.systemProperty("os.version").safeProvider().get() }
            val hostCpuArch = withDefaultOnError("Host CPU Arch", UNKNOWN) { HOST_ARCH_NAME }

            spec.parameters.run {
                this.appId.set(anonymizedBundleId)
                this.userId.set(userId)
                this.builderId.set(builderId)
                this.hostOsType.set(hostOsType)
                this.hostOsVersion.set(hostOsVersion)
                this.hostCpuArch.set(hostCpuArch)
                this.usesSync.set(usesSync)
                this.languageVersion.set(languageVersion.toString())
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
            val targetInfo: TargetInfo? = muteErrors {
                gatherTargetInfo(kotlinCompilation)
            }

            // If we have something to submit register it for submission after the compilation has
            // gathered the feature list information
            targetInfo?.let {
                kotlinCompilation.compileTaskProvider.get().doLast {
                    muteErrors {
                        val analyticsService = provider.get()
                        val json = analyticsService.toJson(targetInfo)
                        if (printAnalytics) {
                            analyticsService.print(json)
                        }
                        if (submitAnalytics) {
                            analyticsService.submit(json)
                        }
                    }
                }
            }
        }
        return project.provider {
            options
        }
    }

    /**
     * Wrapper that ignores error if `failOnAnalyticsError=true`.
     */
    private fun <R> muteErrors(block: () -> R): R? {
        return try {
            block()
        } catch (e: Throwable) {
            when {
                failOnAnalyticsError -> { throw e }
                else -> { null }
            }
        }
    }
}

/**
 * Wrapper to safely obtain provider for usage in configuration phases to support configuration
 * cache across Gradle versions.
 */
private fun <T> Provider<T>.safeProvider(): Provider<T> = this.let {
    when {
        gradleVersion < gradle70 -> {
            @Suppress("DEPRECATION")
            it.forUseAtConfigurationTime()
        }
        else -> it
    }
}

@Suppress("ComplexMethod", "NestedBlockDepth")
private fun gatherTargetInfo(kotlinCompilation: KotlinCompilation<*>): TargetInfo? {
    val project = kotlinCompilation.target.project
    return when (kotlinCompilation) {
        // We don't send metrics for common targets but still collect features as the
        // target specific features are a union of common and target specific features
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
                (kotlinExtension as ExtensionAware).extensions.findByName(
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
                    Family.ANDROID -> null // Not supported yet
                    // TODO 1.9-DEPRECATION Revert to exhaustive branch strategy when leaving 1.9 support
                    // Remaining options are removed in Kotlin 2, so cannot reference them but need
                    // an else clause to be exhaustive
                    // Family.WASM,
                    // Family.ZEPHYR,
                    else -> null
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
    else -> unknown(target.family.name)
}

// Helper method to ensure that we align architecture strings for Kotlin native builds
fun nativeArch(target: KonanTarget): String = try {
    when (target.architecture) {
        Architecture.X64 -> io.realm.kotlin.gradle.analytics.Architecture.X64.serializedName
        Architecture.X86 -> io.realm.kotlin.gradle.analytics.Architecture.X86.serializedName
        Architecture.ARM64 -> io.realm.kotlin.gradle.analytics.Architecture.ARM64.serializedName
        Architecture.ARM32 -> io.realm.kotlin.gradle.analytics.Architecture.ARM.serializedName
        else -> unknown(target.architecture.name)
    }
} catch (e: Throwable) {
    unknown(target.architecture.name)
}

// Helper method to ensure that we align architecture strings for Android platforms
fun androidArch(target: String): String = when (target) {
    "armeabi-v7a" -> io.realm.kotlin.gradle.analytics.Architecture.ARM.serializedName
    "arm64-v8a" -> io.realm.kotlin.gradle.analytics.Architecture.ARM64.serializedName
    "x86" -> io.realm.kotlin.gradle.analytics.Architecture.X86.serializedName
    "x86_64" -> io.realm.kotlin.gradle.analytics.Architecture.X64.serializedName
    else -> unknown(target)
}

fun Provider<String>.getBoolean(): Boolean =
    this.safeProvider().getOrElse("false").equals("true", ignoreCase = true)
