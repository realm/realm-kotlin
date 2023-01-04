package io.realm.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.kotlin.compiler.CollectionType
import io.realm.kotlin.test.util.Compiler

fun createFileAndCompile(fileName: String, code: String): KotlinCompilation.Result =
    Compiler.compileFromSource(SourceFile.kotlin(fileName, code))

/**
 * Generates a formatted string containing code that can be built by the compiler.
 * Use it to generate different types of scenarios for nullability of contained type and the field
 * itself.
 */
fun getCode(
    collectionType: CollectionType,
    contentType: String,
    nullableContent: Boolean,
    nullableField: Boolean
): String {
    val formattedContentType = when (nullableContent) {
        true -> "${contentType}?"
        false -> contentType
    }
    val formattedFieldNullability = when (nullableField) {
        true -> "?"
        false -> ""
    }
    val formattedDefaultValue = when (collectionType) {
        CollectionType.LIST -> "realmListOf<$formattedContentType>()"
        CollectionType.SET -> "realmSetOf<$formattedContentType>()"
        CollectionType.DICTIONARY -> "realmDictionaryOf<$formattedContentType>()"
        else -> throw IllegalArgumentException("Only collections can be used here")
    }
    // See comments in COLLECTION_CODE for meaning of parameters
    return COLLECTION_CODE.format(
        collectionType.description,
        formattedContentType,
        formattedFieldNullability,
        formattedDefaultValue
    )
}

/**
 * Generates a formatted string containing code that can be built by the compiler to test
 * collections that use a start projection.
 */
fun getCodeForStarProjection(collectionType: CollectionType): String =
    STAR_PROJECTION_CODE.format(collectionType.description)

private val COLLECTION_CODE = """
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId

import java.lang.Exception

class A
class EmbeddedClass : EmbeddedRealmObject
class SampleClass : RealmObject {
    // 1st parameter indicates the collection type: list, set or dictionary
    // 2nd parameter indicates the contained type: string, long, etc. - nullability must be handled here
    // 3rd parameter indicates nullability of the field itself
    // 4th parameter indicates the default value: realmListOf, realmSetOf or realmDictionaryOf
    var collection: %1${'$'}s<%2${'$'}s>%3${'$'}s = %4${'$'}s
}
""".trimIndent()

private val STAR_PROJECTION_CODE = """
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId

import java.lang.Exception

class StarProjectionClass : RealmObject {
    var collection: %s<*> = TODO()
}
""".trimIndent()
