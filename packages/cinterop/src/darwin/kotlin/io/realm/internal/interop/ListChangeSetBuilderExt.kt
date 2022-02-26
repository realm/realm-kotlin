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

package io.realm.internal.interop

import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.get
import kotlin.reflect.KMutableProperty0

private fun CArrayPointer<ULongVar>.asAccessor(): ArrayAccessor = { index -> this[index].toInt() }

private fun CArrayPointer<realm_wrapper.realm_index_range_t>.asFromAccessor(): ArrayAccessor =
    { index -> this[index].from.toInt() }

private fun CArrayPointer<realm_wrapper.realm_index_range_t>.asToAccessor(): ArrayAccessor =
    { index -> this[index].to.toInt() }

private fun <T, R> ListChangeSetBuilder<T, R>.initIndicesArray(
    size: CArrayPointer<ULongVar>,
    indices: CArrayPointer<ULongVar>
) = initIndicesArray(size[0].toInt(), indices.asAccessor())

private fun <T, R> ListChangeSetBuilder<T, R>.initRangesArray(
    size: CArrayPointer<ULongVar>,
    ranges: CArrayPointer<realm_wrapper.realm_index_range_t>
) = initRangesArray(size[0].toInt(), ranges.asFromAccessor(), ranges.asToAccessor())

fun <T, R> ListChangeSetBuilder<T, R>.initIndicesArray(
    array: KMutableProperty0<IntArray>,
    size: CArrayPointer<ULongVar>,
    indices: CArrayPointer<ULongVar>
) = array.set(initIndicesArray(size, indices))

fun <T, R> ListChangeSetBuilder<T, R>.initRangesArray(
    array: KMutableProperty0<Array<R>>,
    size: CArrayPointer<ULongVar>,
    ranges: CArrayPointer<realm_wrapper.realm_index_range_t>
) = array.set(initRangesArray(size, ranges))
