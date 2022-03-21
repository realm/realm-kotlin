package io.realm.kotlin.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.kotlin.benchmarks.Entity1
import io.realm.realmListOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessorTests {

    private lateinit var config: RealmConfiguration

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var realm: Realm
    private lateinit var unmanagedObject: Entity1
    private lateinit var managedObject: Entity1

    var valueRef: Any? = null

    @Before
    fun before() {
        config = RealmConfiguration.with(schema = setOf(Entity1::class))
        realm = Realm.open(config)
        managedObject = realm.writeBlocking {
            unmanagedObject = Entity1().apply {
                stringField = "Medium long string"
                booleanField = true
                longField = 42L
                doubleField = 1.234
                objectField = Entity1()
                objectListField = realmListOf(Entity1(), Entity1(), Entity1())
            }
            copyToRealm(unmanagedObject)
        }
    }

    @After
    fun after() {
        realm.close()
        Realm.deleteRealm(config)
    }

    @Test
    fun managedReadString() {
        benchmarkRule.measureRepeated {
            valueRef = managedObject.stringField
        }
    }

    @Test
    fun managedReadLong() {
        benchmarkRule.measureRepeated {
            valueRef = managedObject.longField
        }
    }

    @Test
    fun managedReadDouble() {
        benchmarkRule.measureRepeated {
            valueRef = managedObject.doubleField
        }
    }

    @Test
    fun managedReadBoolean() {
        benchmarkRule.measureRepeated {
            valueRef = managedObject.booleanField
        }
    }

    @Test
    fun managedReadObject() {
        benchmarkRule.measureRepeated {
            valueRef = managedObject.objectField
        }
    }

    @Test
    fun managedReadList() {
        benchmarkRule.measureRepeated {
            valueRef = managedObject.objectListField
        }
    }

    @Test
    fun unManagedReadString() {
        benchmarkRule.measureRepeated {
            valueRef = unmanagedObject.stringField
        }
    }

    @Test
    fun unManagedReadLong() {
        benchmarkRule.measureRepeated {
            valueRef = unmanagedObject.longField
        }
    }

    @Test
    fun unManagedReadDouble() {
        benchmarkRule.measureRepeated {
            valueRef = unmanagedObject.doubleField
        }
    }

    @Test
    fun unManagedReadBoolean() {
        benchmarkRule.measureRepeated {
            valueRef = unmanagedObject.booleanField
        }
    }

    @Test
    fun unManagedReadObject() {
        benchmarkRule.measureRepeated {
            valueRef = unmanagedObject.objectField
        }
    }

    @Test
    fun unManagedReadList() {
        benchmarkRule.measureRepeated {
            valueRef = unmanagedObject.objectListField
        }
    }
}
