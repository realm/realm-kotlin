package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.FqNameImportEmbeddedChild
import io.realm.entities.FqNameImportParent
import io.realm.query
import io.realm.test.platform.PlatformUtils
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
