package io.realm.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LinkingObjectsTests {
    @Test
    fun `non parameter defined`() {
        val result = createFileAndCompile(
            "nonParemeter.kt",
            NON_PARAMETER_LINKING_OBJECTS
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "[Realm] Error in linking objects field nonParameterLinkingObject - only direct property references are valid parameters.")
    }

    @Test
    fun `unsupported types`() {
        mapOf(
            "String" to "\"hello world\"",
            "List<String>" to "listOf()",
            "RealmList<String>" to "realmListOf()",
            "Set<Int>" to "setOf()",
            "RealmSet<Int>" to "realmSetOf()",
            "Invalid?" to "null"
        ).forEach { entry ->
            val (type, value) = entry

            val result = createFileAndCompile(
                "unsupportedTypes.kt",
                TARGET_INVALID_TYPE.format(type, value)
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertContains(
                result.messages,
                "[Realm] Error in linking objects field 'reference' - target property 'targetField' does not reference 'Referent'."
            )
        }
    }
}

private val TARGET_INVALID_TYPE =
    """
    import io.realm.kotlin.ext.linkingObjects
    import io.realm.kotlin.ext.realmListOf
    import io.realm.kotlin.ext.realmSetOf
    import io.realm.kotlin.types.RealmObject
    import io.realm.kotlin.types.RealmList
    import io.realm.kotlin.types.RealmSet
    
    class Invalid : RealmObject {
        var stringField: String = ""
    }
    
    class Target : RealmObject {
        var targetField: %s = %s
    }
    
    class Referent : RealmObject {
        var reference = linkingObjects(Target::targetField)
    }
    """.trimIndent()

private val NON_PARAMETER_LINKING_OBJECTS =
    """
    import io.realm.kotlin.ext.linkingObjects
    import io.realm.kotlin.types.RealmObject
    
    var childProperty = Parent::child
    
    class Parent : RealmObject {
        var child: Child? = null
    }
    
    class Child : RealmObject {
        var nonParameterLinkingObject = linkingObjects(childProperty)
    }
    """.trimIndent()
