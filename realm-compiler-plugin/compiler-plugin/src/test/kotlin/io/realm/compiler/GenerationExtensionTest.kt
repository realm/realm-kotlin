package io.realm.compiler

import java.io.File
import kotlin.reflect.KProperty1

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class GenerationExtensionTest {

    class Files(val directory: String) {
        val fileMap: Map<String, File>
        init {
            val base = File(this::class.java.getResource("${directory}").file)
            val file = File(this::class.java.getResource("${directory}${File.separator}input").file)
            fileMap = file.walkTopDown().toList()
                    .filter{ !it.isDirectory }
                    .map { it.relativeTo(base).path to it}.toMap()
        }
        fun expectedDir() = listOf("src", "test", "resources", directory, "output").joinToString(separator = File.separator)
        fun outputDir() = listOf("src", "test", "resources", directory, "output").joinToString(separator = File.separator)
    }

    @Test
    fun transform() {
        val plugins: List<Registrar> = listOf(Registrar())

        val inputs = Files("/sample")

        val result = KotlinCompilation().apply {
            sources = inputs.fileMap.values.map { SourceFile.fromPath(it)}
            useIR = true
            messageOutputStream = System.out
            compilerPlugins = plugins
            inheritClassPath = true
            kotlincArguments = listOf(
                    "-Xuse-ir",
                    "-Xdump-directory=${inputs.outputDir()}",
                    "-Xphases-to-dump-after=ValidateIrBeforeLowering"
            )
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("io.realm.example.Sample")
        val newInstance = kClazz.newInstance()!!

        for (memberProperty in newInstance::class.memberProperties) {
            if (memberProperty.returnType.classifier == String::class.createType().classifier) {
                val property = memberProperty as KProperty1<Any, String>
                assertTrue(property.get(newInstance).startsWith("Hello "))
            }
        }

        stripInputPath(File("${inputs.outputDir()}/00_ValidateIrBeforeLowering.ir"), inputs.fileMap)

        assertEquals(
                File("${inputs.expectedDir()}/00_ValidateIrBeforeLowering.ir").readText(),
                File("${inputs.outputDir()}/00_ValidateIrBeforeLowering.ir").readText(),
        )
    }

    private fun stripInputPath(file: File, map: Map<String, File>) {
        file.writeText(
            map.entries.fold(file.readText()) { text, (name, file) ->
                text.replace(file.path, name)
            }
        )
    }

}

