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
import kotlin.test.assertTrue

@ExperimentalUnsignedTypes
class VersionIdTests {

    @Suppress("ReplaceAssertBooleanWithAssertEquality")
    @Test
    fun compareVersions() {
        // FIXME Determine how index places a role when comparing VersionID's
        assertTrue(VersionId(0u,0u) == VersionId(0u,0u))
        assertTrue(VersionId(1u,0u) > VersionId(0u,0u))
        assertTrue(VersionId(1u,0u) >= VersionId(0u,0u))
        assertTrue(VersionId(1u,0u) < VersionId(2u,0u))
        assertTrue(VersionId(1u,0u) <= VersionId(2u,0u))
    }
}