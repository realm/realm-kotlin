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

package io.realm.kotlin.internal

import kotlinx.coroutines.flow.Flow

/**
 * A __flowable__ is an internal entity that supports listening to changes on the type [T] as a
 * [Flow].
 *
 * This comes in two variants: One variant for flows on things like Objects, Results and Lists that
 * allow you to pass in keypaths to restrict notifications to certain properties and one variant
 * for flows on entities that doesn't support this, e.g. flows on query aggregates or the Realm as
 * whole.
 */

internal interface KeyPathFlowable<T> {
    fun asFlow(keyPaths: List<String>?): Flow<T>
}
