object Realm {
    const val version = "0.0.1-SNAPSHOT"
    const val group = "io.realm.kotlin"
    const val plugin = "realm-kotlin"
    const val pluginId = "$plugin"
    // Modules has to match ${project.group}:${project.name} to make composite build work
    const val compilerPluginId = "plugin-compiler"
    const val compilerPluginIdNative = "plugin-compiler-shaded"
}

object Versions {
    const val androidMinSdk = 16
    const val androidTargetSDK = 29
    const val androidCompileSdkVersion = 29
    const val androidBuildToolsVersion = "29.0.2"
    const val androidBuildTools = "4.0.1"
    const val autoService = "1.0-rc6"
    const val detektPlugin = "1.14.1"
    const val jvmTarget = "1.8"
    const val kotlin = "1.4.20-M1-63"
    const val kotlinCompileTesting = "1.2.6" // https://github.com/tschuchortdev/kotlin-compile-testing
    const val ktlintPlugin = "9.4.1" // https://github.com/jlleitschuh/ktlint-gradle
    const val ktlintVersion = "0.39.0" // https://github.com/pinterest/ktlint
    const val shadowJar =  "5.2.0"
}

// Could be actual Dependency objects
object Deps {
    const val autoService = "com.google.auto.service:auto-service:${Versions.autoService}"
    const val autoServiceAnnotation = "com.google.auto.service:auto-service-annotations:${Versions.autoService}"
}
