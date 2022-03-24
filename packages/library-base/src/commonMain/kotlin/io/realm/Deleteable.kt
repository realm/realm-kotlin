/*
 * Copyright 2022 Realm Inc.
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

package io.realm

import io.realm.dynamic.DynamicMutableRealm

/**
 * A **deleteable** is an entity that can be deleted in a write transaction or as part of a
 * migration.
 *
 * @see MutableRealm.delete
 * @see DynamicMutableRealm.delete
 */
public interface Deleteable
