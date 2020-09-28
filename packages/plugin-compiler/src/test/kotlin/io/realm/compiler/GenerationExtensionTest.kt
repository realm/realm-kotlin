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
import io.realm.runtimeapi.RealmModelInterface

class GenerationExtensionTest {

    @Test
    fun transform() {
        val plugins: List<Registrar> = listOf(Registrar())

        val resource = File(GenerationExtensionTest::class.java.getResource("/Sample.kt").file)
        val result = KotlinCompilation().apply {
            sources = listOf(SourceFile.fromPath(resource))
            useIR = true
            messageOutputStream = System.out
            compilerPlugins = plugins
            inheritClassPath = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("io.realm.example.Sample")
        val newInstance = kClazz.newInstance()!!

        assertTrue(newInstance is RealmModelInterface)
//        for (memberProperty in newInstance::class.memberProperties) {
//            if (memberProperty.returnType.classifier == String::class.createType().classifier) {
//                val property = memberProperty as KProperty1<Any, String>
//                assertTrue(property.get(newInstance).startsWith("Hello "))
//            }
//        }
    }

}

