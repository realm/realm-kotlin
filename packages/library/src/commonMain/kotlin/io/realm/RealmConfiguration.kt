package io.realm

import kotlin.reflect.KClass

typealias ModelFactory = ((KClass<out RealmModel>) -> RealmModel)

// fun <R : RealmModel> getInstance(type: KClass<R>) : R {
//    return null!!
// }

class RealmConfiguration private constructor(
    val path: String?, // Full path if we don't want to use the default location
    val name: String?, // Optional Realm name (default is 'default')
    val modelFactory: ModelFactory // Factory to instantiate proxy object (since reflection is not supported in K/N)
) {
    data class Builder(
        var path: String? = null,
        var name: String? = null,
        var modelFactory: ModelFactory? = null
    ) {
        fun path(path: String) = apply { this.path = path }
        fun name(name: String) = apply { this.name = name }
        fun factory(factory: ModelFactory) = apply { this.modelFactory = factory }
        fun build(): RealmConfiguration {
            if (modelFactory != null) {
                return RealmConfiguration(path, name, modelFactory!!)
            } else {
                error("modelFactory should be specified")
            }
        }
    }
}
