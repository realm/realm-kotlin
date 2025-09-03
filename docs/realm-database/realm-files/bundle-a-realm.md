# Bundle a Realm - Kotlin SDK
## Overview
> Version added: 1.9.0

The Realm Kotlin SDK supports bundling realm files with your
application. This enables you to pre-populate a database with seed data
in your application download.

> **TIP:**
> You can also add data to your realm the first time an application opens it
using `InitialDataCallback`
or `InitialSubscriptions`.
>

## Asset Realm Locations by Platform
The Realm Kotlin SDK looks for the asset realm that contains your seed data
based on the platform's conventional locations for bundled assets/resources:

- **Android**: Through `android.content.res.AssetManager.open(assetFilename)`
- **JVM**: `Class<T>.javaClass.classLoader.getResource(assetFilename)`
- **Darwin**: `NSBundle.mainBundle.pathForResource(assetFilenameBase, assetFilenameExtension)`

You must place the asset realm in the appropriate location after you create it.
If the asset realm cannot be located when opening the realm for the first time,
`[Realm.open()`
fails with an `IllegalArgumentException`.

## Bundle a Non-Synced Realm
#### Populate an Asset Realm with Seed Data
1. Create a new temporary project to create and populate an asset realm
with seed data. This project uses the same
Realm object schema as your production
app.
2. Open an existing realm with the data you
wish to seed, or create a new realm. Set a specific
`name`
for your asset realm so you can refer to it by name as the initial
data source for your app.
3. Populate the asset realm with the seed data you want to include
in your production application. You can get the path to the asset
realm file using
Realm.configuration.path`.

```kotlin
// Open a local realm to use as the asset realm
val config = RealmConfiguration.Builder(schema = setOf(Item::class))
    .name("asset.realm")
    .build()
val assetRealm = Realm.open(config)

assetRealm.writeBlocking {
    // Add seed data to the asset realm
    copyToRealm(Item().apply {
        summary = "Write an awesome app"
        isComplete = false
    })
}

// Verify the data in the existing realm
// (this data should also be in the bundled realm we open later)
val originalItems: RealmResults<Item> = assetRealm.query<Item>().find()
for(item in originalItems) {
    Log.v("Item in the assetRealm: ${item.summary}")
}

// Get the path to the realm
Log.v("Realm location: ${config.path}")

assetRealm.close()

```

Now that you have an asset realm, you can move it into your production
application and use it there.

> **TIP:**
> Realm files that use the same file format are compatible across SDKs.
If you need to programmatically create asset realms to bundle with
a production application, you can use the Node.js SDK
for easy integration with CI pipelines.
>
> You can find the file format supported by your SDK version in the
changelog for your SDK. This may resemble something like
"File format: Generates Realms with file format v23."
>

#### Bundle and Open the Realm File in Your Production Application
1. Save the copy of the asset realm file to your production application.
You must place this asset file in the appropriate location for your
app's platform. For details, refer to
Asset Realm Locations by Platform.
2. Create a `Configuration`
that your production app can use to open the asset realm.
Set the `initialRealmFile` property in this configuration to the
name of your asset realm. You can optionally provide a `sha256checkSum` for the `initialRealmFile`
to verify the integrity of the realm file when opening it.
If you provide a checksum that does not match the computed checksum
of the asset file when you open the seed realm, `Realm.open()`
fails with an `IllegalArgumentException`.
3. With this configuration, you can open the bundled asset realm. It
contains the data that was in the asset realm at the time you
copied it.

```kotlin
// The config should list the bundled asset realm as the initialRealmFile
val bundledRealmConfig = RealmConfiguration.Builder(schema = setOf(Item::class))
    .initialRealmFile("asset.realm")
    .build()

// After moving the bundled realm to the appropriate location for your app's platform,
// open and use the bundled realm as usual.
val bundledRealm = Realm.open(bundledRealmConfig)
val bundledItems: RealmResults<Item> = bundledRealm.query<Item>().find()
for(item in bundledItems) {
    Log.v("Item in the bundledRealm: ${item.summary}")
}
bundledRealm.close()

```
