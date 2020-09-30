package io.realm.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.runtimeapi.RealmModelInterface
import org.junit.Test
import java.io.File
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.functions
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
                    "-Xjvm-default=enable",
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

        // Check synthetic method has been added.
        val schemaFunction = newInstance::class.companionObject?.functions?.firstOrNull { it.name == "schema" }
        assertNotNull(schemaFunction)

        // Make sure synthetic method returns the correct schema
        // dumpSchema method calls the synthetic method `Sample.schema()` internally
        val dumpSchemaFunction = newInstance::class.functions.firstOrNull { it.name == "dumpSchema" }
        assertNotNull(dumpSchemaFunction)
        assertEquals("[{\"name\": \"Sample\", \"properties\": [{\"<get-name>\": {\"type\": \"int\", \"nullable\": \"true\"}}]}]", dumpSchemaFunction.call(newInstance))
    }
}

