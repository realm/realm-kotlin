package io.realm.kotlin.test.mongodb.util

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AppConfigs {
    private fun buildFunction(name: String, function: String) = buildJsonObject {
        put("name", name)
        put("private", false)
        put("source", function)
    }

    val functions = listOf(
        buildFunction("forwardAsPatch", """
            exports = async function (url, body) {
                const response = await context.http.patch({
                    url: url,
                    body: body,
                    headers: context.request.requestHeaders,
                    encodeBodyAsJSON: true
                });
    
                return response;
            };
        """.trimIndent()),
        buildFunction("insertDocument", """
            exports = function (service, db, collection, document) {
                const mongodb = context.services.get(service);
                const result = mongodb
                    .db(db)
                    .collection(collection)
                    .insertOne(document);
            
                return result;
            };
        """.trimIndent()),
        buildFunction("deleteDocument", """
            exports = function (service, db, collection, query) {
                const mongodb = context.services.get(service);
                const result = mongodb
                    .db(db)
                    .collection(collection)
                    .deleteMany(EJSON.parse(query));
            
                return result;
            };
        """.trimIndent()),
        buildFunction("queryDocument", """
            exports = function (service, db, collection, query) {
                const mongodb = context.services.get(service);
                const result = mongodb
                    .db(db)
                    .collection(collection)
                    .findOne(EJSON.parse(query));
            
                return result;
            };
        """.trimIndent()),
        buildFunction("testAuthFunc", """
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
        """.trimIndent()),
        buildFunction("confirmFunc", """
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
        """.trimIndent()),
        buildFunction("resetFunc", """
            exports = ({ token, tokenId, username, password }, customParam1, customParam2) => {
                if (customParam1 != "say-the-magic-word" || customParam2 != 42) {
                    return { status: 'fail' };
                } else {
                    return { status: 'success' };
                }
            }
        """.trimIndent())
    )
    fun authProviders(functionIds: Map<String, String>) = listOf(
        """                
            {
                "name": "anon-user",
                "type": "anon-user",
                "disabled": false
            }
        """.trimIndent(), """                
            {
                "name": "custom-function",
                "type": "custom-function",
                "config": {
                    "authFunctionId": "${functionIds["testAuthFunc"]}",
                    "authFunctionName": "testAuthFunc"
                },
                "disabled": false
            }
        """.trimIndent(), """
            {
                "name": "local-userpass",
                "type": "local-userpass",
                "config": {
                    "autoConfirm": true,
                    "confirmationFunctionId": "${functionIds["confirmFunc"]}",
                    "confirmationFunctionName": "confirmFunc",
                    "emailConfirmationUrl": "http://realm.io/confirm-user",
                    "resetFunctionId": "${functionIds["resetFunc"]}",
                    "resetFunctionName": "resetFunc",
                    "resetPasswordSubject": "Reset Password",
                    "resetPasswordUrl": "http://realm.io/reset-password",
                    "runConfirmationFunction": false,
                    "runResetFunction": false
                },
                "disabled": false
            }
        """.trimIndent()
    )
}