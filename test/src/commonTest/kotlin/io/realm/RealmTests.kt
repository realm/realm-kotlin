package io.realm

import kotlin.test.*

class RealmTests {

    @Test
    fun open_nullDefaultThrows() {
        val originalConfig = Realm.defaultConfiguration
        try {
            Realm.defaultConfiguration = null
            assertFailsWith<IllegalArgumentException> { Realm.open() }
        } finally {
            Realm.defaultConfiguration = originalConfig
        }
    }
}


