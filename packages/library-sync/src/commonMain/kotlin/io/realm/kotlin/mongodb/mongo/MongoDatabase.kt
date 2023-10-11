/*
 * Copyright 2020 Realm Inc.
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
package io.realm.kotlin.mongodb.mongo

import org.mongodb.kbson.BsonDocument
import kotlin.reflect.KClass

/**
 * The RemoteMongoDatabase provides access to its [BsonDocument] [MongoCollection]s.
 */
public interface MongoDatabase {

	/**
	 * Gets the name of the database.
	 *
	 * @return the database name
	 */
	public val name: String

	/**
	 * Gets a collection.
	 *
	 * @param collectionName the name of the collection to return
	 * @return the collection
	 */
	public fun getCollection(collectionName: String): MongoCollection<BsonDocument>

	/**
	 * Gets a collection, with a specific default document class.
	 *
	 * @param collectionName the name of the collection to return
	 * @param documentClass  the default class to cast any documents returned from the database into.
	 * @param DocumentT the type of the class to use instead of `Document`.
	 * @return the collection
	 */
	public fun <DocumentT: Any> getCollection(
		collectionName: String,
		// TODO Is there a good way to combine this method with the one above or otherwise make it easy to switch
		documentClass: KClass<DocumentT>
	): MongoCollection<DocumentT>
}
