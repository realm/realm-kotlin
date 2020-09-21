object Realm {
    const val version = "0.0.1-SNAPSHOT"
    const val group = "io.realm"
    const val plugin = "realm-kotlin-plugin"
    const val pluginId = "$group.$plugin"
    const val compilerPluginId = "realm-compiler-plugin"
    const val compilerPluginIdNative = "realm-compiler-plugin-shaded"
}

object Versions {
    const val shadowJar =  "5.2.0"
    const val kotlin = "1.4.0"
    const val autoService = "1.0-rc6"
}

// Could be actual Dependency objects
object Deps {
    const val autoService = "com.google.auto.service:auto-service:${Versions.autoService}"
    const val autoServiceAnnotation = "com.google.auto.service:auto-service-annotations:${Versions.autoService}"
}
