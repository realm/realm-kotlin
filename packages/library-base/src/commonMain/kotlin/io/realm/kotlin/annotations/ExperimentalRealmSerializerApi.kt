/*
 * Copyright 2023 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.realm.kotlin.annotations

@MustBeDocumented
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS
)
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
/**
 * This annotation marks Realm APIs that use **experimental** serializer APIs under the hood.
 * Calling these APIs when not using the same version of Kotlin Serialization that Realm depends on 
 * will have undefined behavior. See https://github.com/realm/realm-kotlin#version-compatibility-matrix
 * or https://github.com/realm/realm-kotlin/blob/main/CHANGELOG.md to see which version of Kotlin
 * Serialization that is used.
 */
public annotation class ExperimentalRealmSerializerApi
