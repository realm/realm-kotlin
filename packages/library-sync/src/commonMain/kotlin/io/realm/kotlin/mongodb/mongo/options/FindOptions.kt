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

import org.mongodb.kbson.BsonDocument

/**
 * The options to apply to a find operation (also commonly referred to as a query).
 */
public data class FindOptions(
	/**
	 * The limit to apply. The default is 0, which means there is no limit.
	 */
	val limit: Int = 0,
	/**
	 * A document describing the fields to return for all matching documents.
	 * // TODO Test if there is a difference between `null` and `BsonDocument()`, if not, maybe just disallow `null`?
	 */
	val projection: BsonDocument? = null,
	/**
	 * The sort criteria to apply to the query, or `null` if no sorting is done.
	 * // TODO Test if there is a difference between `null` and `BsonDocument()`, if not, maybe just disallow `null`?
	 */
	val	sort: BsonDocument? = null
) {
	override fun toString(): String {
		return """
			RemoteFindOptions{
				limit=$limit,
				projection=$projection,
				sort=$sort
			}
		""".trimIndent()
	}
}

