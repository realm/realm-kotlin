package realm

actual class Realm actual constructor() {
//    actual fun open(dbName: String, schema: String) : Realm {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }

    actual fun beginTransaction() {
    }

    actual fun commitTransaction() {
    }

    actual fun registerListener(f: () -> Unit) {
    }

//    actual inline fun <reified T : RealmModel> create(): T {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }

    actual companion object {
        actual fun open(realmConfiguration: RealmConfiguration): Realm {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    actual fun <T : RealmModel> save(unmanaged: T): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}