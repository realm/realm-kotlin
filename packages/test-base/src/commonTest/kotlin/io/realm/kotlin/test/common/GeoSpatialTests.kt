package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.Distance
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.GeoBox
import io.realm.kotlin.types.GeoPoint
import io.realm.kotlin.types.GeoSphere
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.GeoPolygon

class Restaurant: RealmObject {
    var location: Location? = null
}

// Custom embedded model class for storing GeoPoints in Realm.
class Location: EmbeddedRealmObject {
    public constructor(latitude: Double, longitude: Double) {
        this.latitude = latitude
        this.longitude = longitude
        coordinates.apply {
            add(longitude)
            add(latitude)
        }
    }
    public constructor(): this(0.0, 0.0) // Empty constructor required by Realm. Should not be used.

    // Name and type required by Realm
    private var coordinates: RealmList<Double> = realmListOf()

    // Name and type by Realm
    private var type: String = "Point"

    @Ignore
    public val latitude: Double

    @Ignore
    public val longitude: Double
}

class GeoSpatialTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private val configuration: RealmConfiguration by lazy {
        RealmConfiguration.Builder(schema = setOf(Restaurant::class, Location::class))
            .directory(tmpDir)
            .build()
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        realm = Realm.open(configuration)

        realm.writeBlocking {

            // Null Island, west cost of Africa
            copyToRealm(Restaurant().apply {
                location = Location(0.0, 0.0)
            })

            // "Normal" values
            copyToRealm(Restaurant().apply {
                location = Location(5.0, 5.0)
            })
            copyToRealm(Restaurant().apply {
                location = Location(-5.0, -5.0)
            })
            copyToRealm(Restaurant().apply {
                location = Location(5.0, -5.0)
            })
            copyToRealm(Restaurant().apply {
                location = Location(-5.0, 5.0)
            })

            // Objects around latitude boundaries
            val latitudes = listOf(-90.1, -90.0, -89.9, 90.1, 90.0, 89.9)
            latitudes.forEach { lat ->
                copyToRealm(Restaurant().apply {
                    location = Location(lat, 0.0)
                })
            }

            // Objects around longitude boundaries
            val longitudes = listOf(-180.1, -180.0, -179.9, 180.1, 180.0, 179.9)
            longitudes.forEach { lng ->
                copyToRealm(Restaurant().apply {
                    location = Location(0.0, lng)
                })
            }

            // Boundaries in "corners"
            val boundaries: List<Pair<Double, Double>> = listOf(
                -89.9 to -179.9,
                -89.9 to 179.9,
                89.9 to -179.9,
                89.9 to 179.9,

                -90.0 to -180.0,
                -90.0 to 180.0,
                90.0 to -180.0,
                90.0 to 180.0,

                -90.1 to -180.1,
                -90.1 to 180.1,
                90.1 to -180.1,
                90.1 to 180.1
            )
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun geoSphere_toString() {
        val sphere = GeoSphere(center = GeoPoint(0.0, 0.0), radius = Distance.fromKilometers(10.0))
        assertEquals(0, realm.query<Restaurant>("location GEOWITHIN $sphere").count().find())
    }

    @Test
    fun geoSphere_within() {
    }

    @Test
    fun geoBox_toString() {
        val box = GeoBox(bottomLeftCorner = GeoPoint(0.0, 0.0), topRightCorner = GeoPoint(0.0, 0.0))
        assertEquals(0, realm.query<Restaurant>("location GEOWITHIN $box").count().find())
    }

    @Test
    fun geoPolygon_toString() {
        val outer = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(5.0, 0.0),
            GeoPoint(5.0, 5.0),
            GeoPoint(0.0, 5.0)
        )
        val inner = listOf(
            GeoPoint(1.0, 1.0),
            GeoPoint(4.0, 1.0),
            GeoPoint(4.0, 4.0),
            GeoPoint(1.0, 4.0)
        )
        val polygon = GeoPolygon(outer, inner)
        assertEquals(0, realm.query<Restaurant>("location GEOWITHIN $polygon").count().find())
    }
}