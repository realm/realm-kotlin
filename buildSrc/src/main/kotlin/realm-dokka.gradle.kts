//import org.jetbrains.dokka.gradle.DokkaTask

//import org.jetbrains.dokka.gradle.DokkaTask

//plugins {
//    id("org.jetbrains.dokka")
//}

//tasks.withType<DokkaTask>().configureEach {
//afterEvaluate {
//    tasks.dokkaHtml.configure {
//        tasks.dokkaHtml.configure {
////    outputDirectory.set(buildDir.resolve("dokka"))
//            moduleName.set("Realm Kotlin SDK")
//            dokkaSourceSets {
//                val commonMain by getting {
//                    includes.from("overview.md", "io.realm.md")
//                }
//
//                configureEach {
//                    moduleVersion.set(Realm.version)
//                    reportUndocumented.set(true)
//                    skipEmptyPackages.set(true)
//                    perPackageOption {
//                        matchingRegex.set(".*\\.internal\\..*")
//                        suppress.set(true)
//                    }
//                }
//            }
//        }
//    }
//}
//    // custom output directory
//
//    dokkaSourceSets {
//        configureEach {
//            displayName.set("JVM")

//        }
    //        named("customNameMain") { // The same name as in Kotlin Multiplatform plugin, so the sources are fetched automatically
//            includes.from("packages.md", "extra.md")
//            samples.from("samples/basic.kt", "samples/advanced.kt")
//        }
//
//        register("differentName") { // Different name, so source roots must be passed explicitly
//            displayName = "JVM"
//            platform = "jvm"
//            sourceRoots.from(kotlin.sourceSets.getByName("jvmMain").kotlin.srcDirs)
//            sourceRoots.from(kotlin.sourceSets.getByName("commonMain").kotlin.srcDirs)
//        }
//    }
//}

//    tasks.dokkaHtml.configure {
//        outputDirectory.set(buildDir.resolve("dokka"))
//        dokkaSourceSets {
//            configureEach {
//                displayName = "sdf"
//                reportUndocumented.set(true)
//                skipEmptyPackages.set(true)
//                displayName.set("JVM")
//
//            }
//        }
//    }
//allprojects {
//    tasks.withType<DokkaTask>().configureEach {
//    dokkaHtml {
//        dokkaSourceSets {
//            configureEach {
//                reportUndocumented.set(true)
//                skipEmptyPackages.set(true)
//                displayName.set("JVM")
//
//            }
//        }
//            named("main") {
//                moduleDisplayName.set("Dokka Gradle Example")
//                includes.from("Module.md")
//                sourceLink {
//                    localDirectory.set(file("src/main/kotlin"))
//                    remoteUrl.set(URL("https://github.com/Kotlin/kotlin-examples/tree/master/" +
//                            "gradle/dokka/dokka-gradle-example/src/main/kotlin"
//                    ))
//                    remoteLineSuffix.set("#L")
//                }
//            }
//        }
//    }
//}
