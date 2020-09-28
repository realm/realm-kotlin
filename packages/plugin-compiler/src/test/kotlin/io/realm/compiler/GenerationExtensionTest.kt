package io.realm.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.runtimeapi.RealmModelInterface
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            kotlincArguments = listOf(
                    "-Xdump-directory=./build/ir/",
                    "-Xphases-to-dump-after=ValidateIrBeforeLowering")
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("io.realm.example.Sample")
        val newInstance = kClazz.newInstance()!!

        assertTrue(newInstance is RealmModelInterface)

        // Accessing getters/setters
        newInstance.isManaged = true
        newInstance.realmObjectPointer = 0xCAFEBABE
        newInstance.realmPointer = 0XCAFED00D
        newInstance.tableName = "Sample"

        assertEquals(true, newInstance.isManaged)
        assertEquals(0xCAFEBABE, newInstance.realmObjectPointer)
        assertEquals(0XCAFED00D, newInstance.realmPointer)
        assertEquals("Sample", newInstance.tableName)
//        for (memberProperty in newInstance::class.memberProperties) {
//            if (memberProperty.returnType.classifier == String::class.createType().classifier) {
//                val property = memberProperty as KProperty1<Any, String>
//                assertTrue(property.get(newInstance).startsWith("Hello "))
//            }
//        }
    }

}

