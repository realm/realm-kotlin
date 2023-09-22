/*
 * Copyright 2023 Realm Inc.
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
package io.realm.kotlin.compiler.k2.model

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * Fir extension that modifies the Realm object API.
 *
 * This consists of:
 * - A [CompanionAugmenter] that adds a (or updates existing) companion object to
 *   RealmObject-classes and modifies it to implement the [RealmObjectCompanion] interface and its
 *   methods.
 * - A [ModelObjectAugmenter] that adds `toString`, `equals` and `hashCode` methods to
 *   RealmObject-classes.
 *
 * All API modifications should be tagged with the [RealmApiGeneratorKey] to make it recognizable
 * in other compiler plugin phases.
 */
class RealmApiAugmenter : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::CompanionAugmenter
        +::ModelObjectAugmenter
    }
}

