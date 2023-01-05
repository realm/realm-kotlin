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
    contentType: String = "",
    nullableContent: Boolean = false,
    nullableField: Boolean = false,
    userStarProjection: Boolean = false
): String {
    val formattedContentType = when (userStarProjection) {
        true -> "*"
        false -> when (nullableContent) {
            true -> "$contentType?"
            false -> contentType
        }
    }
    val formattedFieldNullability = when (nullableField) {
        true -> "?"
        false -> ""
    }
    // See comments in COLLECTION_CODE for meaning of parameters
    return COLLECTION_CODE.format(
        collectionType.description,
        formattedContentType,
        formattedFieldNullability
    )
}

private val COLLECTION_CODE = """
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
    var collection: %1${'$'}s<%2${'$'}s>%3${'$'}s = TODO() // There is no need to use an actual default initializer when testing compilation
}
""".trimIndent()
