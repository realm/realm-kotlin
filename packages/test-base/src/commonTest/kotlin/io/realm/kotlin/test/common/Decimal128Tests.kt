package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.query
import io.realm.kotlin.test.platform.PlatformUtils
import kotlinx.coroutines.runBlocking
import org.mongodb.kbson.Decimal128
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Decimal128Tests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun roundtripSpecialValues() = runBlocking {
        setOf(
            Decimal128.POSITIVE_INFINITY,
            Decimal128.NEGATIVE_INFINITY,
            Decimal128.NaN,
            Decimal128.NEGATIVE_NaN,
            Decimal128.POSITIVE_ZERO,
            Decimal128.NEGATIVE_ZERO,
        ).forEach { decimal128 ->
            realm.write {
                delete(query<Sample>())
                copyToRealm(Sample().apply { decimal128Field = decimal128 })
            }
            realm.query<Sample>("decimal128Field = $0", decimal128).find().single().run {
                assertEquals(decimal128, decimal128Field)
            }
        }
    }
}
