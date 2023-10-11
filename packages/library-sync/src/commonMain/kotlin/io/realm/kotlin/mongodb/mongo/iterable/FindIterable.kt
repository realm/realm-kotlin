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
package io.realm.mongodb.mongo.iterable

import io.realm.internal.jni.JniBsonProtocol
import io.realm.internal.network.NetworkRequest
import io.realm.internal.objectstore.OsJavaNetworkTransport
import io.realm.internal.objectstore.OsMongoCollection
import io.realm.kotlin.mongodb.mongo.options.FindOptions
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson
import org.mongodb.kbson.serialization.Bson

// TODO Unclear exactly how much this needs to change? Should we also expose this as an `FindFlow()`
//  similar to the coroutine kotlin driver: https://github.com/mongodb/mongo-java-driver/blob/master/driver-kotlin-coroutine/src/main/kotlin/com/mongodb/kotlin/client/coroutine/MongoCollection.kt#L285C73-L285C81
//  or should we expose both?

/**
 * Specific iterable for [io.realm.mongodb.mongo.MongoCollection.find] operations.
 *
 * @param <ResultT> The type to which this iterable will decode documents.
</ResultT> */
class FindIterable<ResultT>(
	threadPoolExecutor: java.util.concurrent.ThreadPoolExecutor?,
	osMongoCollection: OsMongoCollection<*>,
	codecRegistry: CodecRegistry?,
	resultClass: java.lang.Class<ResultT>
) : MongoIterable<ResultT>(threadPoolExecutor, osMongoCollection, codecRegistry, resultClass) {
	private val options: FindOptions?
	private val encodedEmptyDocument: String
	private var filter: Bson

	init {
		options = FindOptions()
		filter = Document()
		encodedEmptyDocument = JniBsonProtocol.encode(Document(), codecRegistry)
	}

	override fun callNative(callback: NetworkRequest<*>) {
		val filterString: String = JniBsonProtocol.encode(filter, codecRegistry)
		var projectionString = encodedEmptyDocument
		var sortString = encodedEmptyDocument
		if (options == null) {
			nativeFind(
				FIND,
				osMongoCollection.getNativePtr(),
				filterString,
				projectionString,
				sortString,
				0,
				callback
			)
		} else {
			projectionString = JniBsonProtocol.encode(options.getProjection(), codecRegistry)
			sortString = JniBsonProtocol.encode(options.getSort(), codecRegistry)
			nativeFind(
				FIND_WITH_OPTIONS,
				osMongoCollection.getNativePtr(),
				filterString,
				projectionString,
				sortString,
				options.getLimit()
					.toLong(),
				callback
			)
		}
	}

	/**
	 * Sets the query filter to apply to the query.
	 *
	 * @param filter the filter, which may be null.
	 * @return this
	 */
	fun filter(@Nullable filter: Bson): FindIterable<ResultT> {
		this.filter = filter
		return this
	}

	/**
	 * Sets the limit to apply.
	 *
	 * @param limit the limit, which may be 0
	 * @return this
	 */
	fun limit(limit: Int): FindIterable<ResultT> {
		options!!.limit(limit)
		return this
	}

	/**
	 * Sets a document describing the fields to return for all matching documents.
	 *
	 * @param projection the project document, which may be null.
	 * @return this
	 */
	fun projection(@Nullable projection: Bson): FindIterable<ResultT> {
		options!!.projection(projection)
		return this
	}

	/**
	 * Sets the sort criteria to apply to the query.
	 *
	 * @param sort the sort criteria, which may be null.
	 * @return this
	 */
	fun sort(@Nullable sort: Bson): FindIterable<ResultT> {
		options!!.sort(sort)
		return this
	}

	companion object {
		private const val FIND = 1
		private const val FIND_WITH_OPTIONS = 2
		private external fun nativeFind(
			findType: Int,
			remoteMongoCollectionPtr: Long,
			filter: String,
			projection: String,
			sort: String,
			limit: Long,
			callback: OsJavaNetworkTransport.NetworkTransportJNIResultCallback
		)
	}
}
