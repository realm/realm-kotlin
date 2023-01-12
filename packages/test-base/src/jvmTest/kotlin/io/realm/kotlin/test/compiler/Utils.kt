package io.realm.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.kotlin.compiler.CollectionType
import io.realm.kotlin.test.util.Compiler

fun createFileAndCompile(fileName: String, code: String): KotlinCompilation.Result =
    Compiler.compileFromSource(SourceFile.kotlin(fileName, code))

/**
 * Generates a formatted string containing code related to lists, sets and dictionaries that can be
 * built by the compiler. Use it to generate different types of scenarios for nullability of
 * the element type and the collection field itself.
 */
fun getCode(
    collectionType: CollectionType,
    elementType: String = "*",
    nullableElementType: Boolean = false,
    nullableField: Boolean = false
): String {
    val formattedContentType = when (elementType) {
        "*" -> elementType
        else -> when (nullableElementType) {
            true -> "$elementType?"
            false -> elementType
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
    // 1st parameter indicates the collection type: RealmList, RealmSet, RealmDictionary
    // 2nd parameter indicates the contained type: String, Long, etc. - nullability must be handled here
    // 3rd parameter indicates nullability of the field itself
    var collection: %1${'$'}s<%2${'$'}s>%3${'$'}s = TODO() // There is no need to use an actual default initializer when testing compilation
}
""".trimIndent()
