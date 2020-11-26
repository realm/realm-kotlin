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
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmCompanion
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import org.junit.Test
import java.io.File
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.companionObjectInstance
import kotlin.test.assertEquals
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

        assertTrue(sampleModel is RealmModel)
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
    fun `synthetic schema method generated`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.newInstance()!!
        val companionObject = sampleModel::class.companionObjectInstance

        assertTrue(companionObject is RealmCompanion)

        val expected = "{\"name\": \"Sample\", \"properties\": [{\"name\": {\"type\": \"string\", \"nullable\": \"true\"}}]}"
        assertEquals(expected, companionObject.`$realm$schema`())

        inputs.assertGeneratedIR()
    }

    @Test
    fun `modify accessors to call cinterop`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.newInstance()!!
        val nameProperty = sampleModel::class.members.find { it.name == "name" }
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
