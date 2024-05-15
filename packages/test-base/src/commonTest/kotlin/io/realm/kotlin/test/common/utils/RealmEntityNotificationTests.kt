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
 * Test for top level entities that can be deleted and supports key-path-filtering (i.e.
 * RealmObject, RealmList, RealmSet, Backlinks but specifically not Realm,  RealmResults and
 * RealmAny) should implement this interface to be sure that we test common behaviour across
 * those classes.
 */
interface RealmEntityNotificationTests :
    FlowableTests, DeletableEntityNotificationTests, KeyPathFlowableTests
