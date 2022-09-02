package io.realm.kotlin.test.mongodb.util

import io.ktor.http.HttpMethod

object KtorAppTester {

    // Setups the app with the functions and https endpoints required to run the KtorNetworkTransportTests
    suspend fun BaasClient.initialize(app: BaasApp, methods: List<HttpMethod>) =
        with(app) {
            methods.forEach { httpMethod: HttpMethod ->
                val method = httpMethod.value
                val function = addFunction(
                    Function(
                        name = "test_network_transport_$method",
                        runAsSystem = true,
                        source = """
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
