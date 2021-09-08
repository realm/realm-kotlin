/*
 * Copyright 2021 Realm Inc.
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

package io.realm

import kotlin.test.Test

/**
 * All objects that are tied to a Realm (or implements RealmState) should test for all the
 * standard life cycle operations.
 */
// TODO If we add methods to retrieve instances that implements this interface we could probably
//  just have default implementations of the various test methods here.
interface RealmStateTest {
    @Test
    fun version()

    @Test
    fun version_throwsOnUnmanagedObject()

    @Test
    fun version_throwsIfRealmIsClosed()

    @Test
    fun isFrozen()

    @Test
    fun isFrozen_throwsOnUnmanagedObject()

    @Test
    fun isFrozen_throwsIfRealmIsClosed()

    @Test
    fun isClosed()
}
