/*
 * Copyright 2023 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.test.common.utils

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
