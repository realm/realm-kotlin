plugins {
    kotlin("multiplatform")
    id("io.realm.kotlin")
}

kotlin {
    jvm()

    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> {
            val hostArch = System.getProperty("os.arch")
            when (hostArch) {
                "aarch64" -> macosArm64("native")
                "x86_64" -> macosX64("native")
                else -> throw GradleException("Unrecognized architecture: $hostArch")
            }
        }
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.realm.kotlin:library-base:1.2.0-SNAPSHOT")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
