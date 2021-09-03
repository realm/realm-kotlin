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
package io.realm.mongodb

import io.realm.annotations.Beta
import java.lang.RuntimeException
import java.lang.StringBuilder

/**
 * This class is a wrapper for all errors happening when communicating with a MongoDB Realm app.
 * This include both exceptions and protocol errors.
 *
 * Only [AppException.errorCode] is guaranteed to contain a value. If the error was caused by an
 * underlying exception [AppException.errorMessage] is `null` and [AppException.exception] is set,
 * while if the error was a protocol error [AppException.errorMessage] is set and
 * [AppException.exception] is `null`.
 *
 * @see io.realm.mongodb.ErrorCode for a list of possible errors.
 */
class AppException(
    /**
     * Returns the [ErrorCode] identifying the type of error.
     *
     * If [ErrorCode.UNKNOWN] is returned, it means that the error could not be mapped to any
     * known errors. In that case [.getErrorType] and [.getErrorIntValue] will
     * return the underlying error information which can better identify the type of error.
     *
     * @return the error code identifying the type of error.
     * @see ErrorCode
     */
    // The Java representation of the error.
    val errorCode: ErrorCode,
    /**
     * Returns a string describing the type of error it is.
     *
     * @return
     */
    // The native error representation. Mostly relevant for ErrorCode.UNKNOWN
    // where it can provide more details into the exact error.
    val errorType: String?,
    /**
     * Returns an integer representing this specific type of error. This value is only unique within
     * the value provided by [.getErrorType].
     *
     * @return the integer value representing this type of error.
     */
    val errorIntValue: Int,
    /**
     * Returns a more detailed error message about the cause of this error.
     *
     * @return a detailed error message or `null` if one was not available.
     */
    @get:Nullable
    @param:Nullable val errorMessage: String?,
    /**
     * Returns the underlying exception causing this error, if any.
     *
     * @return the underlying exception causing this error, or `null` if not caused by an exception.
     */
    @get:Nullable
    @param:Nullable val exception: Throwable?
) : RuntimeException(
    errorMessage
) {

    /**
     * Create an error caused by an error in the protocol when communicating with the Object Server.
     *
     * @param errorCode error code for this type of error.
     * @param errorMessage detailed error message.
     */
    constructor(errorCode: ErrorCode, errorMessage: String?) : this(
        errorCode,
        errorCode.type,
        errorCode.intValue(),
        errorMessage,
        null as Throwable?
    ) {
    }

    /**
     * Creates an unknown error that could not be mapped to any known error case.
     *
     *
     * This means that [.getErrorCode] will return [ErrorCode.UNKNOWN], but
     * [.getErrorType] and [.getErrorIntValue] will return the underlying values
     * which can help identify the real error.
     *
     * @param errorCode error code for this type of error.
     * @param errorMessage detailed error message.
     */
    constructor(errorType: String?, errorCode: Int, errorMessage: String?) : this(
        ErrorCode.UNKNOWN,
        errorType,
        errorCode,
        errorMessage,
        null
    ) {
    }

    /**
     * Create an error caused by an an exception when communicating with the Object Server.
     *
     * @param errorCode error code for this type of error.
     * @param exception underlying exception causing this error.
     */
    constructor(errorCode: ErrorCode, exception: Throwable?) : this(errorCode, null, exception) {}

    /**
     * Errors happening while trying to authenticate a user.
     *
     * @param errorCode error code for this type of error.
     * @param title title for this type of error.
     * @param hint a hint for resolving the error.
     */
    constructor(errorCode: ErrorCode, title: String, @Nullable hint: String?) : this(
        errorCode,
        if (hint != null) "$title : $hint" else title,
        null as Throwable?
    ) {
    }

    /**
     * Generic error happening that could happen anywhere.
     *
     * @param errorCode error code for this type of error.
     * @param errorMessage detailed error message.
     * @param exception underlying exception if the error was caused by this.
     */
    constructor(
        errorCode: ErrorCode,
        @Nullable errorMessage: String?,
        @Nullable exception: Throwable?
    ) : this(errorCode, errorCode.type, errorCode.intValue(), errorMessage, exception) {
    }

    /**
     * Returns the [ErrorCode.Category] category for this error.
     * Errors that are [ErrorCode.Category.RECOVERABLE] mean that it is still possible for a
     * given [SyncSession] to resume synchronization. [ErrorCode.Category.FATAL] errors
     * means that session has stopped and cannot be recovered.
     *
     * @return the error category.
     */
    val category: ErrorCode.Category
        get() = errorCode.category

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(errorCode.name)
        sb.append("(")
        sb.append(errorType)
        sb.append(":")
        sb.append(errorIntValue)
        sb.append(')')
        if (errorMessage != null) {
            sb.append(": ")
            sb.append(errorMessage)
        }
        if (exception != null) {
            sb.append('\n')
            sb.append(Util.getStackTrace(exception))
        }
        return sb.toString()
    }
}