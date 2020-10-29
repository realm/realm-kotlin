package test
import io.realm.interop.PropertyType
import io.realm.interop.RealmInterop
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import io.realm.runtimeapi.RealmObject
import kotlin.reflect.KProperty

@RealmObject
class Sample : RealmModel {

    class RealmStringDelegate {
        var field: String = ""
        operator fun getValue(sample: RealmModel, property: KProperty<*>): String {
            val internal = sample as RealmModelInternal
            if (internal.isManaged!!) {
                return RealmInterop.realm_get_value(internal.realmPointer!!, internal.realmObjectPointer!!, sample::class.simpleName!!, property.name, PropertyType.RLM_PROPERTY_TYPE_STRING)
            } else {
                return field
            }
        }

        operator fun setValue(sample: RealmModel, property: KProperty<*>, s: String) {
            val internal = sample as RealmModelInternal
            if (internal.isManaged!!) {
                return RealmInterop.realm_set_value(internal.realmPointer!!, internal.realmObjectPointer!!, sample::class.simpleName!!, property.name, s, false)
            } else {
                field = s
            }

        }
    }

    var name: String? = "foo"

    var test: String by RealmStringDelegate()
}
