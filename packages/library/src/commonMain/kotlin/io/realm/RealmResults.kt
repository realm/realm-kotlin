package io.realm

import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.reflect.KClass

class RealmResults<T : RealmModel> constructor(private val queryPointer: NativePointer,
                                               private val clazz: KClass<T>,
                                               private val modelFactory: ModelFactory
) : AbstractList<T>() {
    override val size: Int
        get() = TODO() // CInterop.queryGetSize(queryPointer).toInt()

    override fun get(index: Int): T {
        val model = modelFactory.invoke(clazz) as RealmModelInternal
//        val objectPointer = TODO() // CInterop.queryGetObjectAt(queryPointer, clazz.simpleName!!, index)
//        model.isManaged = true
//        model.realmObjectPointer = objectPointer
//        model.tableName = clazz.simpleName
        // call T factory to instantiate an Object of type T using it's pointer 'objectPointer'
        return model as T
    }
}
