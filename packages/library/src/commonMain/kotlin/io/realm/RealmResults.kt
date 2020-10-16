package io.realm

import io.realm.runtimeapi.NativePointer
import kotlin.reflect.KClass

class RealmResults<T : RealmModel> constructor(private val queryPointer: NativePointer,
                                                                           private val clazz: KClass<T>,
                                                                           private val modelFactory: ModelFactory
) : AbstractList<T>() {
    override val size: Int
        get() = TODO() // CInterop.queryGetSize(queryPointer).toInt()

    override fun get(index: Int): T {
        val objectPointer = TODO() // CInterop.queryGetObjectAt(queryPointer, clazz.simpleName!!, index)
        val model = modelFactory.invoke(clazz)
        model.isManaged = true
        model.objectPointer = objectPointer
        model.tableName = clazz.simpleName
        // call T factory to instantiate an Object of type T using it's pointer 'objectPointer'
        return model as T
    }

}
