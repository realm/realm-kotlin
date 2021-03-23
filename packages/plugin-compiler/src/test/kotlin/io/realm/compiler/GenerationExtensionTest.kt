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
import io.realm.interop.ClassFlag
import io.realm.interop.PropertyType
import io.realm.interop.NativePointer
import io.realm.internal.Mediator
import io.realm.internal.RealmModelInternal
import io.realm.RealmObject
import io.realm.internal.RealmObjectCompanion
import org.junit.Test
import java.io.File
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

        private fun expectedDir() = listOf("src", "test", "resources", directory, "expected").joinToString(separator = File.separator)
        fun outputDir() = listOf("src", "test", "resources", directory, "output").joinToString(separator = File.separator)

        fun assertGeneratedIR() {
            stripInputPath(File("${outputDir()}/00_ValidateIrBeforeLowering.ir"), fileMap)
            assertEquals(
                File("${expectedDir()}/00_ValidateIrBeforeLowering.ir").readText(),
                File("${outputDir()}/00_ValidateIrBeforeLowering.ir").readText()
            )
        }
    }

    @Test
    fun `implement RealmModelInternal and generate internal properties`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.newInstance()!!

        assertTrue(sampleModel is RealmObject)
        assertTrue(sampleModel is RealmModelInternal)

        // Accessing getters/setters
        sampleModel.`$realm$IsManaged` = true
        sampleModel.`$realm$ObjectPointer` = LongPointer(0xCAFEBABE)
        sampleModel.`$realm$Pointer` = LongPointer(0XCAFED00D)
        sampleModel.`$realm$TableName` = "Sample"

        assertEquals(true, sampleModel.`$realm$IsManaged`)
        assertEquals(0xCAFEBABE, (sampleModel.`$realm$ObjectPointer` as LongPointer).ptr)
        assertEquals(0XCAFED00D, (sampleModel.`$realm$Pointer` as LongPointer).ptr)
        assertEquals("Sample", sampleModel.`$realm$TableName`)

        inputs.assertGeneratedIR()
    }

    @Test
    fun `synthetic method generated`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.getDeclaredConstructor().newInstance()!!
        val companionObject = sampleModel::class.companionObjectInstance

        assertTrue(companionObject is RealmObjectCompanion)

        val table = companionObject.`$realm$schema`()
        assertEquals("Sample", table.name)
        assertEquals("", table.primaryKey)
        assertEquals(setOf(ClassFlag.RLM_CLASS_NORMAL), table.flags)
        assertEquals(sampleModel::class.declaredMemberProperties.size, table.properties.size)
        val properties = mapOf(
            "stringField" to PropertyType.RLM_PROPERTY_TYPE_STRING,
            "byteField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "charField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "shortField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "intField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "longField" to PropertyType.RLM_PROPERTY_TYPE_INT,
            "booleanField" to PropertyType.RLM_PROPERTY_TYPE_BOOL,
            "floatField" to PropertyType.RLM_PROPERTY_TYPE_FLOAT,
            "doubleField" to PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
            "child" to PropertyType.RLM_PROPERTY_TYPE_OBJECT,
        )
        assertEquals(properties.size, table.properties.size)
        table.properties.map { property ->
            val expectedType = properties[property.name] ?: error("Property not found: ${property.name}")
            assertEquals(expectedType, property.type)
        }

        val fields: List<KProperty1<*, *>> = (sampleModel::class.companionObjectInstance as RealmObjectCompanion).`$realm$fields`
        assertEquals(properties.size, fields.size)

        val newInstance = companionObject.`$realm$newInstance`()
        assertNotNull(newInstance)
        assertEquals(kClazz, newInstance.javaClass)
        inputs.assertGeneratedIR()
    }

    @Test
    fun `modify accessors to call cinterop`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.newInstance()!!
        val nameProperty = sampleModel::class.members.find { it.name == "stringField" }
            ?: fail("Couldn't find property name of class Sample")
        assertTrue(nameProperty is KMutableProperty<*>)
        assertTrue(sampleModel is RealmModelInternal)

        // In un-managed mode return only the backing field
        sampleModel.`$realm$IsManaged` = false
        assertEquals("Realm", nameProperty.call(sampleModel))

        sampleModel.`$realm$IsManaged` = true
        sampleModel.`$realm$ObjectPointer` = LongPointer(0xCAFEBABE) // If we don't specify a pointer the cinerop call will NPE

        // FIXME Bypass actual setter/getter invocation as it requires actual JNI compilation of
        //  cinterop-jvm which is not yet in place.
        //  https://github.com/realm/realm-kotlin/issues/62
        // set a value using the CInterop call
        // nameProperty.setter.call(sampleModel, "Zepp")
        // get value using the CInterop call
        // assertEquals("Hello Zepp", nameProperty.call(sampleModel))

        inputs.assertGeneratedIR()
    }

    @Test
    fun `should generate mediator implementation`() {
        val inputs = Files("/modules")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("modules.input.Entities")
        val entitiesModule = kClazz.newInstance()!!

        assertTrue(entitiesModule is Mediator)

        val companionMapping = entitiesModule.companionMapping
        assertEquals(3, companionMapping.size)

        val schema: List<Any> = entitiesModule.schema()
        assertNotNull(schema)
        assertEquals(3, schema.size)

        val kClassA = result.classLoader.loadClass("modules.input.A")
        assertNotNull(kClassA)
        val a = entitiesModule.newInstance(kClassA.kotlin)
        assertNotNull(a)

        val kClassB = result.classLoader.loadClass("modules.input.B")
        assertNotNull(kClassB)
        assertNotNull(entitiesModule.newInstance(kClassB.kotlin))

        val kClassC = result.classLoader.loadClass("modules.input.C")
        assertNotNull(kClassC)
        val c = entitiesModule.newInstance(kClassC.kotlin)
        assertNotNull(c)

        assertNotEquals(kClassB, kClassC)

        // subset of model included in the schema
        val subsetKclazz = result.classLoader.loadClass("modules.input.Subset")
        val subsetModule = subsetKclazz.newInstance()!!
        assertTrue(subsetModule is Mediator)
        val subsetSchema: List<Any> = subsetModule.schema()
        assertNotNull(subsetSchema)
        assertEquals(2, subsetSchema.size)
        assertEquals(
            listOf(a, c)
                .map { (it::class.companionObjectInstance as RealmObjectCompanion).`$realm$schema`() }
                .toSet(),
            subsetSchema.toSet()
        )
        inputs.assertGeneratedIR()
    }

    private fun compile(inputs: Files, plugins: List<Registrar> = listOf(Registrar())): KotlinCompilation.Result =
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
