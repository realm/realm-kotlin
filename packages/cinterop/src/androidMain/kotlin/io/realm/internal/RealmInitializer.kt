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

package io.realm.internal

import android.content.Context
import androidx.startup.Initializer
import java.io.File

/**
 * An **initializer** to allow Realm to access context properties.
 *
 * It is highly discouraged to do blocking operation as part of the app's startup sequence, but if
 * Realm is used from an [Initializer], that initializer needs to depend on the [RealmInitializer].
 */
class RealmInitializer : Initializer<Context> {

    companion object {
        // Watch out for storing things here. Could conflict with live refresh in Android Studio
        lateinit var filesDir: File
    }

    override fun create(context: Context): Context {
        filesDir = context.filesDir
        return context
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}
