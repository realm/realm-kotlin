package io.realm.mongodb.functions

import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import java.lang.Class

/**
 * A <i>Functions</i> manager to call remote Realm functions for the associated Realm App.
 * <p>
 * Arguments and results are encoded/decoded with the <i>Functions'</i> codec registry either
 * inherited from the {@link AppConfiguration#getDefaultCodecRegistry()} or set explicitly
 * when creating the <i>Functions</i>-instance through {@link User#getFunctions(CodecRegistry)}
 * or through the individual calls to {@link #callFunction(String, List, Class, CodecRegistry)}.
 *
 * @see User#getFunctions()
 * @see User#getFunctions(CodecRegistry)
 * @see App#getFunctions(User)
 * @see App#getFunctions(User, CodecRegistry)
 * @see AppConfiguration
 * @see CodecRegistry
 */
interface Functions {

    /**
     * Returns the [io.realm.mongodb.User] that this instance in associated with.
     */
    val user: User

    /**
     * Returns the [io.realm.mongodb.App] that this instance in associated with.
     */
    val app: App

    /**
     * Returns the default codec registry used for encoding arguments and decoding results for this
     * *Realm functions* instance.
     *
     * @return The default codec registry.
     */
    val defaultCodecRegistry: CodecRegistry

    /**
     * Call a MongoDB Realm function synchronously with custom codec registry encoding/decoding
     * arguments/results.
     *
     * @param name Name of the Realm function to call.
     * @param args Arguments to the Realm function.
     * @param resultClass  The type that the functions result should be converted to.
     * @param codecRegistry Codec registry to use for argument encoding and result decoding.
     * @param <ResultT> The type that the response will be decoded as using the `codecRegistry`.
     * @return Result of the Realm function.
     *
     * @throws AppException if the request failed in some way.
     *
     * @see .callFunctionAsync
     * @see AppConfiguration.getDefaultCodecRegistry
    </ResultT> */
    suspend fun <ResultT> callFunction(
        name: String?,
        args: List<*>?,
        resultClass: Class<ResultT>?,
        codecRegistry: CodecRegistry?
    ): ResultT

    /**
     * Call a MongoDB Realm function synchronously with default codec registry encoding/decoding
     * arguments/results.
     *
     * @param name Name of the Realm function to call.
     * @param args Arguments to the Realm function.
     * @param resultClass  The type that the functions result should be converted to.
     * @param <ResultT> The type that the response will be decoded as using the default codec registry.
     * @return Result of the Realm function.
     *
     * @throws AppException if the request failed in some way.
     *
     * @see .callFunction
     * @see AppConfiguration.getDefaultCodecRegistry
    </ResultT> */
    suspend fun <ResultT> callFunction(
        name: String?,
        args: List<*>?,
        resultClass: Class<ResultT>?
    ): ResultT

    /**
     * Call a MongoDB Realm function synchronously with custom result decoder.
     *
     *
     * The arguments will be encoded with the default codec registry encoding.
     *
     * @param name Name of the Realm function to call.
     * @param args Arguments to the Realm function.
     * @param resultDecoder The decoder used to decode the result.
     * @param <ResultT> The type that the response will be decoded as using the `resultDecoder`
     * @return Result of the Realm function.
     *
     * @throws AppException if the request failed in some way.
     *
     * @see .callFunction
     * @see AppConfiguration.getDefaultCodecRegistry
    </ResultT> */
    suspend fun <ResultT> callFunction(
        name: String?,
        args: List<*>?,
        resultDecoder: Decoder<ResultT>?
    ): ResultT
}