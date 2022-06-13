/*
 * Copyright 2022 Realm Inc.
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

package io.realm.kotlin.demo.javacompatibility

import android.app.Application
import io.realm.Realm
import io.realm.kotlin.demo.javacompatibility.data.java.JavaRepository
import io.realm.kotlin.demo.javacompatibility.data.kotlin.KotlinRepository

const val TAG: String = "JavaCompatibilityApp"

class MainApplication : Application() {

    lateinit var java: JavaRepository
    lateinit var kotlin: KotlinRepository

    override fun onCreate() {
        super.onCreate()
        java = JavaRepository(this)
        kotlin = KotlinRepository()
    }
}
