package realm

import kotlin.reflect.KClass

class RealmConfiguration private constructor(
    val path: String?, // Full path if we don't want to use the default location
    val name: String?,
    val schema: Array<KClass<RealmModel>>?
) {
    data class Builder(
        var path: String? = null,
        var name: String? = null,
        var schema: Array<KClass<RealmModel>>? = null
    ) {
        fun path(path: String) = apply { this.path = path }
        fun name(name: String) = apply { this.name = name }
        fun schema(schema: Array<KClass<RealmModel>>) = apply { this.schema = schema }
        fun build() = RealmConfiguration(path, name, schema)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Builder

            if (path != other.path) return false
            if (name != other.name) return false
            if (schema != null) {
                if (other.schema == null) return false
                if (!schema!!.contentEquals(other.schema!!)) return false
            } else if (other.schema != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = path?.hashCode() ?: 0
            result = 31 * result + (name?.hashCode() ?: 0)
            result = 31 * result + (schema?.contentHashCode() ?: 0)
            return result
        }
    }
}