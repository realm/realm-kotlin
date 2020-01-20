package io.realm.internal


/**
 * Interface mimicking what is being added to model classes by the Compiler plugin
 * This is just here to make it easier to develop against the API.
 * All casts to this will be redirected towards the model class companion instead.
 */
internal interface ProxyHelperMethods<T> {
    var columns: ColumnIndices?
    fun insert(obj: T, realm: BaseRealm): Row
    // ...
}
