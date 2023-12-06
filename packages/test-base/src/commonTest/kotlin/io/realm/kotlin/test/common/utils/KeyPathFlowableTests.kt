package io.realm.kotlin.test.common.utils

import kotlin.test.Test

/**
 * All tests classes that tests classes exposing keypath notifications (RealmObject, RealmResults,
 * RealmList, RealmSet, RealmMap) should implement this interface to be sure that we test common
 * behaviour across those classes.
 */
interface KeyPathFlowableTests {
    @Test
    fun keyPath_topLevelProperty()

    @Test
    fun keyPath_nestedProperty()

    @Test
    fun keyPath_defaultDepth()

    @Test
    fun keyPath_propertyBelowDefaultLimit()

    @Test
    fun keyPath_unknownTopLevelProperty()

    @Test
    fun keyPath_unknownNestedProperty()

    @Test
    fun keyPath_invalidNestedProperty()
}
