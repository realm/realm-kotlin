package io.realm

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import java.io.File
import kotlin.reflect.KClass


private val REALM_OBJECT_CLASS_NAME = ClassName("io.realm.kotlin.types", "RealmObject")
private val KCLASS_SET_CLASS_NAME = List::class
    .asClassName()
    .parameterizedBy(
        KClass::class
            .asClassName()
            .parameterizedBy(TypeVariableName("out RealmObject"))
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
fun ClassGeneratorSpec.generate(
    directory: File,
): Pair<String, String> {
    val createdClasses = mutableListOf<String>()
    val setName = "${className.decapitalize()}Classes"

    // Create the file name named after className
    val file = FileSpec.builder(
        packageName = packageName,
        fileName = className
    ).apply {
        // Create count-classes inside the file
        repeat(classCount) { classIndex ->
            val className = "${className}$classIndex"
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
                    }
                    .build()
            )
        }

        val kClasses = createdClasses.map {
            "$it::class"
        }.joinToString(", \n", "\n", ",\n")
        // add a property to facilitate accessing these generated classes
        addProperty(
            PropertySpec
                .builder(setName, KCLASS_SET_CLASS_NAME)
                .initializer("listOf($kClasses)")
                .build()
        )
    }.build()

    //file.writeTo(System.out)
    file.writeTo(directory)
    return className to setName
}
