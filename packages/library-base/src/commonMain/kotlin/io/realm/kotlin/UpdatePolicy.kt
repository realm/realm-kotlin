package io.realm.kotlin

import io.realm.kotlin.dynamic.DynamicMutableRealm

/**
 * Update policy that controls how to handle import of objects with existing primary keys when
 * import them with [MutableRealm.copyToRealm] and [DynamicMutableRealm.copyToRealm].
 *
 * @see MutableRealm.copyToRealm
 * @see DynamicMutableRealm.copyToRealm
 */
public enum class UpdatePolicy {
    /**
     * Update policy that will disallow updating existing objects and instead throw an exception if
     * an object already exists with the same primary key.
     */
    ERROR,

    /**
     * Update policy that will update all properties on any existing objects identified with the same
     * primary key. Properties will be marked as updated in change listeners, even if the property
     * was updated to the same value.
     */
    ALL,
}
