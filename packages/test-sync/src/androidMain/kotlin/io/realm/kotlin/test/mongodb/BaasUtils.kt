package io.realm.kotlin.test.mongodb

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry

actual fun baasTestUrl(): String {
    val arguments: Bundle = InstrumentationRegistry.getArguments()
    // if the test runner provided an argument for the BAAS URL use it
    // Example: adb shell am instrument -w -e baas_url "http"//8.8.8.8:2134" -r io.realm.sync.testapp.test/androidx.test.runner.AndroidJUnitRunner
    return arguments.getString("baas_url") ?: SyncServerConfig.url
}
