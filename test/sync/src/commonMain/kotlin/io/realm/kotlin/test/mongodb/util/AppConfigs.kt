package io.realm.kotlin.test.mongodb.util

import kotlinx.serialization.Contextual
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@Serializable
data class AppFunction constructor(
    val name: String,
    val source: String,
    val private: Boolean = false,
)

@Serializable
data class AppAuthProvider constructor(
    val name: String,
    val type: String,
    val config: Map<String, JsonPrimitive?>? = null,
    val disabled: Boolean = false
)
//
// @Serializable
// sealed interface SyncConfig
//
// @Serializable
// class PartitionSync(
//     val db: String,
//     enabled: Boolean,
//     key: String,
//     type: String,
//     required: Boolean,
//     permissions: JsonObject
// ) : SyncConfig {
//     val sync = buildJsonObject {
//         put("state", if (enabled) "enabled" else "disabled")
//         put("database_name", db)
//         put("partition", buildJsonObject {
//             put("key", key)
//             put("type", type)
//             put("required", required)
//             put("permissions", permissions)
//         })
//     }
// }
// @Serializable
// class FlexibleSync(
//     db: String,
//     enabled: Boolean,
//     queryableFieldsName: List<String>,
//     permissions: JsonObject
// ) : SyncConfig {
//     @SerialName("flexible_sync")
//     val flexibleSync = buildJsonObject {
//         // put("state", if (enabled) "enabled" else "disabled")
//         // put("database_name", db)
//         putJsonArray("queryable_fields_names", buildJsonArray {
//             this.add("")
//         }
//         )
//         // put("permissions", permissions)
//     }
//
//     //"flexible_sync": {
//     //                     "state": "enabled",
//     //                     "database_name": "$dbName",
//     //                     "queryable_fields_names": ["name", "section"],
//     //                     "permissions": {
//     //                         "rules": {},
//     //                         "defaultRoles": [{
//     //                             "name": "all",
//     //                             "applyWhen": {},
//     //                             "read": true,
//     //                             "write": true
//     //                         }]
//     //                     }
//     //                 }
// }

object AppConfigs {
    val forwardAsPatch = AppFunction(
        name = "forwardAsPatch",
        source = """
                    exports = async function (url, body) {
                        const response = await context.http.patch({
                            url: url,
                            body: body,
                            headers: context.request.requestHeaders,
                            encodeBodyAsJSON: true
                        });
            
                        return response;
                    };
                """
    )

    val insertDocument = AppFunction(
        name = "insertDocument", source = """
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
    val deleteDocument = AppFunction(
        name = "deleteDocument", source = """
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
    val queryDocument = AppFunction(
        name = "queryDocument", source = """
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
    val testAuthFunc = AppFunction(
        name = "testAuthFunc", source = """
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
        """
    )
    val confirmFunc = AppFunction(
        name = "confirmFunc", source = """
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
        """
    )
    val resetFunc = AppFunction(
        name = "resetFunc", source = """
            exports = ({ token, tokenId, username, password }, customParam1, customParam2) => {
                if (customParam1 != "say-the-magic-word" || customParam2 != 42) {
                    return { status: 'fail' };
                } else {
                    return { status: 'success' };
                }
            }
        """.trimIndent()
    )

    val anonymousAuthProvider = AppAuthProvider(
        name = "anon-user",
        type = "anon-user"
    )

    fun customAuthProviderBuilder(
        confirmationFunction: Pair<String, String>
    ) = AppAuthProvider(
        name = "custom-function",
        type = "custom-function",
        config = mapOf(
            "authFunctionId" to JsonPrimitive(confirmationFunction.first),
            "authFunctionName" to JsonPrimitive(confirmationFunction.second)
        )
    )

    fun localUserAuthProviderBuilder(
        confirmationFunction: Pair<String, String>?,
        resetFunction: Pair<String, String>?,
    ) = AppAuthProvider(
        name = "local-userpass",
        type = "local-userpass",
        config = mapOf(
            "autoConfirm" to JsonPrimitive(true),
            "confirmationFunctionId" to JsonPrimitive(confirmationFunction?.first),
            "confirmationFunctionName" to JsonPrimitive(confirmationFunction?.second),
            "emailConfirmationUrl" to JsonPrimitive("http://realm.io/confirm-user"),
            "resetFunctionId" to JsonPrimitive(resetFunction?.first),
            "resetFunctionName" to JsonPrimitive(resetFunction?.second),
            "resetPasswordSubject" to JsonPrimitive("Reset Password"),
            "resetPasswordUrl" to JsonPrimitive("http://realm.io/reset-password"),
            "runConfirmationFunction" to JsonPrimitive((confirmationFunction != null)),
            "runResetFunction" to JsonPrimitive((resetFunction != null))
        )
    )
}