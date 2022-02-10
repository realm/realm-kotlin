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

import kotlin.reflect.KMutableProperty0

private fun LongArray.asAccessor(): ArrayAccessor = { index -> this[index].toInt() }

private fun Array<LongArray>.asFromAccessor(): ArrayAccessor = { index -> this[index][0].toInt() }
private fun Array<LongArray>.asToAccessor(): ArrayAccessor = { index -> this[index][1].toInt() }

private fun <T, R> CollectionChangeSetBuilder<T, R>.initIndicesArray(indices: LongArray) =
    initIndicesArray(indices.size, indices.asAccessor())

private fun <T, R> CollectionChangeSetBuilder<T, R>.initRangesArray(ranges: Array<LongArray>) =
    initRangesArray(ranges.size, ranges.asFromAccessor(), ranges.asToAccessor())

fun <T, R> CollectionChangeSetBuilder<T, R>.initIndicesArray(
    array: KMutableProperty0<IntArray>,
    indices: LongArray
) = array.set(initIndicesArray(indices))

fun <T, R> CollectionChangeSetBuilder<T, R>.initRangesArray(
    array: KMutableProperty0<Array<R>>,
    ranges: Array<LongArray>
) = array.set(initRangesArray(ranges))
