// TODO Figure out if we should support watch
///*
// * Copyright 2020 Realm Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package io.realm.mongodb.mongo.events
//
//import io.realm.internal.Util.checkContainsKey
//import io.realm.mongodb.AppException
//import io.realm.mongodb.ErrorCode
//import org.bson.BsonArray
//import org.bson.BsonBoolean
//import org.bson.BsonDocument
//import org.bson.BsonElement
//import org.bson.BsonString
//import org.bson.BsonValue
//
///**
// * Indicates which fields have been modified in a given update operation.
// */
//class UpdateDescription internal constructor(
//	updatedFields: BsonDocument?,
//	removedFields: Collection<String>?
//) {
//	private val updatedFields: BsonDocument
//	private val removedFields: MutableSet<String>
//
//	/**
//	 * Creates an update description with the specified updated fields and removed field names.
//	 *
//	 * @param updatedFields Nested key-value pair representation of updated fields.
//	 * @param removedFields Collection of removed field names.
//	 */
//	init {
//		this.updatedFields = if (updatedFields == null) BsonDocument() else updatedFields
//		this.removedFields =
//			if (removedFields == null) java.util.HashSet<String>() else java.util.HashSet<String>(
//				removedFields
//			)
//	}
//
//	/**
//	 * Returns a [BsonDocument] containing keys and values representing (respectively) the
//	 * fields that have changed in the corresponding update and their new values.
//	 *
//	 * @return the updated field names and their new values.
//	 */
//	fun getUpdatedFields(): BsonDocument {
//		return updatedFields
//	}
//
//	/**
//	 * Returns a [List] containing the field names that have been removed in the corresponding
//	 * update.
//	 *
//	 * @return the removed fields names.
//	 */
//	fun getRemovedFields(): Collection<String> {
//		return removedFields
//	}
//
//	/**
//	 * Convert this update description to an update document.
//	 *
//	 * @return an update document with the appropriate $set and $unset documents.
//	 */
//	fun toUpdateDocument(): BsonDocument {
//		val unsets: MutableList<BsonElement> = java.util.ArrayList<BsonElement>()
//		for (removedField in removedFields) {
//			unsets.add(BsonElement(removedField, BsonBoolean(true)))
//		}
//		val updateDocument = BsonDocument()
//		if (updatedFields.size() > 0) {
//			updateDocument.append("\$set", updatedFields)
//		}
//		if (unsets.size > 0) {
//			updateDocument.append("\$unset", BsonDocument(unsets))
//		}
//		return updateDocument
//	}
//
//	/**
//	 * Converts this update description to its document representation as it would appear in a
//	 * MongoDB Change Event.
//	 *
//	 * @return the update description document as it would appear in a change event
//	 */
//	fun toBsonDocument(): BsonDocument {
//		val updateDescDoc = BsonDocument()
//		updateDescDoc.put(
//			Fields.UPDATED_FIELDS_FIELD,
//			getUpdatedFields()
//		)
//		val removedFields = BsonArray()
//		for (field in getRemovedFields()) {
//			removedFields.add(BsonString(field))
//		}
//		updateDescDoc.put(
//			Fields.REMOVED_FIELDS_FIELD,
//			removedFields
//		)
//		return updateDescDoc
//	}
//
//	/**
//	 * Unilaterally merge an update description into this update description.
//	 *
//	 * @param otherDescription the update description to merge into this
//	 * @return this merged update description
//	 */
//	fun merge(@Nullable otherDescription: UpdateDescription?): UpdateDescription {
//		if (otherDescription != null) {
//			for ((key) in updatedFields.entrySet()) {
//				if (otherDescription.removedFields.contains(key)) {
//					updatedFields.remove(key)
//				}
//			}
//			for (removedField in removedFields) {
//				if (otherDescription.updatedFields.containsKey(removedField)) {
//					removedFields.remove(removedField)
//				}
//			}
//			removedFields.addAll(otherDescription.removedFields)
//			updatedFields.putAll(otherDescription.updatedFields)
//		}
//		return this
//	}
//
//	val isEmpty: Boolean
//		/**
//		 * Determines whether this update description is empty.
//		 *
//		 * @return true if the update description is empty, false otherwise
//		 */
//		get() = updatedFields.isEmpty() && removedFields.isEmpty()
//
//	override fun equals(obj: Any?): Boolean {
//		if (obj == null || obj.javaClass != UpdateDescription::class.java) {
//			return false
//		}
//		val other = obj as UpdateDescription
//		return other.getRemovedFields() == removedFields && other.getUpdatedFields().equals(
//			updatedFields
//		)
//	}
//
//	override fun hashCode(): Int {
//		return removedFields.hashCode() + 31 * updatedFields.hashCode()
//	}
//
//	private object Fields {
//		const val UPDATED_FIELDS_FIELD = "updatedFields"
//		const val REMOVED_FIELDS_FIELD = "removedFields"
//	}
//
//	companion object {
//		private const val DOCUMENT_VERSION_FIELD = "__stitch_sync_version"
//
//		/**
//		 * Converts an update description BSON document from a MongoDB Change Event into an
//		 * UpdateDescription object.
//		 *
//		 * @param document the
//		 * @return the converted UpdateDescription
//		 */
//		fun fromBsonDocument(document: BsonDocument): UpdateDescription {
//			try {
//				checkContainsKey(Fields.UPDATED_FIELDS_FIELD, document, "document")
//				checkContainsKey(Fields.REMOVED_FIELDS_FIELD, document, "document")
//			} catch (exception: java.lang.IllegalArgumentException) {
//				throw AppException(ErrorCode.EVENT_DESERIALIZING, exception)
//			}
//			val removedFieldsArr: BsonArray = document.getArray(Fields.REMOVED_FIELDS_FIELD)
//			val removedFields: MutableSet<String> = java.util.HashSet(removedFieldsArr.size())
//			for (field in removedFieldsArr) {
//				removedFields.add(field.asString().getValue())
//			}
//			return UpdateDescription(
//				document.getDocument(Fields.UPDATED_FIELDS_FIELD),
//				removedFields
//			)
//		}
//
//		/**
//		 * Find the diff between two documents.
//		 *
//		 *
//		 * NOTE: This does not do a full diff on [BsonArray]. If there is
//		 * an inequality between the old and new array, the old array will
//		 * simply be replaced by the new one.
//		 *
//		 * @param beforeDocument original document
//		 * @param afterDocument  document to diff on
//		 * @param onKey          the key for our depth level
//		 * @param updatedFields  contiguous document of updated fields,
//		 * nested or otherwise
//		 * @param removedFields  contiguous list of removedFields,
//		 * nested or otherwise
//		 * @return a description of the updated fields and removed keys between the documents
//		 */
//		private fun diff(
//			beforeDocument: BsonDocument,
//			afterDocument: BsonDocument,
//			@Nullable onKey: String?,
//			updatedFields: BsonDocument,
//			removedFields: MutableSet<String>
//		): UpdateDescription {
//			// for each key in this document...
//			for ((key, oldValue) in beforeDocument.entrySet()) {
//				// don't worry about the _id or version field for now
//				if (key == "_id" || key == DOCUMENT_VERSION_FIELD) {
//					continue
//				}
//				val actualKey = if (onKey == null) key else String.format("%s.%s", onKey, key)
//				// if the key exists in the other document AND both are BsonDocuments
//				// diff the documents recursively, carrying over the keys to keep
//				// updatedFields and removedFields flat.
//				// this will allow us to reference whole objects as well as nested
//				// properties.
//				// else if the key does not exist, the key has been removed.
//				if (afterDocument.containsKey(key)) {
//					val newValue: BsonValue = afterDocument.get(key)
//					if (oldValue is BsonDocument && newValue is BsonDocument) {
//						diff(
//							oldValue as BsonDocument,
//							newValue as BsonDocument,
//							actualKey,
//							updatedFields,
//							removedFields
//						)
//					} else if (!oldValue.equals(newValue)) {
//						updatedFields.put(actualKey, newValue)
//					}
//				} else {
//					removedFields.add(actualKey)
//				}
//			}
//
//			// for each key in the other document...
//			for ((key, newValue) in afterDocument.entrySet()) {
//				// don't worry about the _id or version field for now
//				if (key == "_id" || key == DOCUMENT_VERSION_FIELD) {
//					continue
//				}
//				// if the key is not in the this document,
//				// it is a new key with a new value.
//				// updatedFields will included keys that must
//				// be newly created.
//				val actualKey = if (onKey == null) key else String.format("%s.%s", onKey, key)
//				if (!beforeDocument.containsKey(key)) {
//					updatedFields.put(actualKey, newValue)
//				}
//			}
//			return UpdateDescription(updatedFields, removedFields)
//		}
//
//		/**
//		 * Find the diff between two documents.
//		 *
//		 *
//		 * NOTE: This does not do a full diff on [BsonArray]. If there is
//		 * an inequality between the old and new array, the old array will
//		 * simply be replaced by the new one.
//		 *
//		 * @param beforeDocument original document
//		 * @param afterDocument  document to diff on
//		 * @return a description of the updated fields and removed keys between the documents.
//		 */
//		fun diff(
//			@Nullable beforeDocument: BsonDocument?,
//			@Nullable afterDocument: BsonDocument?
//		): UpdateDescription {
//			return if (beforeDocument == null || afterDocument == null) {
//				UpdateDescription(
//					BsonDocument(),
//					java.util.HashSet<String>()
//				)
//			} else diff(
//				beforeDocument,
//				afterDocument,
//				null,
//				BsonDocument(),
//				java.util.HashSet<String>()
//			)
//		}
//	}
//}
