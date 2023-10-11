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
 * The options to apply to a findOneAndUpdate, findOneAndReplace, or findOneAndDelete operation
 * (also commonly referred to as findOneAndModify operations).
 */
public data class FindOneAndModifyOptions(
	/**
	 * A document describing the fields to return for all matching documents.
	 * // TODO Test if there is a difference between `null` and `BsonDocument()`, if not, maybe just disallow `null`?
	 */
	val projection: BsonDocument? = null,
	/**
	 * The sort criteria to apply to the query, or `null` if no sorting is done.
	 * // TODO Test if there is a difference between `null` and `BsonDocument()`, if not, maybe just disallow `null`?
	 */
	val sort: BsonDocument? = null,
	/**
	 * Set to `true` if a new document should be inserted if there are no matches to the query filter.
	 */
	val upsert: Boolean = false,
	/**
	 * Set to true if findOneAndModify operations should return the new updated document.
	 * Set to false / leave blank to have these operation return the document before the update.
	 * Note: Only findOneAndUpdate and findOneAndReplace take this options
	 * findOneAndDelete will always return the old document
	 */
	val returnNewDocument: Boolean = false
) {
	override fun toString(): String {
		return """
			RemoteFindOneAndModifyOptions{
				projection=$projection,
				sort=$sort,
				upsert=$upsert,
				returnNewDocument=$returnNewDocument
			}
			"""
	}
}
