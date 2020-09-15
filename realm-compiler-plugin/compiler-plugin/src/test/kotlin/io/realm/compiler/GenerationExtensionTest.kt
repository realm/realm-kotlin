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

    @Test
    fun transform() {
        val plugins: List<Registrar> = listOf(Registrar())

        val resource = File(GenerationExtensionTest::class.java.getResource("/input/Sample.kt").file)
        val result = KotlinCompilation().apply {
            sources = listOf(SourceFile.fromPath(resource))
            useIR = true
            messageOutputStream = System.out
            compilerPlugins = plugins
            inheritClassPath = true
            kotlincArguments = listOf(
                    "-Xuse-ir",
                    "-Xdump-directory=src/test/resources/output/",
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

        junitx.framework.FileAssert.assertEquals(
                File("src/test/resources/expected/00_ValidateIrBeforeLowering.ir"),
                File("src/test/resources/output/00_ValidateIrBeforeLowering.ir)
    }

}

