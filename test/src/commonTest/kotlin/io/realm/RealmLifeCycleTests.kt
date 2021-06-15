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

interface RealmLifeCycleTests {
    @Test
    fun version()

    @Test
    fun version_throwsOnUnmanagedObject()

    @Test
    fun version_throwsIfRealmIsClosed()

    @Test
    fun frozen()

    @Test
    fun frozen_throwsOnUnmanagedObject()

    @Test
    fun frozen_throwsIfRealmIsClosed()

    @Test
    fun closed()
}
