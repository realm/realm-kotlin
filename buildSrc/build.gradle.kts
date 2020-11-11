// Add support for precompiled script plugins: https://docs.gradle.org/current/userguide/custom_plugins.html#sec:precompiled_plugins
plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

repositories {
    google()
    jcenter()
    gradlePluginPortal()
    maven("https://dl.bintray.com/kotlin/kotlin-dev")
}


// Setup dependencies for building the buildScript.
buildscript {
    repositories {
        jcenter()
        maven("https://dl.bintray.com/kotlin/kotlin-dev")
    }
    dependencies {
        // FIXME Cannot define this as constant outside of buildscript block
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.20-RC")
    }
}

// Setup dependencies for the buildscripts consuming the precompiled plugins
// These seem to propagate to all projects including the buildSrc/ directory, which also means
// they are not allowed to set the version. It can only be set from here.
dependencies {
    implementation("org.jlleitschuh.gradle:ktlint-gradle:9.4.1")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.14.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.20-RC")
    implementation("com.android.tools.build:gradle:4.0.1") // FIXME: Figure out why this is required here
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
