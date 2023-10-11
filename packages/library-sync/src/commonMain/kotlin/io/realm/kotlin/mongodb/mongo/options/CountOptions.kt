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
 * The options for a count operation.
 */
public data class CountOptions(
	/**
	 * The limit to apply. The default is 0, which means there is no limit.
	 */
	val limit: Int = 0
) {
	override fun toString(): String {
		return """
			RemoteCountOptions{limit=$limit}
			"""
	}
}
