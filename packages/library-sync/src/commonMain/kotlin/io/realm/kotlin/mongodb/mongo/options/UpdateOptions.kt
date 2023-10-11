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
package io.realm.kotlin.mongodb.mongo.options

/**
 * The options to apply when updating documents.
 */
public data class UpdateOptions(
	/**
	 * Set to `true` if a new document should be inserted if there are no matches to the query filter.
	 */
	val upsert: Boolean
) {
	override fun toString(): String {
		return """
			RemoteUpdateOptions{upsert=$upsert}
			"""
	}
}
