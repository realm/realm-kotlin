package io.realm.kotlinx.dataframe

import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.dynamic.DynamicRealm
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.query.RealmResults
import io.realm.kotlinx.dataframe.internal.createDataFrameForCollection
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import java.io.File

/**
 * Reads a Realm file into dataframes.
 *
 * If a [className] is provided, the top-level dataframe returned will contain all objects of that
 * type. If no [className] is provided, the entire Realm file is read into dataframes and the
 * top-level dataframe will contain a list of all classes.
 *
 * @param realmFile path to the Realm file to open.
 * @param encryptionKey optional encryption key. Only provide this if needed to open the file.
 * @param className optional Realm model class name. If no class name is provided, the entire Realm
 * file is loaded into the dataframe.
 *
 * @return a dataframe representing either all objects the given [className] or the entire Realm file.
 */
public fun DataFrame.Companion.readRealm(realmFile: String, encryptionKey: ByteArray? = null, className: String? = null): AnyFrame {
    return readRealm(File(realmFile), encryptionKey, className)
}

/**
 * Reads a Realm file into dataframes.
 *
 * If a [className] is provided, the top-level dataframe returned will contain all objects of that
 * type. If no [className] is provided, the entire Realm file is read into dataframes and the
 * top-level dataframe will contain a list of all classes.
 *
 * @param realmFile path to the Realm file to open.
 * @param encryptionKey optional encryption key. Only provide this if needed to open the file.
 * @param className optional Realm model class name. If no class name is provided, the entire Realm
 * file is loaded into the dataframe.
 *
 * @return a dataframe representing either all objects the given [className] or the entire Realm file.
 */
public fun DataFrame.Companion.readRealm(realmFile: File, encryptionKey: ByteArray? = null, className: String? = null): AnyFrame {
    if (!realmFile.exists()) {
        throw IllegalArgumentException("File does not exists: ${realmFile.absolutePath}")
    }
    // Work-around for making sure that `File.parentFile` returns something
    val resolvedFile = File(realmFile.absolutePath)
    val builder = RealmConfiguration.Builder(schema = setOf())
        .directory(resolvedFile.parentFile.absolutePath)
        .name(resolvedFile.name)
    if (encryptionKey != null) {
        builder.encryptionKey(encryptionKey)
    }
    val config: RealmConfiguration = builder.build()
    val realm: DynamicRealm = DynamicRealm.open(config)
    val schema = realm.schema()
    val df: AnyFrame = if (className != null) {
        // Only read objects of the given class into dataframes
        val classSchema = schema[className] ?: throw IllegalArgumentException("$className does not exist in the given Realm file: ${realmFile.absolutePath}")
        val results: RealmResults<out DynamicRealmObject> = realm.query(className).find()
        createDataFrameForCollection(schema, classSchema, results)
    } else {
        realm.toDataFrame()
    }
    realm.close()
    return df
}

/**
 * Convert a dataframe into a [DynamicRealmObject] and save it in the Realm.
 *
 * Note, that dataframes only support a very limited set of types, and these types must
 * match the schema of the Realm file.
 *
 * TODO This currently doesn't work as we don't expose a `DynamicMutableRealm` (or similar).
 *  Just show the API surface for now.
 */
@Suppress("unused")
public fun DataFrame<*>.copyToRealm(realm: DynamicRealm, className: String, updatePolicy: UpdatePolicy = UpdatePolicy.ERROR) {
    // Do nothing
}
