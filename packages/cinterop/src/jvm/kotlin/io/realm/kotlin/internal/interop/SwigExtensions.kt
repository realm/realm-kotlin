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
@file:Suppress("ClassNaming")

package io.realm.kotlin.internal.interop

/**
 * realm_class_info_t variant that automatically frees any heap allocated resources
 */
class realm_class_info_t_managed : realm_class_info_t() {
    @Synchronized
    override fun delete() {
        if (realm_class_info_t.getCPtr(this) != 0L) {
            realmc.realm_class_info_t_cleanup(this)
        }
        super.delete()
    }
}

/**
 * realm_property_info_t variant that automatically frees any heap allocated resources
 */
class realm_property_info_t_managed : realm_property_info_t() {
    @Synchronized
    override fun delete() {
        if (realm_property_info_t.getCPtr(this) != 0L) {
            realmc.realm_property_info_t_cleanup(this)
        }
        super.delete()
    }
}
