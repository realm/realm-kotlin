/*
 * Copyright 2024 Realm Inc.
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
package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.util.SchemaProcessor
import io.realm.kotlin.types.EmbeddedRealmObject
import kotlin.test.Test

class CycleEmbeddedObject1 : EmbeddedRealmObject {
    var name: String = ""
    var o1: CycleEmbeddedObject2 = CycleEmbeddedObject2()
}

class CycleEmbeddedObject2 : EmbeddedRealmObject {
    var name: String = ""
    var o1: CycleEmbeddedObject3 = CycleEmbeddedObject3()
}

class CycleEmbeddedObject3 : EmbeddedRealmObject {
    var name: String = ""
    var o1: CycleEmbeddedObject1 = CycleEmbeddedObject1()
}

class NoCycleEmbeddedObject1 : EmbeddedRealmObject {
    var name: String = ""
    var o1: NoCycleEmbeddedObject2 = NoCycleEmbeddedObject2()
}

class NoCycleEmbeddedObject2 : EmbeddedRealmObject {
    var name: String = ""
}

class SchemaProcessorTests {

    @Test
    fun cyclesThrow() {
        assertFailsWithMessage<IllegalStateException>("Cycles in embedded object schemas are not supported") {
            SchemaProcessor.process(
                "",
                classes = setOf(
                    CycleEmbeddedObject1::class,
                    CycleEmbeddedObject2::class,
                    CycleEmbeddedObject3::class
                )
            )
        }
    }

    @Test
    fun noCyclesDoesntThrow() {
        SchemaProcessor.process(
            "",
            classes = setOf(NoCycleEmbeddedObject1::class, NoCycleEmbeddedObject2::class)
        )
    }
}
