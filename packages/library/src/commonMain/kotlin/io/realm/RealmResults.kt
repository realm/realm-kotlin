package io.realm

import io.realm.internal.link
import io.realm.interop.RealmInterop
import io.realm.runtimeapi.Link
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.reflect.KClass

// FIXME API-QUERY
//  - Lazy API makes it harded to debug
//  - Postponing execution to actually accessing the elements also prevents query parser errors to
//    be raised. Maybe we can get an option to prevalidate queries in the C-API?
class RealmResults<T : RealmModel> constructor(
    private val realm: NativePointer,
    private val queryPointer: () -> NativePointer,
    private val clazz: KClass<T>,
    private val modelFactory: ModelFactory
) : AbstractList<T>(), Queryable<T> {

    private val query: NativePointer by lazy { queryPointer() }
    private val result: NativePointer by lazy { RealmInterop.realm_query_find_all(query) }

    override val size: Int
        get() = RealmInterop.realm_results_count(result).toInt()

    override fun get(index: Int): T {
        val link: Link = RealmInterop.realm_results_get<T>(result, index.toLong())
        val model = modelFactory.invoke(clazz) as RealmModelInternal
        model.link(realm, clazz, link)
        return model as T
    }

    override fun query(query: String, vararg args: Any): RealmResults<T> {
        return RealmResults(
            realm,
            { RealmInterop.realm_query_parse(result, clazz.simpleName!!, query, *args) },
            clazz,
            modelFactory,
        )
    }
}
