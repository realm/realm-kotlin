# Serialization - Kotlin SDK
The Realm Kotlin SDK supports Kotlin Serialization. You can serialize specific
Realm data types using stable serializers, or user-defined classes with
an experimental full-document serialization API.

## Realm Data Type Serializers
The Realm Kotlin SDK provides serializers for the following data types for
KSerializer:

|Realm Data Type|KSerializer for Type|
| --- | --- |
|`MutableRealmInt`|`MutableRealmIntKSerializer::class`|
|`RealmAny`|`RealmAnyKSerializer::class`|
|`RealmDictionary`|`RealmDictionaryKSerializer::class`|
|`RealmInstant`|`RealmInstantKSerializer::class`|
|`RealmList`|`RealmListKSerializer::class`|
|`RealmSet`|`RealmSetKSerializer::class`|
|`RealmUUID`|`RealmUUIDKSerializer::class`|

The serializers are located in `io.realm.kotlin.serializers`.

For examples of how Realm serializes the different data types, refer to
Serialization Output Examples.

Deserializing Realm data types generates unmanaged data instances.

### Register a Serializer for a Property
You can register a serializer for a specific property. Use the `@Serializable`
annotation to bind to a specific Realm data type serializer.

```kotlin
class Frog : RealmObject {
    var name: String = ""
    @Serializable(RealmListKSerializer::class)
    var favoritePonds: RealmList<String> = realmListOf()
}

```

### Register a Serializer for All Occurrences in a File
You can register a serializer for all occurrences of that type within a file
by adding the declaration to the top of the file:

```kotlin
@file:UseSerializers(RealmSetKSerializer::class)
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.serializers.RealmSetKSerializer
import io.realm.kotlin.types.RealmSet
import kotlinx.serialization.UseSerializers

```

Then, any objects that have properties of that type within the file can use
the serializer without individually registering it:

```kotlin
// These objects have RealmSet properties that get serializers
// from declaring `@file:UseSerializers(RealmSetKSerializer::class)`.
// No need to individually declare them on every `RealmSet` property in the file.
class Movie : RealmObject {
    var movieTitle: String = ""
    var actors: RealmSet<String> = realmSetOf()
}

class TVSeries : RealmObject {
    var seriesTitle: String = ""
    var episodeTitles: RealmSet<String> = realmSetOf()
}

```

### Automatically Bind Realm Types to Serializers
To automatically bind all Realm types to their serializers, you can add
a snippet containing all serializers to the top of a file:

```kotlin
@file:UseSerializers(
   MutableRealmIntKSerializer::class,
   RealmAnyKSerializer::class,
   RealmDictionaryKSerializer::class,
   RealmInstantKSerializer::class,
   RealmListKSerializer::class,
   RealmSetKSerializer::class,
   RealmUUIDKSerializer::class
)
```

### Serialization Output Examples
These examples illustrate how the different Realm data types serialize
using a JSON encoder:

|Realm Data Type|Serialization Type and Example|
| --- | --- |
|MutableRealmInt|Serializes using a regular integer value. MutableRealmInt.create(35) serializes to 35|
|RealmAny|Serializes using a map containing a union of all values and its type. RealmAny.create("hello world") serializes to {"type": "STRING", "string": "hello world"} RealmAny.create(20) serializes to {"type": "INT", "int": 20}|
|RealmDictionary|Serializes using a generic list. realmDictionaryOf("hello" to "world") serializes to {"hello": "world"}|
|RealmInstant|Serializes as a BsonDateTime . RealmInstant.now() serializes to {"$date": {"$numberLong": "<millis>"}}|
|RealmList|Serializes using a generic list. realmListOf("hello", world) serializes to ["hello", "world"]|
|RealmSet|Serializes using a generic list. realmSetOf("hello", world) serializes to ["hello", "world"]|
|BsonObjectId or ObjectId|Serializes as a BsonObjectId . ObjectId.create() serializes to {"$oid": <ObjectId bytes as 24-character, big-endian hex string>}|
|RealmUUID|Serializes as a BsonBinary . RealmUUID.random() serializes to { "$binary": {"base64": "<payload>", "subType": "<t>"}}|
|RealmObject|Serializes using the polymorphic setup defined by the user. Do this via the SerializersModule: `val json = Json {`<br>` serializersModule = SerializersModule {`<br>` polymorphic(RealmObject::class) {`<br>` subclass(SerializableSample::class)`<br>` }`<br>` }`<br>`}`|

### Add KSerialization to Your Project
The Realm Kotlin SDK's EJSON serialization support depends on the official
Kotlin Serialization library. You must add
[Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)
to your project. Use the same version used in your Realm Kotlin SDK version.
Refer to the [Version Compatibility Matrix in the realm-kotlin GitHub repository](https://github.com/realm/realm-kotlin#version-compatibility-matrix)
for information about the supported dependencies of each version.

The `@Serializable` annotation in the following examples comes from
the Kotlin Serialization framework.

### Stable Encoder
The stable encoder does not support user-defined classes. You can use these
argument types with the stable encoder:

- Primitives
- BSON
- `MutableRealmInt`
- `RealmUUID`
- `ObjectId`
- `RealmInstant`
- `RealmAny`
- Array
- Collection
- Map

To return a collection or map, you can use `BsonArray` or `BsonDocument`.

### Full-Document Encoder
The full-document encoder enables you to serialize and deserialize user-defined
classes.
The full-document encoder supports contextual serializers.

> **IMPORTANT:**
> The current implementation of full document serialization is experimental.
Calling these APIs when your project uses a different version of Kotlin
Serialization than Realm's dependency causes undefined behavior. Refer to
the [Version Compatibility Matrix in the realm-kotlin GitHub repository](https://github.com/realm/realm-kotlin#version-compatibility-matrix)
for information about the supported dependencies of each version.
>

#### Required Imports
To use this feature, add one or more of the following imports to your
file as relevant:

```kotlin
import kotlinx.serialization.Serializable
import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import kotlinx.serialization.modules.SerializersModule
import io.realm.kotlin.serializers.RealmListKSerializer

```

#### Define a Serializer
When you use serialization in the Realm Kotlin SDK, you can define a
serializer in one of two ways:

- Add the `@Serializable` annotation to a class
- Define a custom KSerializer for your type, and pass it to the relevant API

```kotlin
@Serializable
class Person(
    val firstName: String,
    val lastName: String
)

```

You can set a custom EJSON serializer for your app in the `AppConfiguration`,
as in a case where you want to use a contextual serializer:

```kotlin
@Serializable
class Frogger(
    val name: String,
    @Contextual
    val date: LocalDateTime
)

AppConfiguration.Builder(FLEXIBLE_APP_ID)
    .ejson(
        EJson(
            serializersModule = SerializersModule {
                contextual(DateAsIntsSerializer)
            }
        )
    )
    .build()

```

#### Experimental Opt-In
Because the full-document serialization API is experimental, you must add
the relevant `@OptIn` annotations for the APIs you use.

```kotlin
@OptIn(ExperimentalRealmSerializerApi::class)

```

## Other Serialization Libraries
Serialization methods used by libraries that depend on reflection, such as
[GSON](https://github.com/google/gson) do not work with the SDK
by default.

This is because the SDK compiler plugin injects a hidden field
into object models, prefixed with `io_realm_kotlin_`. The SDK uses
this hidden field to manage internal object state. Any library that
relies on fields instead of getters and setters needs to ignore this
hidden field.

To use the SDK with external libraries such as GSON, exclude the hidden
fields from serialization using a prefix match:

```kotlin
var gson: Gson = GsonBuilder()
    .setExclusionStrategies(object: ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes?): Boolean =
            f?.name?.startsWith("io_realm_kotlin_") ?: false
        override fun shouldSkipClass(clazz: Class<*>?): Boolean =
            false
    })
    .create()
```
