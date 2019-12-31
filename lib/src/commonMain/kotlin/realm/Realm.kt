package realm

expect class Realm() {
    companion object {
        fun open(realmConfiguration: RealmConfiguration) : Realm
    }
//    fun open(dbName: String, schema: String) : Realm
    fun beginTransaction()
    fun commitTransaction()
    fun registerListener(f: () -> Unit)
    //    reflection is not supported in K/N so we can't offer method like
    //    inline fun <reified T : RealmModel> create() : T
    //    to create dynamically a managed model. we're limited thus to persist methods
    //    were we take an already created un-managed instance and return a new manageable one
    //    (note since parameter are immutable in Kotlin, we need to create a new instance instead of
    //    doing this operation in place)
    fun <T : RealmModel> save(unmanaged: T) : T
}
