package io.realm

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import java.io.File
import kotlin.reflect.KClass


private val REALM_OBJECT_CLASS_NAME = ClassName("io.realm.kotlin.types", "RealmObject")
private val REALM_LIST_CLASS_NAME = ClassName("io.realm.kotlin.types", "RealmList")
private val KCLASS_SET_CLASS_NAME = List::class
    .asClassName()
    .parameterizedBy(
        KClass::class
            .asClassName()
            .parameterizedBy(TypeVariableName("out RealmObject"))
    )

private val MAP_SETS_CLASS_NAME = Map::class
    .asClassName()
    .parameterizedBy(
        String::class.asClassName(),
        List::class
            .asClassName()
            .parameterizedBy(
                KClass::class
                    .asClassName()
                    .parameterizedBy(TypeVariableName("out RealmObject"))
            )
    )

private val STRINT_REALM_LIST_CLASS_NAME = REALM_LIST_CLASS_NAME
    .parameterizedBy(
        String::class.asClassName()
    )

data class ClassGeneratorSpec(
    val classCount: Int,
    val packageName: String,
    val className: String,
    val stringFieldCount: Int,
)

/**
 * Generates the classes defined by the spec, and returns the set name containing all classes.
 */
private fun generateClasses(
    classCount: Int,
    packageName: String,
    name: String,
    stringFieldCount: Int,
    stringRealmListCount: Int,
    output: File,
): Pair<String, String> {
    val createdClasses = mutableListOf<String>()
    val setName = "${name.decapitalize()}Classes"

    // Create the file name named after className
    val file = FileSpec.builder(
        packageName = packageName,
        fileName = name
    ).apply {
        addImport("io.realm.kotlin.ext","realmListOf")
        // Create count-classes inside the file
        repeat(classCount) { classIndex ->
            val className = "${name}$classIndex"
            createdClasses.add(className)
            addType(
                TypeSpec.classBuilder(className)
                    .addSuperinterface(REALM_OBJECT_CLASS_NAME)
                    .apply {
                        // Add string fields
                        repeat(stringFieldCount) {
                            addProperty(
                                PropertySpec.builder("stringField${it}", String::class)
                                    .initializer("\"\"")
                                    .mutable(true)
                                    .build()
                            )
                        }
                        repeat(stringRealmListCount) {
                            addProperty(
                                PropertySpec.builder("stringRealmListFieldCount${it}", STRINT_REALM_LIST_CLASS_NAME)
                                    .initializer("realmListOf()")
                                    .mutable(true)
                                    .build()
                            )
                        }
                    }
                    .build()
            )
        }

        val classesAsList = createdClasses.map {
            "$it::class"
        }.joinToString(", \n", "listOf(\n", ",\n)")
        // add a property to facilitate accessing these generated classes
        addProperty(
            PropertySpec
                .builder(setName, KCLASS_SET_CLASS_NAME)
                .initializer(classesAsList)
                .build()
        )
    }.build()

    file.writeTo(output)
    return name to setName
}

fun generateSuiteEntryPoint(
    name: String,
    packageName: String,
    generatedSets: Map<String, String>,
    output: File
) {
    val file = FileSpec.builder(
        packageName = packageName,
        fileName = name
    ).apply {
        addImport(REALM_OBJECT_CLASS_NAME.packageName, REALM_OBJECT_CLASS_NAME.simpleName)

        val generatedSetsAsMap = generatedSets.entries.map { (key, path) ->
            """"$key" to $path"""
        }.joinToString(",\n", "mapOf(\n", ",\n)")

        addProperty(
            PropertySpec
                .builder("${name}ClassesMap", MAP_SETS_CLASS_NAME)
                .initializer(generatedSetsAsMap)
                .build()
        )
    }.build()
    file.writeTo(output)
}

class BenchmarkClassSuite {
    companion object {
        operator fun invoke(
            name: String,
            packageName: String,
            output: File,
            block: BenchmarkClassSuiteBuilder.() -> Unit,
        ) {
            val builder = BenchmarkClassSuiteBuilder(
                suiteName = name,
                packageName = packageName,
                output = output,
            )
            builder.block()
        }
    }

    class BenchmarkClassSuiteBuilder(
        val suiteName: String,
        val packageName: String,
        val output: File,
    ) {
        val generatedSets = mutableMapOf<String, String>()

        fun addClassGeneratorSpec(
            classCount: Int,
            className: String,
            stringFieldCount: Int = 0,
            stringRealmListCount: Int = 0,
        ) {
            val (key, value) = generateClasses(
                classCount = classCount,
                packageName = packageName,
                name = className,
                stringFieldCount = stringFieldCount,
                stringRealmListCount = stringRealmListCount,
                output = output,
            )
            generatedSets.put(key, value)
            build()
        }

        fun build() {
            generateSuiteEntryPoint(
                name = suiteName,
                packageName = packageName,
                generatedSets = generatedSets,
                output = output,
            )
        }
    }
}
