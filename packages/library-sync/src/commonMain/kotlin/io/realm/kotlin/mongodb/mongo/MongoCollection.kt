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

import io.realm.kotlin.mongodb.mongo.options.FindOptions
import io.realm.mongodb.mongo.iterable.AggregateIterable
import io.realm.mongodb.mongo.iterable.FindIterable
import io.realm.kotlin.mongodb.mongo.options.CountOptions
import io.realm.kotlin.mongodb.mongo.options.FindOneAndModifyOptions
import io.realm.kotlin.mongodb.mongo.options.InsertManyResult
import io.realm.kotlin.mongodb.mongo.options.UpdateOptions
import io.realm.kotlin.mongodb.mongo.result.DeleteResult
import io.realm.kotlin.mongodb.mongo.result.InsertOneResult
import io.realm.kotlin.mongodb.mongo.result.UpdateResult
import org.mongodb.kbson.BsonDocument
import kotlin.reflect.KClass

/**
 * The MongoCollection interface provides read and write access to documents.
 *
 * Use [MongoDatabase.getCollection] to get a collection instance.
 *
 * Before any access is possible, there must be an active, logged-in user.
 *
 * @param <DocumentT> The type that this collection will encode documents from and decode
 * documents to.
 * @see MongoDatabase
 */
public interface MongoCollection<DocumentT: Any> {

//	private val nameSpace: MongoNamespace
//	private val osMongoCollection: OsMongoCollection<DocumentT>

	/**
	 * Gets the namespace of this collection, i.e. the database and collection names together.
	 *
	 * @return the namespace
	 */
	public val namespace: MongoNamespace

	/**
	 * Gets the name of this collection
	 *
	 * @return the name of this collection
	 */
	public val name: String
		// return nameSpace.getCollectionName()

	/**
	 * Gets the class of documents stored in this collection.
	 *
	 * If you used the simple [MongoDatabase.getCollection] to get this collection,
	 * this is [org.bson.Document].
	 *
	 * @return the class of documents in this collection
	 */
	public val documentClass: KClass<DocumentT>
//		return osMongoCollection.getDocumentClass()

	// TODO Do not support CodecRegistry on Kotlin, but use Kotlin Serialization instead?
	// fun getCodecRegistry(): CodecRegistry {

	/**
	 * Creates a new MongoCollection instance with a different default class to cast any
	 * documents returned from the database into.
	 *
	 * @param clazz          the default class to which any documents returned from the database
	 * will be cast.
	 * @param <NewDocumentT> The type that the new collection will encode documents from and decode
	 * documents to.
	 * @return a new MongoCollection instance with the different default class
	 */
	public fun <NewDocumentT: Any> withDocumentClass(clazz: KClass<NewDocumentT>): MongoCollection<NewDocumentT>
		// return MongoCollection<DocumentT>(nameSpace, osMongoCollection.withDocumentClass(clazz))

	// TODO Do not support CodecRegistry on Kotlin, but use Kotlin Serialization instead?
	//	fun withCodecRegistry(codecRegistry: CodecRegistry?): MongoCollection<DocumentT> {

	/**
	 * Counts the number of documents in the collection.
	 *
	 * @param filter an optional query filter
	 * @param options: optional options describing the count
	 * @return a task containing the number of documents in the collection
	 */
	public suspend fun count(filter: BsonDocument = BsonDocument(), options: CountOptions = CountOptions()): Long

	/**
	 * Finds a document in the collection.
	 *
	 * @param filter the query filter
	 * @param options a [FindOptions] struct
	 *
	 * @return a task containing the result of the find one operation
	 */
	public suspend fun findOne(
		filter: BsonDocument = BsonDocument(),
		options: FindOptions = FindOptions()
	): DocumentT

	/**
	 * Finds a document in the collection.
	 *
	 * @param filter the query filter
	 * @param options a [FindOptions] struct
	 * @param resultClass the class to decode each document into
	 * @param T the target document type
	 * @return a task containing the result of the find one operation
	 */
	public suspend fun <T : Any> findOne(
		filter: BsonDocument = BsonDocument(),
		options: FindOptions = FindOptions(),
		// TODO Is there a good way to combine this method with the one above or otherwise make it easy to switch return result
		resultClass: KClass<T>
	): T

	/**
	 * Finds all documents in the collection.
	 *
	 * All documents will be delivered in the form of a [FindIterable] from which individual
	 * elements can be extracted.
	 *
	 * @param filter  the query filter
	 * @param options a [FindOptions] struct
	 * @return an iterable containing the result of the find operation
	 */
	public suspend fun find(
		filter: BsonDocument = BsonDocument(),
		options: FindOptions = FindOptions()
	): FindIterable<DocumentT>

	/**
	 * Finds all documents in the collection specifying an output class.
	 *
	 * All documents will be delivered in the form of a [FindIterable] from which individual
	 * elements can be extracted.
	 *
	 * @param filter  the query filter
	 * @param options a [FindOptions] struct
	 * @param resultClass the class to decode each document into
	 * @param <ResultT>   the target document type of the iterable.
	 * @return an iterable containing the result of the find operation
	 */
	public suspend fun <ResultT: Any> find(
		filter: BsonDocument = BsonDocument(),
		options: FindOptions = FindOptions(),
		// TODO Is there a good way to combine this method with the one above or otherwise make it easy to switch
		resultClass: KClass<ResultT>
	): FindIterable<ResultT>

	/**
	 * Aggregates documents according to the specified aggregation pipeline.
	 *
	 * All documents will be delivered in the form of an [AggregateIterable] from which
	 * individual elements can be extracted.
	 *
	 * @param pipeline the aggregation pipeline
	 * @return an [AggregateIterable] from which the results can be extracted
	 */
	public suspend fun aggregate(pipeline: List<BsonDocument>): AggregateIterable<DocumentT>

	/**
	 * Aggregates documents according to the specified aggregation pipeline specifying an output
	 * class.
	 *
	 * All documents will be delivered in the form of an [AggregateIterable] from which
	 * individual elements can be extracted.
	 *
	 * @param pipeline    the aggregation pipeline
	 * @param resultClass the class to decode each document into
	 * @param <ResultT>   the target document type of the iterable.
	 * @return an [AggregateIterable] from which the results can be extracted
	 */
	public suspend fun <ResultT: Any> aggregate(
		pipeline: List<BsonDocument>,
		// TODO Is there a good way to combine this method with the one above or otherwise make it easy to switch
		resultClass: KClass<ResultT>
	): AggregateIterable<DocumentT>

	/**
	 * Inserts the provided document. If the document is missing an identifier, the client should
	 * generate one.
	 *
	 * @param document the document to insert
	 * @return a task containing the result of the insert one operation
	 */
	public suspend fun insertOne(document: DocumentT): InsertOneResult

	/**
	 * Inserts one or more documents.
	 *
	 * @param documents the documents to insert
	 * @return a task containing the result of the insert many operation
	 */
	public suspend fun insertMany(documents: List<DocumentT>): InsertManyResult

	/**
	 * Removes at most one document from the collection that matches the given filter.  If no
	 * documents match, the collection is not
	 * modified.
	 *
	 * @param filter the query filter to apply the the delete operation
	 * @return a task containing the result of the remove one operation
	 */
	public suspend fun deleteOne(filter: BsonDocument): DeleteResult

	/**
	 * Removes all documents from the collection that match the given query filter.  If no documents
	 * match, the collection is not modified.
	 *
	 * @param filter the query filter to apply the the delete operation
	 * @return a task containing the result of the remove many operation
	 */
	public suspend fun deleteMany(filter: BsonDocument): DeleteResult

	/**
	 * Update a single document in the collection according to the specified arguments.
	 *
	 * @param filter a document describing the query filter, which may not be null.
	 * @param update a document describing the update, which may not be null. The update to
	 * apply must include only update operators.
	 * @param updateOptions the options to apply to the update operation
	 * @return a task containing the result of the update one operation
	 */
	public suspend fun updateOne(
		filter: BsonDocument,
		update: BsonDocument,
		updateOptions: UpdateOptions = UpdateOptions()
	): UpdateResult

	/**
	 * Update all documents in the collection according to the specified arguments.
	 *
	 * @param filter a document describing the query filter, which may not be null.
	 * @param update a document describing the update, which may not be null. The update to
	 * apply must include only update operators.
	 * @param updateOptions the options to apply to the update operation
	 * @return a task containing the result of the update many operation
	 */
	public suspend fun updateMany(
		filter: BsonDocument,
		update: BsonDocument,
		updateOptions: UpdateOptions = UpdateOptions()
	): UpdateResult

	/**
	 * Finds a document in the collection and performs the given update.
	 *
	 * @param filter the query filter
	 * @param update the update document
	 * @param options a [FindOneAndModifyOptions] struct
	 * @return a task containing the resulting document
	 */
	public suspend fun findOneAndUpdate(
		filter: BsonDocument,
		update: BsonDocument,
		options: FindOneAndModifyOptions = FindOneAndModifyOptions()
	): DocumentT

	/**
	 * Finds a document in the collection and performs the given update.
	 *
	 * @param filter      the query filter
	 * @param update      the update document
	 * @param options a [FindOneAndModifyOptions] struct
	 * @param resultClass the class to decode each document into
	 * @param ResultT   the target document type of the iterable.
	 * @return a task containing the resulting document
	 */
	public suspend fun <ResultT: Any> findOneAndUpdate(
		filter: BsonDocument,
		update: BsonDocument,
		options: FindOneAndModifyOptions = FindOneAndModifyOptions(),
		// TODO Is there a good way to combine this method with the one above or otherwise make it easy to switch
		resultClass: KClass<ResultT>
	): ResultT

	/**
	 * Finds a document in the collection and replaces it with the given document.
	 *
	 * @param filter      the query filter
	 * @param replacement the document to replace the matched document with
	 * @param options a [FindOneAndModifyOptions] struct
	 * @return a task containing the resulting document
	 */
	public suspend fun findOneAndReplace(
		filter: BsonDocument,
		replacement: BsonDocument,
		options: FindOneAndModifyOptions = FindOneAndModifyOptions()
	) : DocumentT

	/**
	 * Finds a document in the collection and replaces it with the given document.
	 *
	 * @param filter      the query filter
	 * @param replacement the document to replace the matched document with
	 * @param resultClass the class to decode each document into
	 * @param options a [FindOneAndModifyOptions] struct
	 * @param ResultT the target document type of the iterable.
	 * @return a task containing the resulting document
	 */
	public suspend fun <ResultT: Any> findOneAndReplace(
		filter: BsonDocument,
		replacement: BsonDocument,
		options: FindOneAndModifyOptions = FindOneAndModifyOptions(),
		// TODO Is there a good way to combine this method with the one above or otherwise make it easy to switch
		resultClass: KClass<ResultT>
	): ResultT

	/**
	 * Finds a document in the collection and delete it.
	 *
	 * @param filter the query filter
	 * @return a task containing the resulting document
	 */
	public suspend fun findOneAndDelete(
		filter: BsonDocument = BsonDocument(),
		options: FindOneAndModifyOptions = FindOneAndModifyOptions(),
	): DocumentT

	/**
	 * Finds a document in the collection and delete it.
	 *
	 * @param filter      the query filter
	 * @param options     a [FindOneAndModifyOptions] struct
	 * @param resultClass the class to decode each document into
	 * @param <ResultT>   the target document type of the iterable.
	 * @return a task containing the resulting document
	 */
	public suspend fun <ResultT: Any> findOneAndDelete(
		filter: BsonDocument = BsonDocument(),
		options: FindOneAndModifyOptions = FindOneAndModifyOptions(),
		// TODO Is there a good way to combine this method with the one above or otherwise make it easy to switch
		resultClass: KClass<ResultT>
	): ResultT

	// TODO Figure out if we should support watch
//	/**
//	 * Watches a collection. The resulting stream will be notified of all events on this collection
//	 * that the active user is authorized to see based on the configured MongoDB Realm rules.
//	 *
//	 * @return a task that provides access to the stream of change events.
//	 */
//	fun watch(): RealmEventStreamTask<DocumentT> {
//		return RealmEventStreamTaskImpl(getNamespace().getFullName(),
//			object : Executor<DocumentT>() {
//				@Throws(java.io.IOException::class)
//				fun run(): EventStream<DocumentT> {
//					return osMongoCollection.watch()
//				}
//			})
//	}
//
//	/**
//	 * Watches specified IDs in a collection.
//	 *
//	 * @param ids the ids to watch.
//	 * @return a task that provides access to the stream of change events.
//	 */
//	fun watch(vararg ids: BsonValue?): RealmEventStreamTask<DocumentT> {
//		return RealmEventStreamTaskImpl(getNamespace().getFullName(),
//			object : Executor<DocumentT>() {
//				@Throws(java.io.IOException::class)
//				fun run(): EventStream<DocumentT> {
//					return osMongoCollection.watch(java.util.Arrays.asList(*ids))
//				}
//			})
//	}
//
//	/**
//	 * Watches specified IDs in a collection. This convenience overload supports the use case
//	 * of non-[BsonValue] instances of [ObjectId] by wrapping them in
//	 * [BsonObjectId] instances for the user.
//	 *
//	 * @param ids unique object identifiers of the IDs to watch.
//	 * @return a task that provides access to the stream of change events.
//	 */
//	fun watch(vararg ids: ObjectId?): RealmEventStreamTask<DocumentT> {
//		return RealmEventStreamTaskImpl(getNamespace().getFullName(),
//			object : Executor<DocumentT>() {
//				@Throws(java.io.IOException::class)
//				fun run(): EventStream<DocumentT> {
//					return osMongoCollection.watch(java.util.Arrays.asList(*ids))
//				}
//			})
//	}
//
//	/**
//	 * Watches a collection. The provided document will be used as a match expression filter on
//	 * the change events coming from the stream. This convenience overload supports the use of
//	 * non-[BsonDocument] instances for the user.
//	 *
//	 *
//	 * See [how to define a match filter](https://docs.mongodb.com/manual/reference/operator/aggregation/match/).
//	 *
//	 *
//	 * Defining the match expression to filter ChangeEvents is similar to
//	 * [how to define the match expression for triggers](https://docs.mongodb.com/realm/triggers/database-triggers/)
//	 *
//	 * @param matchFilter the $match filter to apply to incoming change events
//	 * @return a task that provides access to the stream of change events.
//	 */
//	fun watchWithFilter(matchFilter: Document?): RealmEventStreamTask<DocumentT> {
//		return RealmEventStreamTaskImpl(getNamespace().getFullName(),
//			object : Executor<DocumentT>() {
//				@Throws(java.io.IOException::class)
//				fun run(): EventStream<DocumentT> {
//					return osMongoCollection.watchWithFilter(matchFilter)
//				}
//			})
//	}
//
//	/**
//	 * Watches a collection. The provided BSON document will be used as a match expression filter on
//	 * the change events coming from the stream.
//	 *
//	 *
//	 * See [how to define a match filter](https://docs.mongodb.com/manual/reference/operator/aggregation/match/).
//	 *
//	 *
//	 * Defining the match expression to filter ChangeEvents is similar to
//	 * [how to define the match expression for triggers](https://docs.mongodb.com/realm/triggers/database-triggers/)
//	 *
//	 * @param matchFilter the $match filter to apply to incoming change events
//	 * @return a task that provides access to the stream of change events.
//	 */
//	fun watchWithFilter(matchFilter: BsonDocument?): RealmEventStreamTask<DocumentT> {
//		return RealmEventStreamTaskImpl(getNamespace().getFullName(),
//			object : Executor<DocumentT>() {
//				@Throws(java.io.IOException::class)
//				fun run(): EventStream<DocumentT> {
//					return osMongoCollection.watchWithFilter(matchFilter)
//				}
//			})
//	}
//
//	/**
//	 * Watches a collection asynchronously. The resulting stream will be notified of all events on this collection
//	 * that the active user is authorized to see based on the configured MongoDB Realm rules.
//	 *
//	 * @return a task that provides access to the stream of change events.
//	 */
//	fun watchAsync(): RealmEventStreamAsyncTask<DocumentT> {
//		return RealmEventStreamAsyncTaskImpl(getNamespace().getFullName(),
//			object : Executor<DocumentT>() {
//				@Throws(java.io.IOException::class)
//				fun run(): EventStream<DocumentT> {
//					return osMongoCollection.watch()
//				}
//			})
//	}
//
//	/**
//	 * Watches specified IDs in a collection asynchronously.
//	 *
//	 * @param ids the ids to watch.
//	 * @return a task that provides access to the stream of change events.
//	 */
//	fun watchAsync(vararg ids: BsonValue?): RealmEventStreamAsyncTask<DocumentT> {
//		return RealmEventStreamAsyncTaskImpl(getNamespace().getFullName(),
//			object : Executor<DocumentT>() {
//				@Throws(java.io.IOException::class)
//				fun run(): EventStream<DocumentT> {
//					return osMongoCollection.watch(java.util.Arrays.asList(*ids))
//				}
//			})
//	}
//
//	/**
//	 * Watches specified IDs in a collection asynchronously. This convenience overload supports the use case
//	 * of non-[BsonValue] instances of [ObjectId] by wrapping them in
//	 * [BsonObjectId] instances for the user.
//	 *
//	 * @param ids unique object identifiers of the IDs to watch.
//	 * @return a task that provides access to the stream of change events.
//	 */
//	fun watchAsync(vararg ids: ObjectId?): RealmEventStreamAsyncTask<DocumentT> {
//		return RealmEventStreamAsyncTaskImpl(getNamespace().getFullName(),
//			object : Executor<DocumentT>() {
//				@Throws(java.io.IOException::class)
//				fun run(): EventStream<DocumentT> {
//					return osMongoCollection.watch(java.util.Arrays.asList(*ids))
//				}
//			})
//	}
//
//	/**
//	 * Watches a collection asynchronously. The provided document will be used as a match expression filter on
//	 * the change events coming from the stream. This convenience overload supports the use of
//	 * non-[BsonDocument] instances for the user.
//	 *
//	 *
//	 * See [how to define a match filter](https://docs.mongodb.com/manual/reference/operator/aggregation/match/).
//	 *
//	 *
//	 * Defining the match expression to filter ChangeEvents is similar to
//	 * [how to define the match expression for triggers](https://docs.mongodb.com/realm/triggers/database-triggers/)
//	 *
//	 * @param matchFilter the $match filter to apply to incoming change events
//	 * @return a task that provides access to the stream of change events.
//	 */
//	fun watchWithFilterAsync(matchFilter: Document?): RealmEventStreamAsyncTask<DocumentT> {
//		return RealmEventStreamAsyncTaskImpl(getNamespace().getFullName(),
//			object : Executor<DocumentT>() {
//				@Throws(java.io.IOException::class)
//				fun run(): EventStream<DocumentT> {
//					return osMongoCollection.watchWithFilter(matchFilter)
//				}
//			})
//	}
//
//	/**
//	 * Watches a collection asynchronously. The provided BSON document will be used as a match expression filter on
//	 * the change events coming from the stream.
//	 *
//	 *
//	 * See [how to define a match filter](https://docs.mongodb.com/manual/reference/operator/aggregation/match/).
//	 *
//	 *
//	 * Defining the match expression to filter ChangeEvents is similar to
//	 * [how to define the match expression for triggers](https://docs.mongodb.com/realm/triggers/database-triggers/)
//	 *
//	 * @param matchFilter the $match filter to apply to incoming change events
//	 * @return a task that provides access to the stream of change events.
//	 */
//	fun watchWithFilterAsync(matchFilter: BsonDocument?): RealmEventStreamAsyncTask<DocumentT> {
//		return RealmEventStreamAsyncTaskImpl(getNamespace().getFullName(),
//			object : Executor<DocumentT>() {
//				@Throws(java.io.IOException::class)
//				fun run(): EventStream<DocumentT> {
//					return osMongoCollection.watchWithFilter(matchFilter)
//				}
//			})
//	}
}
