/*
 * Copyright 2022 Realm Inc.
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
package io.realm.kotlin.test.mongodb.util

import io.realm.kotlin.test.mongodb.TEST_APP_CLUSTER_NAME
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TEST_APP_PARTITION
import io.realm.kotlin.test.mongodb.common.FLEXIBLE_SYNC_SCHEMA
import io.realm.kotlin.test.mongodb.common.PARTITION_BASED_SCHEMA
import kotlinx.serialization.json.Json

interface AppInitializer {
    val name: String
    suspend fun initialize(client: AppServicesClient, app: BaasApp)
}

open class BaseAppInitializer(
    override val name: String,
    val block: (suspend AppServicesClient.(app: BaasApp) -> Unit)? = null,
) : AppInitializer {

    override suspend fun initialize(client: AppServicesClient, app: BaasApp) {
        with(client) {
            with(app) {
                addFunction(insertDocument)
                addFunction(queryDocument)
                addFunction(deleteDocument)
                addFunction(countDocuments)

                val testAuthFuncId = addFunction(testAuthFunc)._id
                addAuthProvider(
                    """
                        {
                            "type": "custom-function",
                            "config": {
                                "authFunctionId": "$testAuthFuncId",
                                "authFunctionName": "${testAuthFunc.name}"
                            }
                        }
                    """.trimIndent()
                )

                addAuthProvider("""{"type": "anon-user"}""")

                // Enable 'API-KEY' by updating it. It exists by default in the server so we cannot add.
                getAuthProvider("api-key").run {
                    enable(true)
                }

                val (type: String, config: String) = if (TEST_APP_CLUSTER_NAME.isEmpty()) {
                    "mongodb" to """{ "uri": "mongodb://localhost:26000" }"""
                } else {
                    "mongodb-atlas" to """{ "clusterName": "$TEST_APP_CLUSTER_NAME" }"""
                }
                addService(
                    """
                        {
                            "name": "BackingDB",
                            "type": "$type",
                            "config": $config
                        }
                    """.trimIndent()
                ).let { service: Service ->
                    val dbName = app.clientAppId
                    service.addDefaultRule(
                        """
                            {
                                "roles": [{
                                    "name": "defaultRole",
                                    "apply_when": {},
                                    "document_filters": {
                                        "read": true,
                                        "write": true
                                    },
                                    "write": true,
                                    "read": true,
                                    "insert": true,
                                    "delete": true
                                }]
                            }
                        """.trimIndent()
                    )

                    app.setCustomUserData(
                        """
                            {
                                "mongo_service_id": ${service._id},
                                "enabled": true,
                                "database_name": "$dbName",
                                "collection_name": "UserData",
                                "user_id_field": "user_id"
                            }
                        """.trimIndent()
                    )
                }
                with(client) {
                    block?.invoke(this, app)
                }
            }
        }
    }
}

object DefaultPartitionBasedAppInitializer :
    BaseAppInitializer(
        TEST_APP_PARTITION,
        { app ->
            addEmailProvider(app)
            initializePartitionSync(app)
        }
    )

object DefaultFlexibleSyncAppInitializer :
    BaseAppInitializer(
        TEST_APP_FLEX,
        { app ->
            addEmailProvider(app)
            initializeFlexibleSync(app)
        }
    )

@Suppress("LongMethod")
suspend fun AppServicesClient.initializeFlexibleSync(
    app: BaasApp,
    recoveryDisabled: Boolean = false, // TODO
) {
    val databaseName = app.clientAppId

    app.setSchema(FLEXIBLE_SYNC_SCHEMA)

    app.mongodbService.setSyncConfig(
        """
            {
                "flexible_sync": {
                    "state": "enabled",
                    "database_name": "$databaseName",
                    "is_recovery_mode_disabled": $recoveryDisabled,
                    "queryable_fields_names": [
                        "name",
                        "section",
                        "stringField",
                        "location",
                        "selector"
                    ],
                    "asymmetric_tables": [
                        "AsymmetricA",
                        "Measurement"
                    ]
                }
            }
        """.trimIndent()
    )
    app.waitUntilInitialSyncCompletes()
}

@Suppress("LongMethod")
suspend fun AppServicesClient.initializePartitionSync(
    app: BaasApp,
    recoveryDisabled: Boolean = false, // TODO
) {
    val databaseName = app.clientAppId

    app.addFunction(canReadPartition)
    app.addFunction(canWritePartition)

    app.setSchema(
        schema = PARTITION_BASED_SCHEMA,
        extraProperties = mapOf(
            "realm_id" to PrimitivePropertyType.Type.STRING
        )
    )

    app.mongodbService.setSyncConfig(
        """
            {
                "sync": {
                    "state": "enabled",
                    "database_name": "$databaseName",
                    "is_recovery_mode_disabled": $recoveryDisabled,
                    "partition": {
                        "key": "realm_id",
                        "type": "string",
                        "permissions": {
                            "read": {
                                "%%true": {
                                    "%function": {
                                        "arguments": [
                                            "%%partition"
                                        ],
                                        "name": "canReadPartition"
                                    }
                                }
                            },
                            "write": {
                                "%%true": {
                                    "%function": {
                                        "arguments": [
                                            "%%partition"
                                        ],
                                        "name": "canWritePartition"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
    )

    app.waitUntilInitialSyncCompletes()
}

suspend fun AppServicesClient.addEmailProvider(
    app: BaasApp,
    autoConfirm: Boolean = true,
    runConfirmationFunction: Boolean = false,
) = with(app) {
    val confirmFuncId = addFunction(confirmFunc)._id
    val resetFuncId = addFunction(resetFunc)._id

    addAuthProvider(
        """
            {
                "type": "local-userpass",
                "config": {
                    "autoConfirm": $autoConfirm,
                    "confirmationFunctionId": "$confirmFuncId",
                    "confirmationFunctionName": "${confirmFunc.name}",
                    "emailConfirmationUrl": "http://realm.io/confirm-user",
                    "resetFunctionId": "$resetFuncId",
                    "resetFunctionName": "${resetFunc.name}",
                    "resetPasswordSubject": "Reset Password",
                    "resetPasswordUrl": "http://realm.io/reset-password",
                    "runConfirmationFunction": $runConfirmationFunction,
                    "runResetFunction": false
                }
            }
        """.trimIndent()
    )
}

private val insertDocument = Function(
    name = "insertDocument",
    source =
    """
        exports = function (service, db, collection, document) {
            const mongodb = context.services.get(service);
            const result = mongodb
                .db(db)
                .collection(collection)
                .insertOne(document);
        
            return result;
        }
        
    """.trimIndent()
)

private val deleteDocument = Function(
    name = "deleteDocument",
    source =
    """
        exports = function (service, db, collection, query) {
            const mongodb = context.services.get(service);
            const result = mongodb
                .db(db)
                .collection(collection)
                .deleteMany(EJSON.parse(query));
        
            return result;
        }
        
    """.trimIndent()
)

private val queryDocument = Function(
    name = "queryDocument",
    source =
    """
        exports = function (service, db, collection, query) {
            const mongodb = context.services.get(service);
            const result = mongodb
                .db(db)
                .collection(collection)
                .findOne(EJSON.parse(query));
        
            return result;
        }
    """.trimIndent()
)

private val countDocuments = Function(
    name = "countDocuments",
    source =
    """
        exports = function (service, db, collection) {
            const mongodb = context.services.get(service);
            const result = mongodb
                .db(db)
                .collection(collection)
                .count();
            return { value: result };
        }
        
    """.trimIndent()
)

private val testAuthFunc = Function(
    name = "testAuthFunc",
    source =
    """
        exports = ({mail, id}) => {
            // Auth function will fail for emails with a domain different to @10gen.com
            // or with id lower than 666
            if (!new RegExp("@10gen.com${'$'}").test(mail) || id < 666) {
                throw new Error(`Authentication failed`);
            } else {
                // Use the users email as UID
                return mail;
            }
        }
    """.trimIndent()
)

private val confirmFunc = Function(
    name = "confirmFunc",
    source =
    """
        exports = async ({ token, tokenId, username }) => {
          // process the confirm token, tokenId and username
          
          if (username.includes("realm_verify")) {
            // Automatically confirm users with `realm_verify` in their email.
            return { status: 'success' }
          } else if (username.includes("realm_pending")) {
            // Emails with `realm_pending` in their email will be placed in Pending
            // the first time they register and will then be fully confirmed when
            // they retry their confirmation logic.
            const mdb = context.services.get("BackingDB");
            const collection = mdb.db("custom-auth").collection("users");
            const existing = await collection.findOne({ username: username });
            if (existing) {
                return { status: 'success' };
            }
            await collection.insertOne({ username: username });
            return { status: 'pending' }
          } else if (username.endsWith("@10gen.com")) {
            return { status: 'success' }
          } else {
            // All other emails should fail to confirm outright.
            return { status: 'fail' };
          }
        }
    """.trimIndent()
)

private val resetFunc = Function(
    name = "resetFunc",
    source =
    """
        exports = ({ token, tokenId, username, password }, customParam1, customParam2) => {
            if (customParam1 != "say-the-magic-word" || customParam2 != 42) {
                return { status: 'fail' };
            } else {
                return { status: 'success' };
            }
        }
    """.trimIndent()
)

private val canReadPartition = Function(
    name = "canReadPartition",
    source =
    """
        /**
         * Users with an email that contains `_noread_` do not have read access,
         * all others do.
         */
        exports = async (partition) => {
          const email = context.user.data.email;
          if (email != undefined) {
            return !email.includes("_noread_");
          } else {
            return true;
          }
        }
    """.trimIndent()
)

private val canWritePartition =
    Function(
        name = "canWritePartition",
        source =
        """
            /**
             * Users with an email that contains `_nowrite_` do not have write access,
             * all others do.
             */
            exports = async (partition) => {
              const email = context.user.data.email;
              if (email != undefined) {
                return(!email.includes("_nowrite_"));
              } else {
                return true;
              }  
            }
        """.trimIndent()
    )

val FIRST_ARG_FUNCTION = Function(
    name = "firstArg",
    source =
    """
        exports = function(arg){
          // Returns first argument
          return arg
        };
    """.trimIndent()
)

val SUM_FUNCTION = Function(
    name = "sum",
    source =
    """
        exports = function(...args) {
            return parseInt(args.reduce((a,b) => a + b, 0));
        };
    """.trimIndent()
)

val NULL_FUNCTION = Function(
    name = "null",
    source =
    """
        exports = function(arg){
          return null;
        };
    """.trimIndent()
)

val ERROR_FUNCTION = Function(
    name = "error",
    source =
    """
        exports = function(arg){
          return unknown;
        };
    """.trimIndent()
)

val VOID_FUNCTION = Function(
    name = "void",
    source =
    """
        exports = function(arg){
          return void(0);
        };
    """.trimIndent()
)

val AUTHORIZED_ONLY_FUNCTION = Function(
    name = "authorizedOnly",
    source =
    """
        exports = function(arg){
          /*
            Accessing application's values:
            var x = context.values.get("value_name");
        
            Accessing a mongodb service:
            var collection = context.services.get("mongodb-atlas").db("dbname").collection("coll_name");
            var doc = collection.findOne({owner_id: context.user.id});
        
            To call other named functions:
            var result = context.functions.execute("function_name", arg1, arg2);
        
            Try running in the console below.
          */
          return {arg: context.user};
        };
    """.trimIndent(),
    canEvaluate = Json.decodeFromString(
        """
            {
                "%%user.data.email": {
                    "%in": [
                        "authorizeduser@example.org"
                    ]
                }
            }
        """.trimIndent()
    )
)
