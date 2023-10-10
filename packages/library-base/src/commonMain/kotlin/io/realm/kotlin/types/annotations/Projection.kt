package io.realm.kotlin.types.annotations

import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
public annotation class Projection(val origin: KClass<out TypedRealmObject>)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
@MustBeDocumented
public annotation class ProjectedField(val origin: String)
