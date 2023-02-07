package io.realm.kotlin.test

/**
 * All classes that tests classes that exposes notifications on entities that can be removed from
 * the realm (i.e. RealmObject, RealmList, RealmSet, Backlinks but specifically not Realm and
 * RealmResults) should implement this interface to be sure that we test common behaviour across
 * those classes.
 */
interface RealmEntityNotificationTests : FlowableTests {
    // Verify that we get deletion events and close the Flow when the object being observed (or
    // containing object) is deleted.
    fun deleteEntity()

    // Verify that we emit deletion events and close the flow when registering for notifications on
    // an outdated entity.
    fun asFlowOnDeleteEntity()
}
