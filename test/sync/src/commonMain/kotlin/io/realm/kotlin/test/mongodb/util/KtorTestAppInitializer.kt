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

import io.ktor.http.HttpMethod

object KtorTestAppInitializer {

    // Setups the app with the functions and https endpoints required to run the KtorNetworkTransportTests
    suspend fun AppServicesClient.initialize(app: BaasApp, methods: List<HttpMethod>) =
        with(app) {
            methods.forEach { httpMethod: HttpMethod ->
                val method = httpMethod.value
                val function = addFunction(
                    Function(
                        name = "test_network_transport_$method",
                        runAsSystem = true,
                        source =
                        """
                        exports = async function (request, response) {
                            response.setHeader('Content-Type', 'text/plain');
                            let isSuccess = request.query["success"] == "true";
                        
                            if (isSuccess) {
                                response.setStatusCode(200);
                                response.setBody("$method-success");
                            } else {
                                response.setStatusCode(500);
                                response.setBody("$method-failure");
                            }
                        }
                        """.trimIndent()
                    )
                )

                addEndpoint(
                    """
                    {
                      "route": "/test_network_transport",
                      "function_name": "${function.name}",
                      "function_id": "${function._id}",
                      "http_method": "$method",
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
}
