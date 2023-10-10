package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.projectInto
import io.realm.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmProjectionFactory
import io.realm.kotlin.types.annotations.ProjectedField
import io.realm.kotlin.types.annotations.Projection
import kotlin.test.assertEquals

// Use a compiler plugin to verify that projection is valid and create a helper method
// for doing it
@Projection(QuerySample::class)
data class ClassMappingProjection(
	val stringField: String = ""
) {
	companion object: RealmProjectionFactory<QuerySample, ClassMappingProjection> {
		override fun createProjection(origin: QuerySample): ClassMappingProjection {
			return ClassMappingProjection(origin.stringField)
		}
	}
}

// Advanced projection that is both checked at compile time and allow for renaming and child
// mapping
@Projection(QuerySample::class)
data class PropertyMappingProjection(
	@ProjectedField("stringField")
	val name: String = "",
	@ProjectedField("nullableRealmObject.stringField") // TODO Should we support a path of KMutableProperties
	val linkedField: String = "",
	val nullableRealmObject: QuerySample?, // TODO Should we all forcing this to be non-null? This will require handling null somewhere
	@ProjectedField("nullableRealmObject") // TODO Support multiple references to same origin fielsd
	val nestedProjectionObject: NestedChild,
	val intListField: List<Int>,
	val intSetField: Set<Int>,
	val intDictionaryField: Map<String, Int>
) {
	// TODO Autogenerate companion object implementing this interface
	// TODO Should we make this interface public, so people can create their own logic, but still
	//  benefit from our APIs?
	companion object: RealmProjectionFactory<QuerySample, PropertyMappingProjection> {
		// TODO Autogenerate this method
		override fun createProjection(origin: QuerySample): PropertyMappingProjection {
			// TODO Autogenerate this mapping.
			// TODO A lot of the conversion logic would probably benefit from being moved to inline helper functions
			return PropertyMappingProjection(
				origin.stringField,
				origin.nullableRealmObject?.stringField ?: "",
				origin.nullableRealmObject,
				origin.projectInto(NestedChild::class),
				origin.intListField.toList(),
				origin.intSetField.toSet(),
				origin.intDictionaryField.toMap()
			)
		}
	}
}

@Projection(QuerySample::class)
data class NestedChild(val name: String) {
	companion object: RealmProjectionFactory<QuerySample, NestedChild> {
		override fun createProjection(origin: QuerySample): NestedChild {
			return NestedChild(origin.stringField)
		}
	}
}

// Mapping class used at runtime. Only support very simple mapping and requires a constructor
// with field names matching the ones from the class being mapped.
// TODO Probably better left for a phase 2?
data class SimpleProjectionMapping(
	val stringField: String = ""
)

class ProjectionTests {

	private lateinit var tmpDir: String
	private lateinit var realm: Realm

	@BeforeTest
	fun setup() {
		tmpDir = PlatformUtils.createTempDir()
		val configuration = RealmConfiguration.Builder(schema = setOf(QuerySample::class))
			.directory(tmpDir)
			.build()
		realm = Realm.open(configuration)
		realm.writeBlocking {
			repeat(5) {
				copyToRealm(QuerySample().apply {
					stringField = "string-$it"
					intField = it
					nullableRealmObject = QuerySample().apply {
						stringField = "substring-$it"
						intField = it + 10
					}
				})
			}
		}
	}

	@AfterTest
	fun tearDown() {
		if (this::realm.isInitialized && !realm.isClosed()) {
			realm.close()
		}
		PlatformUtils.deleteTempDir(tmpDir)
	}

	@Test
	fun projectSingleObject() {
		val managedObj = realm.query<QuerySample>().find().first()
		val projectedObj = managedObj.projectInto(PropertyMappingProjection::class)
		assertEquals(managedObj.stringField, projectedObj.name)
		assertEquals(projectedObj.name, "string-0")
	}

	@Test
	fun projectResults() {
		val results: RealmResults<QuerySample> = realm.query<QuerySample>().find()
		val projectedResults: List<PropertyMappingProjection> = results.projectInto(PropertyMappingProjection::class)
		assertEquals(results.size, projectedResults.size)
	}

}