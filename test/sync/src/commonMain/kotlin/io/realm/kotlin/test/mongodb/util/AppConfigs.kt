package io.realm.kotlin.test.mongodb.util

import io.realm.kotlin.test.mongodb.TESTAPP_FLEX
import io.realm.kotlin.test.mongodb.TESTAPP_PARTITION
import kotlinx.coroutines.delay

object AppConfigs {
    suspend fun BaasClient.initialize(app: BaasApp) {
        val databaseName = app.clientAppId
        when (app.name) {
            TESTAPP_PARTITION -> asTestAppPartition(app) { service: Service ->
                service.setSyncConfig(
                    """
                    {
                        "sync": {
                            "state": "enabled",
                            "database_name": "$databaseName",
                            "partition": {
                                "key": "realm_id",
                                "type": "string",
                                "permissions": {
                                    "read": true,
                                    "write": true
                                }
                            }
                        }
                    }
                    """.trimIndent()
                )

                app.addSchema(
                    """
                    {
                        "metadata": {
                            "data_source": "BackingDB",
                            "database": "$databaseName",
                            "collection": "SyncDog"
                        },
                        "schema": {
                            "properties": {
                                "_id": {
                                    "bsonType": "objectId"
                                },
                                "breed": {
                                    "bsonType": "string"
                                },
                                "name": {
                                    "bsonType": "string"
                                },
                                "realm_id": {
                                    "bsonType": "string"
                                }
                            },
                            "required": [
                                "name"
                            ],
                            "title": "SyncDog"
                        }
                    }
                    """.trimIndent()
                )

                app.addSchema(
                    """
                    {
                        "metadata": {
                            "data_source": "BackingDB",
                            "database": "$databaseName",
                            "collection": "SyncPerson"
                        },
                        "relationships": {
                            "dogs": {
                                "ref": "#/relationship/BackingDB/$databaseName/SyncDog",
                                "source_key": "dogs",
                                "foreign_key": "_id",
                                "is_list": true
                            }
                        },
                        "schema": {
                            "properties": {
                                "_id": {
                                    "bsonType": "objectId"
                                },
                                "age": {
                                    "bsonType": "int"
                                },
                                "dogs": {
                                    "bsonType": "array",
                                    "items": {
                                        "bsonType": "objectId"
                                    }
                                },
                                "firstName": {
                                    "bsonType": "string"
                                },
                                "lastName": {
                                    "bsonType": "string"
                                },
                                "realm_id": {
                                    "bsonType": "string"
                                }
                            },
                            "required": [
                                "firstName",
                                "lastName",
                                "age"
                            ],
                            "title": "SyncPerson"
                        }
                    }
                    """.trimIndent()
                )
            }
            TESTAPP_FLEX -> asTestAppPartition(app) { service: Service ->
                service.setSyncConfig(
                    """
                    {
                        "flexible_sync": {
                            "state": "enabled",
                            "database_name": "$databaseName",
                            "queryable_fields_names": [
                                "name",
                                "section"
                            ],
                            "permissions": {
                                "rules": {},
                                "defaultRoles": [
                                    {
                                        "name": "read-write",
                                        "applyWhen": {},
                                        "read": true,
                                        "write": true
                                    }
                                ]
                            }
                        }
                    }
                    """.trimIndent()
                )

                app.addSchema(
                    """
                    {
                        "metadata": {
                            "data_source": "BackingDB",
                            "database": "$databaseName",
                            "collection": "FlexChildObject"
                        },
                        "schema": {
                            "properties": {
                              "_id": {
                                "bsonType": "objectId"
                              },
                              "name": {
                                "bsonType": "string"
                              },
                              "realm_id": {
                                "bsonType": "string"
                              }
                            },
                            "required": [
                              "name",
                              "_id"
                            ],
                            "title": "FlexChildObject"
                        }
                    }
                    """.trimIndent()
                )

                app.addSchema(
                    """
                    {
                        "metadata": {
                            "data_source": "BackingDB",
                            "database": "$databaseName",
                            "collection": "FlexParentObject"
                        },
                        "relationships": {
                            "child": {
                                "ref": "#/relationship/BackingDB/$databaseName/FlexChildObject",
                                "source_key": "child",
                                "foreign_key": "_id",
                                "is_list": false
                            }
                        },
                        "schema": {
                            "properties": {
                                "_id": {
                                  "bsonType": "objectId"
                                },
                                "age": {
                                  "bsonType": "int"
                                },
                                "child": {
                                  "bsonType": "objectId"
                                },
                                "name": {
                                  "bsonType": "string"
                                },
                                "realm_id": {
                                  "bsonType": "string"
                                },
                                "section": {
                                  "bsonType": "int"
                                }
                            },
                            "required": [
                                "name",
                                "section",
                                "age",
                                "_id"
                            ],
                            "title": "FlexParentObject"
                        
                        }
                    }
                    """.trimIndent()
                )
            }
        }

        delay(5000)
    }

    // Enables forward as patch functionality as a HTTPS endpoint on the baas app.
    private suspend fun BaasClient.enableForwardAsPatch(app: BaasApp) = with(app) {
        addFunction(forwardAsPatch).let { function: Function ->
            addEndpoint(
                """
                    {
                      "route": "/forwardAsPatch",
                      "function_name": "${function.name}",
                      "function_id": "${function._id}",
                      "http_method": "POST",
                      "validation_method": "NO_VALIDATION",
                      "secret_id": "",
                      "secret_name": "",
                      "create_user_on_auth": false,
                      "fetch_custom_user_data": false,
                      "respond_result": false,
                      "disabled": false,
                      "return_type": "JSON"
                    }       
            """.trimIndent()
            )
        }
    }

    private suspend fun BaasClient.asTestAppPartition(
        app: BaasApp,
        block: suspend BaasClient.(service: Service) -> Unit
    ) = with(app) {
        enableForwardAsPatch(app)

        val confirmFuncId = addFunction(confirmFunc)._id
        val resetFuncId = addFunction(resetFunc)._id

        addFunction(insertDocument)
        addFunction(queryDocument)
        addFunction(deleteDocument)

        addAuthProvider(
            """
            {
                "type": "local-userpass",
                "config": {
                    "autoConfirm": true,
                    "confirmationFunctionId": "$confirmFuncId",
                    "confirmationFunctionName": "${confirmFunc.name}",
                    "emailConfirmationUrl": "http://realm.io/confirm-user",
                    "resetFunctionId": "$resetFuncId",
                    "resetFunctionName": "${resetFunc.name}",
                    "resetPasswordSubject": "Reset Password",
                    "resetPasswordUrl": "http://realm.io/reset-password",
                    "runConfirmationFunction": false,
                    "runResetFunction": false
                }
            }
        """.trimIndent()
        )

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

        addAuthProvider(
            """
            {
                "type": "anon-user"
            }
        """.trimIndent()
        )

        // Enable 'API-KEY' by updating it. It exists by default in the server so we cannot add.
        getAuthProvider("api-key").run {
            enable(true)
        }

        addService(
            """
            {
                "name": "BackingDB",
                "type": "mongodb",
                "config": { "uri": "mongodb://localhost:26000" }
            }
        """.trimIndent()
        ).let {
            block(it)
        }

        setDevelopmentMode(true)
    }

    private val forwardAsPatch = Function(
        name = "forwardAsPatch",
        runAsSystem = true,
        source = """
                    exports = async function (request, response) {
                        try {
                          if(request.body === undefined) {
                            throw new Error(`Request body was not defined.`)
                          }
                          const forwardRequest = JSON.parse(request.body.text());
                          
                          const forwardResponse = await context.http.patch({
                            url: forwardRequest.url,
                            body: JSON.parse(forwardRequest.body),
                            headers: context.request.requestHeaders,
                            encodeBodyAsJSON: true
                          });
                          
                          response.setStatusCode(forwardResponse.statusCode);
                      } catch (error) {
                        response.setStatusCode(400);
                        response.setBody(error.message);
                      }
                    };
                """.trimIndent()
    )

    private val insertDocument = Function(
        name = "insertDocument",
        source = """
            exports = function (service, db, collection, document) {
                const mongodb = context.services.get(service);
                const result = mongodb
                    .db(db)
                    .collection(collection)
                    .insertOne(document);
            
                return result;
            };
        """
    )

    private val deleteDocument = Function(
        name = "deleteDocument",
        source = """
            exports = function (service, db, collection, query) {
                const mongodb = context.services.get(service);
                const result = mongodb
                    .db(db)
                    .collection(collection)
                    .deleteMany(EJSON.parse(query));
            
                return result;
            };
        """
    )

    private val queryDocument = Function(
        name = "queryDocument",
        source = """
            exports = function (service, db, collection, query) {
                const mongodb = context.services.get(service);
                const result = mongodb
                    .db(db)
                    .collection(collection)
                    .findOne(EJSON.parse(query));
            
                return result;
            };
        """
    )

    private val testAuthFunc = Function(
        name = "testAuthFunc",
        source = """
            exports = ({mail, id}) => {
                // Auth function will fail for emails with a domain different to @androidtest.realm.io
                // or with id lower than 666
                if (!new RegExp("@androidtest.realm.io${'$'}").test(mail) || id < 666) {
                    return 0;
                } else {
                    // Use the users email as UID
                    return mail;
                }
            }
        """.trimIndent()
    )

    private val confirmFunc = Function(
        name = "confirmFunc",
        source = """
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
                } else {
                  // All other emails should fail to confirm outright.
                  return { status: 'fail' };
                }
              };
        """.trimIndent()
    )

    private val resetFunc = Function(
        name = "resetFunc",
        source = """
            exports = ({ token, tokenId, username, password }, customParam1, customParam2) => {
                if (customParam1 != "say-the-magic-word" || customParam2 != 42) {
                    return { status: 'fail' };
                } else {
                    return { status: 'success' };
                }
            }
        """.trimIndent()
    )
}
