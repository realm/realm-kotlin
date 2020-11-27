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

// package io.realm.util
//
// import io.realm.runtimeapi.RealmCompanion
// import io.realm.runtimeapi.RealmModel
// import kotlin.reflect.KClass
//
// /**
// * Interim helper to place manual code written around schemas.
// */
// class Module(classes: List<KClass<out RealmModel>>) {
//    val classes: Collection<KClass<out RealmModel>> = HashSet(classes.toSet())
//
//    fun schema(): String {
//        return classes
//                .map { it.companion() }
//                .map { it.schema() }
//                .joinToString(prefix = "[", separator = ",", postfix = "]") { it }
//    }
//
//    private fun <T : Any> KClass<T>.companion(): RealmCompanion {
//        return this.nestedClasses.first { it.isCompanion }.objectInstance as RealmCompanion
//    }
//
// }
