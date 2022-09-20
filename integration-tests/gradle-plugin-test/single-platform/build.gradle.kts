plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("io.realm.kotlin")
}

android {
    compileSdk = 32

    defaultConfig {
        minSdk = 21
        targetSdk = 32

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("com.android.support:appcompat-v7:28.0.0")
    implementation("io.realm.kotlin:library-base:1.2.0-SNAPSHOT")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("com.android.support.test:runner:1.0.2")
    androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.2")
}