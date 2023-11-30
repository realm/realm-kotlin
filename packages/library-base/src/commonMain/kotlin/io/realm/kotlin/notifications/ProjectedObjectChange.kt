/*
 * Copyright 2021 Realm Inc.
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

package io.realm.kotlin.notifications

public sealed interface SingleProjectionQueryChange<O : Any> {
    /**
     * Returns the newest state of object being observed. `null` is returned if there is no object to
     * observe.
     */
    public val obj: O?
}

/**
 * TODO Docs
 */
public interface PendingProjection<O : Any> : SingleProjectionQueryChange<O>

/**
 * TODO Docs
 * TODO Annoying to have both `ProjectionsChanges` and `ProjectionChange`...other name for one of them?
 */
public sealed interface ProjectionChange<O : Any> : SingleProjectionQueryChange<O> {
    /**
     * Returns the newest state of object being observed. `null` is returned if the object
     * has been deleted.
     */
    override val obj: O?
}

/**
 * TODO Docs
 */
public interface InitialProjection<O : Any> : SingleProjectionQueryChange<O> {
    override val obj: O
}

/**
 * TODO Docs
 * TODO Can we actually track all changed fields?
 */
public interface UpdatedProjection<O : Any> : SingleProjectionQueryChange<O> {
    override val obj: O

    /**
     * Returns the names of properties that has changed.
     */
    public val changedFields: Array<String>

    /**
     * Checks if a given field has been changed.
     *
     * @param fieldName to be checked if its value has been changed.
     * @return `true` if the field has been changed. It returns `false` the field cannot be found
     * or the field hasn't been changed.
     */
    public fun isFieldChanged(fieldName: String): Boolean {
        return changedFields.firstOrNull { it == fieldName } != null
    }
}

/**
 * TODO Docs
 */
public interface DeletedProjection<O : Any> : ProjectionChange<O>
