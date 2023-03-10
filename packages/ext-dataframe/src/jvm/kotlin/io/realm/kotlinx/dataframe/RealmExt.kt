package io.realm.kotlinx.dataframe

import io.realm.kotlin.Realm

/**
 * Returns a description of this extension and its dependencies.
 *
 * @return a string representation of this extension library and its dependencies.
 */
public fun Realm.Companion.getInfo(): String {
    val apiVersion = io.realm.kotlinx.dataframe.internal.API_VERSION
    val realmVersion = io.realm.kotlin.internal.SDK_VERSION
    return "Realm Kotlin Jupyter/DataFrame Extension: v$apiVersion with Realm v$realmVersion"
}
