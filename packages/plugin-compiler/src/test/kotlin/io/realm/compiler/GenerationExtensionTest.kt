package io.realm.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.NativeWrapper
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

        // Inject Mock NativeWrapper implementation
        NativeWrapper.instance = MockNativeWrapper
        sampleModel.`$realm$IsManaged` = true
        sampleModel.`$realm$ObjectPointer` = LongPointer(0xCAFEBABE) // If we don't specify a pointer the cinerop call will NPE

        // set a value using the CInterop call
        nameProperty.setter.call(sampleModel, "Zepp")
        // get value using the CInterop call
        assertEquals("Hello Zepp", nameProperty.call(sampleModel))

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
    object MockNativeWrapper : NativeWrapper {
        // simulate a storage engine for setters accessors modification tests
        private val storage = mutableMapOf<Any, Any?>()

        override fun objectGetString(pointer: NativePointer, propertyName: String): String? {
            return "Hello ${storage["$pointer$propertyName"]}"
        }

        override fun objectSetString(pointer: NativePointer, propertyName: String, value: String?) {
            storage["$pointer$propertyName"] = value
        }

        override fun openRealm(path: String, schema: String): NativePointer {
            error("Should not be invoked")
        }

        override fun realmresultsQuery(pointer: NativePointer, objectType: String, query: String): NativePointer {
            error("Should not be invoked")
        }

        override fun addObject(pointer: NativePointer, objectType: String): NativePointer {
            error("Should not be invoked")
        }

        override fun beginTransaction(pointer: NativePointer) {
            error("Should not be invoked")
        }

        override fun commitTransaction(pointer: NativePointer) {
            error("Should not be invoked")
        }

        override fun cancelTransaction(pointer: NativePointer) {
            error("Should not be invoked")
        }

        override fun objectGetInt64(pointer: NativePointer, propertyName: String): Long? {
            error("Should not be invoked")
        }

        override fun objectSetInt64(pointer: NativePointer, propertyName: String, value: Long) {
            error("Should not be invoked")
        }

        override fun queryGetSize(queryPointer: NativePointer): Long {
            error("Should not be invoked")
        }

        override fun queryGetObjectAt(queryPointer: NativePointer, objectType: String, index: Int): NativePointer {
            error("Should not be invoked")
        }
    }
}
