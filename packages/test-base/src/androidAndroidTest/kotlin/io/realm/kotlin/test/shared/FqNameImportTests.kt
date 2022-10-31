package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.FqNameImportEmbeddedChild
import io.realm.kotlin.entities.FqNameImportParent
import io.realm.kotlin.ext.query
import io.realm.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class FqNameImportTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(FqNameImportParent::class, FqNameImportEmbeddedChild::class))
                .directory(tmpDir)
                .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun import() {
        realm.writeBlocking {
            copyToRealm(FqNameImportParent().apply { child = FqNameImportEmbeddedChild() })
        }

        realm.query<FqNameImportParent>().find().single().run {
            assertNotNull(child)
        }
    }
}
