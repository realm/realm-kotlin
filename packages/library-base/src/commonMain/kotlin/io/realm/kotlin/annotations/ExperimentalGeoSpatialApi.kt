/*
 * Copyright 2023 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.realm.kotlin.annotations

/**
 * This annotation mark Realm APIs for geo spatial queries that are considered **experimental**, i.e.
 * there are no guarantees given that these APIs cannot change without warning between minor and
 * major versions. They will not change between patch versions.
 *
 * For all other purposes these APIs are considered stable, i.e. they undergo the same testing
 * as other parts of the API and should behave as documented with no bugs. They are primarily
 * marked as experimental because we are unsure if these APIs provide value and solve the use
 * cases that people have. If not, they will be changed or removed altogether.
 */
@MustBeDocumented
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS
)
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public annotation class ExperimentalGeoSpatialApi
