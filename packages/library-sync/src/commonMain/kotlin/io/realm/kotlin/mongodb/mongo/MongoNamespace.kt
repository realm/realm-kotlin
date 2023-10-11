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
package io.realm.kotlin.mongodb.mongo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * TODO This class needs to be supported by Kotlin Serialization
 * A MongoDB namespace, which includes a database name and collection name.
 */
@Serializable
public class MongoNamespace {

	/**
	 * Construct an instance for the given full name.  The database name is the string preceding the first `"."` character.
	 *
	 * @param fullName the non-null full namespace
	 * @see .checkDatabaseNameValidity
	 * @see .checkCollectionNameValidity
	 */
	public constructor(fullName: String) {
		this.fullName = fullName
		databaseName = getDatatabaseNameFromFullName(fullName)
		collectionName = getCollectionNameFullName(fullName)
		checkDatabaseNameValidity(databaseName)
		checkCollectionNameValidity(collectionName)
	}

	/**
	 * Construct an instance from the given database name and collection name.
	 *
	 * @param databaseName   the valid database name
	 * @param collectionName the valid collection name
	 * @see .checkDatabaseNameValidity
	 * @see .checkCollectionNameValidity
	 */
	public constructor(
		 databaseName: String,
		 collectionName: String
	) {
		checkDatabaseNameValidity(databaseName)
		checkCollectionNameValidity(collectionName)
		this.databaseName = databaseName
		this.collectionName = collectionName
		fullName = "$databaseName.$collectionName"
	}

	/**
	 * Gets the database name.
	 *
	 * @return the database name
	 */
	@SerialName("db")
	public val databaseName: String

	/**
	 * Gets the collection name.
	 *
	 * @return the collection name
	 */
	@SerialName("coll")
	public val collectionName: String

	/**
	 * Gets the full name, which is the database name and the collection name, separated by a period.
	 *
	 * @return the full name
	 */
	@Transient
	public val fullName: String



	/**
	 * Returns the standard MongoDB representation of a namespace, which is `&lt;database&gt;.&lt;collection&gt;`.
	 *
	 * @return string representation of the namespace.
	 */
	override fun toString(): String {
		return fullName
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as MongoNamespace

		if (databaseName != other.databaseName) return false
		return collectionName == other.collectionName
	}

	override fun hashCode(): Int {
		var result = databaseName.hashCode()
		result = 31 * result + collectionName.hashCode()
		return result
	}

	private companion object {
		private const val COMMAND_COLLECTION_NAME = "\$cmd"
		private val PROHIBITED_CHARACTERS_IN_DATABASE_NAME: Set<Char> = setOf(
			'\u0000', '/', '\\', ' ', '"', '.'
		)

		/**
		 * Check the validity of the given database name. A valid database name is non-null, non-empty, and does not contain any of the
		 * following characters: `'\0', '/', '\\', ' ', '"', '.'`. The server may impose additional restrictions on database names.
		 *
		 * @param databaseName the database name
		 * @throws IllegalArgumentException if the database name is invalid
		 */
		private fun checkDatabaseNameValidity(databaseName: String) {
			isTrueArgument("databaseName is not empty", databaseName.isNotEmpty())
			for (i in databaseName.indices) {
				isTrueArgument(
					"databaseName does not contain '" + databaseName[i] + "'",
					!PROHIBITED_CHARACTERS_IN_DATABASE_NAME.contains(
						databaseName[i]
					)
				)
			}
		}

		/**
		 * Check the validity of the given collection name.   A valid collection name is non-null and non-empty.  The server may impose
		 * additional restrictions on collection names.
		 *
		 * @param collectionName the collection name
		 * @throws IllegalArgumentException if the collection name is invalid
		 */
		private fun checkCollectionNameValidity(collectionName: String) {
			isTrueArgument("collectionName is not empty", !collectionName.isEmpty())
		}

		private fun getCollectionNameFullName(namespace: String): String {
			val firstDot = namespace.indexOf('.')
			return if (firstDot == -1) {
				namespace
			} else namespace.substring(firstDot + 1)
		}

		private fun getDatatabaseNameFromFullName(namespace: String): String {
			val firstDot = namespace.indexOf('.')
			return if (firstDot == -1) {
				""
			} else namespace.substring(0, firstDot)
		}

		private fun isTrueArgument(name: String, condition: Boolean) {
			if (!condition) {
				throw IllegalArgumentException("state should be: $name")
			}
		}
	}
}
