package io.realm.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.runtimeapi.*
import org.junit.Test
import java.io.File
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
                    .filter{ !it.isDirectory }
                    .map { it.relativeTo(base).path to it}.toMap()
        }
        private fun expectedDir() = listOf("src", "test", "resources", directory, "expected").joinToString(separator = File.separator)
        fun outputDir() = listOf("src", "test", "resources", directory, "output").joinToString(separator = File.separator)

        fun assertOutput() {
            stripInputPath(File("${outputDir()}/00_ValidateIrBeforeLowering.ir"), fileMap)
            assertEquals(
                    File("${expectedDir()}/00_ValidateIrBeforeLowering.ir").readText(),
                    File("${outputDir()}/00_ValidateIrBeforeLowering.ir").readText()
            )
        }
    }

    @Test
    fun transform() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val newInstance = kClazz.newInstance()!!
        val nameProperty = newInstance::class.members.find { it.name == "name" }
                ?: fail("Couldn't find property name of class Sample")

        assertTrue(newInstance is RealmModel)
        assertTrue(newInstance is RealmModelInternal)

        // Inject Mock NativeWrapper implementation
        NativeWrapper.instance = MockNativeWrapper

        // Accessing getters/setters
        newInstance.`$realm$IsManaged` = true
        newInstance.`$realm$ObjectPointer` = LongPointer(0xCAFEBABE)
        newInstance.`$realm$Pointer` = LongPointer(0XCAFED00D)
        newInstance.`$realm$TableName` = "Sample"

        assertEquals("Managed name value", nameProperty.call(newInstance))
        assertEquals(true, newInstance.`$realm$IsManaged`)
        assertEquals(0xCAFEBABE, (newInstance.`$realm$ObjectPointer` as LongPointer).ptr)
        assertEquals(0XCAFED00D, (newInstance.`$realm$Pointer` as LongPointer).ptr)
        assertEquals("Sample", newInstance.`$realm$TableName`)

        // In un-managed mode return only the backing field
        newInstance.`$realm$IsManaged` = false
        assertEquals("Realm", nameProperty.call(newInstance))

        val companionObject = newInstance::class.companionObjectInstance
        assertTrue(companionObject is RealmCompanion)

        // Check synthetic schema method has been added.
        val expected = "{\"name\": \"Sample\", \"properties\": [{\"name\": {\"type\": \"string\", \"nullable\": \"false\"}}]}"
        assertEquals(expected, companionObject.`$realm$schema`())

        inputs.assertOutput()

    }

    private fun compile(inputs: Files, plugins: List<Registrar> = listOf(Registrar())): KotlinCompilation.Result =
        KotlinCompilation().apply {
            sources = inputs.fileMap.values.map { SourceFile.fromPath(it)}
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

    class LongPointer(val ptr : Long): NativePointer
    object MockNativeWrapper : NativeWrapper {
        override fun openRealm(path: String, schema: String): NativePointer {
            TODO("Not yet implemented")
        }

        override fun realmresultsQuery(pointer: NativePointer, objectType: String, query: String): NativePointer {
            TODO("Not yet implemented")
        }

        override fun addObject(pointer: NativePointer, objectType: String): NativePointer {
            TODO("Not yet implemented")
        }

        override fun beginTransaction(pointer: NativePointer) {
            TODO("Not yet implemented")
        }

        override fun commitTransaction(pointer: NativePointer) {
            TODO("Not yet implemented")
        }

        override fun cancelTransaction(pointer: NativePointer) {
            TODO("Not yet implemented")
        }

        override fun objectGetString(pointer: NativePointer, propertyName: String): String? {
            return "Managed $propertyName value"
        }

        override fun objectSetString(pointer: NativePointer, propertyName: String, value: String?) {
            TODO("Not yet implemented")
        }

        override fun objectGetInt64(pointer: NativePointer, propertyName: String): Long? {
            TODO("Not yet implemented")
        }

        override fun objectSetInt64(pointer: NativePointer, propertyName: String, value: Long) {
            TODO("Not yet implemented")
        }

        override fun queryGetSize(queryPointer: NativePointer): Long {
            TODO("Not yet implemented")
        }

        override fun queryGetObjectAt(queryPointer: NativePointer, objectType: String, index: Int): NativePointer {
            TODO("Not yet implemented")
        }

    }
}

