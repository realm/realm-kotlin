package io.realm

import kotlin.reflect.KClass

class RealmResults<T : RealmModel> constructor(private val queryPointer: BindingPointer,
                                                                           private val clazz: KClass<T>,
                                                                           private val modelFactory: ModelFactory
) : AbstractList<T>() {
    override val size: Int
        get() = CInterop.queryGetSize(queryPointer).toInt()

    override fun get(index: Int): T {
        val objectPointer =
            CInterop.queryGetObjectAt(queryPointer, clazz.simpleName!!, index)
        val model = modelFactory.invoke(clazz)
        model.isManaged = true
        model.objectPointer = objectPointer
        model.tableName = clazz.simpleName
        // call T factory to instantiate an Object of type T using it's pointer 'objectPointer'
        return model as T
    }

}