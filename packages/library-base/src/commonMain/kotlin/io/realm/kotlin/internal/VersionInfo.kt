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

package io.realm.kotlin.internal

import io.realm.kotlin.VersionId

/**
 * Version meta data for an overall [Realm]-instance with [VersionData] for the user-facing [Realm]
 * and the underlying [SuspendableNotifier]'s and [SuspendableWriter]'s live realms.
 */
public data class VersionInfo(val main: VersionData?, val notifier: VersionData?, val writer: VersionData?) {
    val all: Set<VersionId> = setOf(notifier, writer).mapNotNull { it?.versions }.flatten().toSet()
    val allTracked: Set<VersionId> = setOf(notifier, writer).mapNotNull { it?.active }.flatten().toSet()
}
