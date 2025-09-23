# Geospatial Data - Kotlin SDK
> Version added: 1.11.0

Geospatial data, or "geodata", specifies points and geometric objects on the Earth's
surface.

Kotlin SDK version 1.11.0 and later adds experimental geospatial APIs
that support querying with geospatial data. These queries can check whether a given
point is contained within a shape. For example, you can find all coffee shops within
15 km of a specified point.

## Geospatial Data Types
The Kotlin SDK supports geospatial queries using the following data types:

- GeoPoint
- GeoCircle
- GeoBox
- GeoPolygon

The SDK provides these geospatial data types to simplify querying geospatial data. You *cannot* persist these data types directly.

For information on how to persist geospatial data, refer to the
Persist Geospatial Data section on this page.

### GeoPoint
A `GeoPoint` defines a specific
location on the Earth's surface. All of the geospatial data types use `GeoPoints`
to define their location.

### GeoCircle
A `GeoCircle` defines a circle on
the Earth's surface. You define a `GeoCircle` by providing a `GeoPoint`
for the center of the circle and a `Distance`
object to specify the radius of the circle.

> **NOTE:**
> You can define the radius in kilometers, miles, degrees, or radians.
>

The following example creates 2 circles:

```kotlin
val circle1 = GeoCircle.create(
    center = GeoPoint.create(47.8, -122.6),
    radius = Distance.fromKilometers(44.4)
)
val circle2 = GeoCircle.create(
    center = GeoPoint.create(47.3, -121.9),
    radius = Distance.fromDegrees(0.25)
)

```

![Two GeoCircles](/images/geocircles.png)

### GeoBox
A `GeoBox` defines a rectangle on
the Earth's surface. You define the rectangle by specifying the bottom left
(southwest) corner and the top right (northeast) corner.

The following example creates 2 boxes:

```kotlin
val box1 = GeoBox.create(
    bottomLeft = GeoPoint.create(47.3, -122.7),
    topRight = GeoPoint.create(48.1, -122.1)
)
val box2 = GeoBox.create(
    bottomLeft = GeoPoint.create(47.5, -122.4),
    topRight = GeoPoint.create(47.9, -121.8)
)

```

![2 GeoBoxes](/images/geoboxes.png)

### GeoPolygon
A `GeoPolygon` defines a polygon
on the Earth's surface.

Because a polygon is a closed shape, you must provide a minimum of 4 points:
3 points to define the polygon's shape and a fourth to close the shape.

> **IMPORTANT:**
> The fourth point in a polygon *must* be the same as the first point.
>

You can also exclude areas within a polygon by defining one or more "holes".
A hole is another polygon whose bounds fit completely within the outer polygon.
Holes can also be nested within each other. A location is considered inside the
polygon if it is included in an odd number of rings.

The following example creates 3 polygons:

- A basic polygon with 5 points
- The same polygon with a single hole
- The same polygon with two holes

```kotlin
// Create a basic polygon
val basicPolygon = GeoPolygon.create(
    listOf(
        GeoPoint.create(48.0, -122.8),
        GeoPoint.create(48.2, -121.8),
        GeoPoint.create(47.6, -121.6),
        GeoPoint.create(47.0, -122.0),
        GeoPoint.create(47.2, -122.6),
        GeoPoint.create(48.0, -122.8)
    )
)

// Create a polygon with a single hole
val outerRing = listOf(
        GeoPoint.create(48.0, -122.8),
        GeoPoint.create(48.2, -121.8),
        GeoPoint.create(47.6, -121.6),
        GeoPoint.create(47.0, -122.0),
        GeoPoint.create(47.2, -122.6),
        GeoPoint.create(48.0, -122.8)
)

val hole1 = listOf(
        GeoPoint.create(47.8, -122.6),
        GeoPoint.create(47.7, -122.2),
        GeoPoint.create(47.4, -122.6),
        GeoPoint.create(47.6, -122.5),
        GeoPoint.create(47.8, -122.6)
)

val polygonWithOneHole = GeoPolygon.create(outerRing, hole1)

// Add a second hole to the polygon
val hole2 = listOf(
    GeoPoint.create(47.55, -122.05),
    GeoPoint.create(47.5, -121.9),
    GeoPoint.create(47.3, -122.1),
    GeoPoint.create(47.55, -122.05)
)

val polygonWithTwoHoles = GeoPolygon.create(outerRing, hole1, hole2)

```

![3 GeoPolygons](/images/geopolygons.png)

## Persist Geospatial Data
> **IMPORTANT:**
> Currently, you can only persist geospatial data. Geospatial data types *cannot* be persisted directly. For example, you
can't declare a property that is of type `GeoBox`.
>
> These types can only be used as arguments for geospatial queries.
>

If you want to persist geospatial data, it must conform to the
[GeoJSON spec](https://datatracker.ietf.org/doc/html/rfc7946).

To do this with the Kotlin SDK, you can create a GeoJSON-compatible
embedded class that you can then use in your data model.

### Create a GeoJSON-Compatible Class
To create a class that conforms to the GeoJSON spec, you:

1. Create an embedded realm object
(a class that inherits from
`EmbeddedRealmObject`).
2. At a minimum, add the two fields required by the GeoJSON spec: A field of type `String` property that maps to a `type` property
with the value of `"Point"`: `var type: String = "Point"`A field of type `RealmList<Double>` that maps to a `coordinates`
property in the realm schema containing a latitude/longitude
pair: `var coordinates: RealmList<Double> = realmListOf()`

The following example shows an embedded class named `CustomGeoPoint` that is used
to persist geospatial data:

```kotlin
class CustomGeoPoint : EmbeddedRealmObject {
    constructor(latitude: Double, longitude: Double) {
        coordinates.apply {
            add(longitude)
            add(latitude)
        }
    }
    // Empty constructor required by Realm
    constructor() : this(0.0, 0.0)

    // Name and type required by Realm
    var coordinates: RealmList<Double> = realmListOf()

    // Name, type, and value required by Realm
    private var type: String = "Point"

    @Ignore
    var latitude: Double
        get() = coordinates[1]
        set(value) {
            coordinates[1] = value
        }

    @Ignore
    var longitude: Double
        get() = coordinates[0]
        set(value) {
            coordinates[0] = value
        }
}

```

### Use the Embedded Class
Use the `customGeoPoint` class in your realm model, as shown in the
following example:

```kotlin
class Company : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var location: CustomGeoPoint? = null
}

```

You can then add instances of your class to the realm:

```kotlin
realm.writeBlocking {
    copyToRealm(
        Company().apply {
            location = CustomGeoPoint(47.68, -122.35)
        }
    )
    copyToRealm(
        Company().apply {
            location = CustomGeoPoint(47.9, -121.85)
        }
    )
}

```

The following image shows the results of creating these two `Company` objects:

![2 GeoPoints](/images/geopoints.png)

## Query Geospatial Data
To query against geospatial data, you can use the `GEOWITHIN` operator with
RQL. This method takes the `coordinates`
property of an embedded object and checks if that point is contained within
the geospatial shape for that object.

The format for querying geospatial data is the same, regardless of the shape of
the geodata region.

> **IMPORTANT:**
> You cannot use parameterized queries with geospatial data.
>

The following examples show querying against various shapes to return a list of
companies within the shape.

### GeoCircle
```kotlin
val companiesInLargeCircle =
    realm.query<Company>("location GEOWITHIN $circle1").find()
println("Companies in large circle: ${companiesInLargeCircle.size}")

val companiesInSmallCircle =
    realm.query<Company>("location GEOWITHIN $circle2").find()
println("Companies in small circle: ${companiesInSmallCircle.size}")

```

```
Companies in large circle: 1
Companies in small circle: 0
```

![Querying a GeoCircle example](/images/geocircles-query.png)

### GeoBox
```kotlin
val companiesInLargeBox =
    realm.query<Company>("location GEOWITHIN $box1").find()
println("Companies in large box: ${companiesInLargeBox.size}")

val companiesInSmallBox =
    realm.query<Company>("location GEOWITHIN $box2").find()
println("Companies in small box: ${companiesInSmallBox.size}")

```

```
Companies in large box: 1
Companies in small box: 2
```

![Querying a GeoBox example](/images/geoboxes-query.png)

### GeoPolygon
```kotlin
val companiesInBasicPolygon =
    realm.query<Company>("location GEOWITHIN $basicPolygon").find()
println("Companies in basic polygon: ${companiesInBasicPolygon.size}")

val companiesInPolygonWithHoles =
    realm.query<Company>("location GEOWITHIN $polygonWithTwoHoles").find()
println("Companies in polygon with holes: ${companiesInPolygonWithHoles.size}")

```

```
Companies in basic polygon: 2
Companies in polygon with holes: 1
```

![Querying a GeoPolygon example](/images/geopolygons-query.png)
