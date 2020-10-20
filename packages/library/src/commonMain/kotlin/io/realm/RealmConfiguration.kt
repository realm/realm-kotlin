package io.realm

import kotlin.reflect.KClass

internal typealias ModelFactory = ((KClass<out RealmModel>) -> RealmModel)

// fun <R : RealmModel> getInstance(type: KClass<R>) : R {
//    return null!!
// }

public class RealmConfiguration private constructor(
    public val path: String?, // Full path if we don't want to use the default location
    public val name: String?, // Optional Realm name (default is 'default')
    public val modelFactory: ModelFactory // Factory to instantiate proxy object (since reflection is not supported in K/N) TODO: Should this be public?
) {
    public data class Builder(
        var path: String? = null,
        var name: String? = null,
        var modelFactory: ModelFactory? = null
    ) {
        public fun path(path: String): RealmConfiguration.Builder = apply { this.path = path }
        public fun name(name: String): RealmConfiguration.Builder = apply { this.name = name }
        public fun factory(factory: ModelFactory): RealmConfiguration.Builder = apply { this.modelFactory = factory } // TODO: Should this be public?
        public fun build(): RealmConfiguration {
            if (modelFactory != null) {
                return RealmConfiguration(path, name, modelFactory!!)
            } else {
                error("modelFactory should be specified")
            }
        }
    }
}
