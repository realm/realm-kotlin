/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.RealmObject
import io.realm.internal.RealmObjectCompanion
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.PropertyType
import org.junit.Test
import java.io.File
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.companionObjectInstance
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class GenerationExtensionTest {

    /**
     * Wrapping conventions around test cases.
     *
     * Convention is that the subfolders of `directory` contains:
     * - `input` - Kotlin input files
     * - `output` - IR dump from compilation
     * - `expected` - Expected IR dump
     *
     * @param directory Directory containing test case files.
     */
    class Files(private val directory: String) {
        val fileMap: Map<String, File>

        init {
            val base = File(this::class.java.getResource("$directory").file)
            val file = File(this::class.java.getResource("${directory}${File.separator}input").file)
            fileMap = file.walkTopDown().toList()
                .filter { !it.isDirectory }
                .map { it.relativeTo(base).path to it }.toMap()
        }

        private fun expectedDir() = listOf(
            "src",
            "test",
            "resources",
            directory,
            "expected"
        ).joinToString(separator = File.separator)

        fun outputDir() = listOf(
            "src",
            "test",
            "resources",
            directory,
            "output"
        ).joinToString(separator = File.separator)

        fun assertGeneratedIR() {
            val outputFile = File("${outputDir()}/main/00_ValidateIrBeforeLowering.ir")
            stripInputPath(outputFile, fileMap)
            assertEquals(
                File("${expectedDir()}/00_ValidateIrBeforeLowering.ir").readText(),
                outputFile.readText()
            )
        }
    }

    @Test
    fun `RealmConfiguration Schema Argument Lowering`() {
        val inputs = Files("/schema")
        val result = compile(inputs)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        inputs.assertGeneratedIR()
    }

    @Test
    @Suppress("invisible_member", "invisible_reference")
    fun `implement RealmObjectInternal and generate internal properties`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.newInstance()!!

        assertTrue(sampleModel is RealmObject)
        assertTrue(sampleModel is io.realm.internal.RealmObjectInternal)

        // Accessing getters/setters
        sampleModel.`$realm$IsManaged` = true
        sampleModel.`$realm$ObjectPointer` = LongPointer(0xCAFEBABE)
        // Cannot initialize a RealmReference without a model, so skipping this from the test
        // sampleModel.`$realm$Owner` = LongPointer(0XCAFED00D)
        sampleModel.`$realm$TableName` = "Sample"

        assertEquals(true, sampleModel.`$realm$IsManaged`)
        assertEquals(0xCAFEBABE, (sampleModel.`$realm$ObjectPointer` as LongPointer).ptr)
        // Cannot initialize a RealmReference without a model, so skipping this from the test
        // assertEquals(0XCAFED00D, (sampleModel.`$realm$Owner` as LongPointer).ptr)
        assertEquals("Sample", sampleModel.`$realm$TableName`)

        inputs.assertGeneratedIR()
    }

    @Test
    fun `multiple constructors in model definition`() {
        val inputs = Files("/multiple_ctor")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        inputs.assertGeneratedIR()
    }

    @Test
    @Suppress("LongMethod")
    fun `synthetic method generated`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.getDeclaredConstructor().newInstance()!!
        val companionObject = sampleModel::class.companionObjectInstance

        assertTrue(companionObject is RealmObjectCompanion)

        val (table, properties) = companionObject.`$realm$schema`()
        val realmFields = companionObject.`$realm$fields`!!

        assertEquals("Sample", table.name)
        assertEquals("id", table.primaryKey)
        // FIXME Technically this should check that the class is neither embedded or anything else
        //  special, but as we don't support it yet there is nothing to check
        // assertEquals(setOf(ClassFlag.RLM_CLASS_NORMAL), table.flags)
        assertEquals(realmFields.count(), properties.size)
        val expectedProperties = mapOf(
            // Primary key
            "id" to PropertyType.RLM_PROPERTY_TYPE_INT,

            // Primitive types
            "stringField" to PropertyType.RLM_PROPERTY_TYPE_STRING,
            "byteField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "charField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "shortField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "intField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "longField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "booleanField" to PropertyType.RLM_PROPERTY_TYPE_BOOL,
            "floatField" to PropertyType.RLM_PROPERTY_TYPE_FLOAT,
            "doubleField" to PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
            "timestampField" to PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP,

            // RealmObject
            "child" to PropertyType.RLM_PROPERTY_TYPE_OBJECT,

            // List types
            "stringListField" to PropertyType.RLM_PROPERTY_TYPE_STRING,
            "byteListField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "charListField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "shortListField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "intListField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "longListField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "booleanListField" to PropertyType.RLM_PROPERTY_TYPE_BOOL,
            "floatListField" to PropertyType.RLM_PROPERTY_TYPE_FLOAT,
            "doubleListField" to PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
            "timestampListField" to PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP,
            "objectListField" to PropertyType.RLM_PROPERTY_TYPE_OBJECT,

            // Nullable list types
            "nullableStringListField" to PropertyType.RLM_PROPERTY_TYPE_STRING,
            "nullableByteListField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "nullableCharListField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "nullableShortListField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "nullableIntListField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "nullableLongListField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "nullableBooleanListField" to PropertyType.RLM_PROPERTY_TYPE_BOOL,
            "nullableFloatListField" to PropertyType.RLM_PROPERTY_TYPE_FLOAT,
            "nullableDoubleListField" to PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
            "nullableTimestampListField" to PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP,
        )
        assertEquals(expectedProperties.size, properties.size)
        properties.map { property ->
            val expectedType =
                expectedProperties[property.name] ?: error("Property not found: ${property.name}")
            assertEquals(expectedType, property.type)
        }

        val fields: List<KMutableProperty1<*, *>>? =
            (sampleModel::class.companionObjectInstance as RealmObjectCompanion).`$realm$fields`
        assertEquals(expectedProperties.size, fields?.size)

        val newInstance = companionObject.`$realm$newInstance`()
        assertNotNull(newInstance)
        assertEquals(kClazz, newInstance.javaClass)
        inputs.assertGeneratedIR()
    }

    @Test
    @Suppress("invisible_member", "invisible_reference")
    fun `modify accessors to call cinterop`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.newInstance()!!
        val nameProperty = sampleModel::class.members.find { it.name == "stringField" }
            ?: fail("Couldn't find property name of class Sample")
        assertTrue(nameProperty is KMutableProperty<*>)
        assertTrue(sampleModel is io.realm.internal.RealmObjectInternal)

        // In un-managed mode return only the backing field
        sampleModel.`$realm$IsManaged` = false
        assertEquals("Realm", nameProperty.call(sampleModel))

        sampleModel.`$realm$IsManaged` = true
        sampleModel.`$realm$ObjectPointer` =
            LongPointer(0xCAFEBABE) // If we don't specify a pointer the cinerop call will NPE

        // FIXME Bypass actual setter/getter invocation as it requires actual JNI compilation of
        //  cinterop-jvm which is not yet in place.
        //  https://github.com/realm/realm-kotlin/issues/62
        // set a value using the CInterop call
        // nameProperty.setter.call(sampleModel, "Zepp")
        // get value using the CInterop call
        // assertEquals("Hello Zepp", nameProperty.call(sampleModel))

        inputs.assertGeneratedIR()
    }

    private fun compile(
        inputs: Files,
        plugins: List<Registrar> = listOf(Registrar())
    ): KotlinCompilation.Result =
        KotlinCompilation().apply {
            sources = inputs.fileMap.values.map { SourceFile.fromPath(it) }
            useIR = true
            messageOutputStream = System.out
            compilerPlugins = plugins
            inheritClassPath = true
            kotlincArguments = listOf(
                "-Xjvm-default=enable",
                "-Xdump-directory=${inputs.outputDir()}",
                "-Xphases-to-dump-after=ValidateIrBeforeLowering"
            )
        }.compile()

    private fun compileFromSource(
        source: SourceFile,
        plugins: List<Registrar> = listOf(Registrar())
    ): KotlinCompilation.Result =
        KotlinCompilation().apply {
            sources = listOf(source)
            useIR = true
            messageOutputStream = System.out
            compilerPlugins = plugins
            inheritClassPath = true
            kotlincArguments = listOf("-Xjvm-default=enable")
        }.compile()

    companion object {
        private fun stripInputPath(file: File, map: Map<String, File>) {
            file.writeText(
                map.entries.fold(file.readText()) { text, (name, file) ->
                    text.replace(file.path, name)
                }
            )
        }
    }

    class LongPointer(val ptr: Long) : NativePointer
}
