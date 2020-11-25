package io.realm

import io.realm.internal.link
import io.realm.interop.RealmInterop
import io.realm.runtimeapi.Link
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.math.max
import kotlin.reflect.KClass

// FIXME API-QUERY
class RealmQuery<T : RealmModel>(
    val nativeRealm: NativePointer,
    val nativeQuery: NativePointer,
    val clazz: KClass<T>,
    // FIXME Can we freely use this for Java - More efficient with factory type
    val factory: () -> T,
) {

    // FIXME API-QUERY
    fun <T : RealmModel> first(): T {
        val link: Link = RealmInterop.realm_query_find_first<T>(nativeQuery)
        val o = factory() as RealmModelInternal
        o.link(nativeRealm, clazz, link)
        return o as T
    }

    // FIXME API-QUERY
    fun all(): List<T> {
        val result: NativePointer = RealmInterop.realm_query_find_all(nativeQuery)
        val count: Long = RealmInterop.realm_results_count(result)
        return LongRange(0, max(0L, count - 1)).map { i ->
            val link: Link = RealmInterop.realm_results_get<T>(result, i)
            val o = factory() as RealmModelInternal
            o.link(nativeRealm, clazz, link)
            o as T
        }
    }
}
