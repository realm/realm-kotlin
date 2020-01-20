package io.realm.annotations

/**
 * Any method annotated by this method will have all expressions of the form
 * `x as RealmProxy` replaced by `x`. This is required because all methods/properties defined by
 * the RealmProxy interface will be inlined by another step.
 *
 * TODO: We probably don't need this annotation at all
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class ModifyWithRealmCompilerPlugin