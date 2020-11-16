// Add support for precompiled script plugins: https://docs.gradle.org/current/userguide/custom_plugins.html#sec:precompiled_plugins
plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

gradlePlugin {
    plugins {
        register("realm-publisher") {
            id = "realm-publisher"
            implementationClass = "io.realm.RealmPublishPlugin"
        }
    }
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
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
        classpath("org.jfrog.buildinfo:build-info-extractor-gradle:${Versions.artifactoryPlugin}")
    }
}

// Setup dependencies for the buildscripts consuming the precompiled plugins
// These seem to propagate to all projects including the buildSrc/ directory, which also means
// they are not allowed to set the version. It can only be set from here.
dependencies {
    implementation("org.jfrog.buildinfo:build-info-extractor-gradle:${Versions.artifactoryPlugin}")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:${Versions.ktlintPlugin}")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${Versions.detektPlugin}")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
    implementation("com.android.tools.build:gradle:${Versions.Android.buildTools}") // FIXME: Figure out why this is required here
    implementation("com.android.tools.build:gradle-api:${Versions.Android.buildTools}")
    implementation(kotlin("script-runtime"))
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
