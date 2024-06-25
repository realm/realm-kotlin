@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.mongodb.util

import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.interop.PropertyInfo
import io.realm.kotlin.internal.interop.PropertyType
import io.realm.kotlin.internal.realmObjectCompanionOrNull
import io.realm.kotlin.internal.schema.RealmClassImpl
import io.realm.kotlin.schema.RealmClassKind
import io.realm.kotlin.types.BaseRealmObject
//import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.reflect.KClass

// TODO REname methods and classes
class SchemaProcessor private constructor(
    val classes: Set<KClass<out BaseRealmObject>>,
    val databaseName: String,
    val extraProperties: Map<String, PrimitivePropertyType.Type>,
) {
    companion object {
        private val json = Json {
//            classDiscriminatorMode = ClassDiscriminatorMode.NONE
            classDiscriminator = ""
            prettyPrint = true
            encodeDefaults = true
        }

        fun process(
            databaseName: String,
            classes: Set<KClass<out BaseRealmObject>>,
            extraProperties: Map<String, PrimitivePropertyType.Type> = emptyMap(),
        ): Pair<Map<String, JsonElement>, Map<String, JsonElement>> {
            val processor = SchemaProcessor(classes, databaseName, extraProperties)

            val jsonSchemas: Map<String, JsonElement> = processor.processedSchemas
                .entries
                .filterNot { (_, schema) -> schema.kind == RealmClassKind.EMBEDDED }
                .associate { (name, schema) ->
                    // add metadata
                    name to json.encodeToJsonElement(SchemaRequest(databaseName, schema))
                }

            val jsonSchemasWithRelationship = processor.processedSchemas
                .entries
                .filterNot { (_, schema) -> schema.kind == RealmClassKind.EMBEDDED }
                .associate { (name, schema) ->
                    // add metadata
                    name to json.encodeToJsonElement(SchemaRequest(databaseName, schema, processor.processedRelationships[name]!!))
                }

            return jsonSchemas to jsonSchemasWithRelationship
        }
    }

    val processedSchemas: MutableMap<String, Schema> = mutableMapOf()
    val processedRelationships: MutableMap<String, Map<String, SchemaRelationship>> = mutableMapOf()

    val realmSchemas: Map<String, RealmClassImpl> = classes.associate { clazz ->
        val companion = clazz.realmObjectCompanionOrNull()!!
        val realmSchema = companion.io_realm_kotlin_schema()
        realmSchema.cinteropClass.name to realmSchema
    }

    init {
        // TODO CHECK embedded CYCLES
        generateSchemas()
        generateRelationships()
    }

    private fun generateRelationships() {
        processedSchemas.values.forEach { schema ->
            processedRelationships[schema.title] = analyze(schema.properties).associateBy { it.sourceKey }
        }
    }

    private fun analyze(
        properties: Map<String, SchemaPropertyType>,
        path: String = "",
    ): List<SchemaRelationship> {
        return properties.entries
            .filterNot { (_, value) ->
                value is PrimitivePropertyType
            }
            .flatMap { (key, value: SchemaPropertyType) ->
                value.toSchemaRelationships(key, path)
            }
    }

    private fun SchemaPropertyType.toSchemaRelationships(
        key: String,
        path: String = "",
    ): List<SchemaRelationship> {
        return when (this) {
            is ObjectReferenceType -> toSchemaRelationships(path)
            is CollectionPropertyType -> items.toSchemaRelationships("$path$key.[]")
            is MapPropertyType -> additionalProperties.toSchemaRelationships("$path$key.[]")
            is Schema -> analyze(properties, "$path${key}.")
            else -> emptyList()
        }
    }

    private fun ObjectReferenceType.toSchemaRelationships(path: String = "") =
        listOf(
            SchemaRelationship(
                database = databaseName,
                target = target,
                sourceKey = "${path}${sourceKey}",
                foreignKey = targetKey,
                isList = isList
            )
        )

    private fun generateSchemas() {
        realmSchemas.forEach { entry ->
            if (entry.key !in processedSchemas)
                entry.value.toSchema()
        }
    }

    private fun RealmClassImpl.toSchema() {
        val name = cinteropClass.name

        val properties: Map<String, SchemaPropertyType> = cinteropProperties
            .filterNot {
                it.isComputed
            }
            .associate { property: PropertyInfo ->
                property.name to property.toSchemaProperty()
            } +
                when (kind) {
                    RealmClassKind.STANDARD -> extraProperties.entries
                        .associate {
                            it.key to PrimitivePropertyType(
                                bsonType = it.value,
                                isRequired = false,
                            )
                        }

                    RealmClassKind.EMBEDDED -> emptyMap()
                    RealmClassKind.ASYMMETRIC -> emptyMap()
                }

        val required: List<String> = properties.entries
            .filter { (_, value) ->
                value.isRequired
            }
            .map { (name, _) -> name }

        processedSchemas[name] = Schema(
            title = name,
            properties = properties,
            required = required,
            kind = kind
        )
    }

    private fun PropertyInfo.toSchemaProperty(): SchemaPropertyType =
        when (collectionType) {
            CollectionType.RLM_COLLECTION_TYPE_NONE -> propertyValueType()
            CollectionType.RLM_COLLECTION_TYPE_LIST -> CollectionPropertyType(
                items = propertyValueType(isCollection = true),
                uniqueItems = false
            )

            CollectionType.RLM_COLLECTION_TYPE_SET -> CollectionPropertyType(
                items = propertyValueType(isCollection = true),
                uniqueItems = true
            )

            CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> MapPropertyType(
                additionalProperties = propertyValueType(isCollection = true)
            )

            else -> throw IllegalStateException("Unsupported $collectionType")
        }

    private fun PropertyInfo.propertyValueType(isCollection: Boolean = false): SchemaPropertyType =
        if (type == PropertyType.RLM_PROPERTY_TYPE_OBJECT)
            realmSchemas[linkTarget]!!
                .let { targetSchema: RealmClassImpl ->
                    when (targetSchema.kind) {
                        RealmClassKind.STANDARD -> ObjectReferenceType(
                            name,
                            targetSchema,
                            isCollection
                        )

                        RealmClassKind.EMBEDDED -> getSchema(targetSchema.name)
                        RealmClassKind.ASYMMETRIC -> TODO()
                    }
                }
        else
            PrimitivePropertyType(
                bsonType = type.toSchemaType(),
                isRequired = !isNullable
            )

    private fun getSchema(name: String): Schema {
        if (name !in processedSchemas)
            realmSchemas[name]!!.toSchema()

        return processedSchemas[name]!!
    }
}
