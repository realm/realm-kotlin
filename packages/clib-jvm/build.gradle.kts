plugins {
    kotlin("jvm")
    `java`

}

repositories {
    mavenLocal()
}
dependencies {
    implementation(project(":runtime-api"))
//    implementation("io.realm.kotlin:library-jvm")
}
sourceSets {
    main {
        java.srcDir("swig")
    }
}
