package io.realm.test.mongodb.shared

import io.realm.mongodb.AppException
import io.realm.mongodb.Credentials
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import io.realm.test.util.TestHelper
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.fail

class EmailPasswordAuthTests {

    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp()
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    @Test
    fun registerUser() {
        runBlocking {
            val email = TestHelper.randomEmail()
            val password = "password1234"
            app.emailPasswordAuth.registerUser(email, password)
            val credentials = Credentials.emailPassword(email, password)
            val user = app.login(credentials)
            assertNotNull(user)
        }
    }

    @Test
    fun registerUser_invalidServerArgsThrows() {
        runBlocking {
            val email = "invalid-email"
            val password = "1234"

            // TODO do exhaustive exception assertion once we have all AppException fields in place
            assertFailsWith<AppException> {
                app.emailPasswordAuth.registerUser(email, password)
                fail("Should never reach this.")
            }
        }
    }

//    @Ignore("Find a way to automate this")
//    @Test
//    fun confirmUser() {
//        TODO("Figure out how to manually test this")
//    }
//
//    @Ignore("Find a way to automate this")
//    @Test
//    fun confirmUserAsync() {
//        TODO("Figure out how to manually test this")
//    }
//
//    @Test
//    fun confirmUser_invalidServerArgsThrows() {
//        val provider = app.emailPassword
//        try {
//            provider.confirmUser("invalid-token", "invalid-token-id")
//            Assert.fail()
//        } catch (ex: AppException) {
//            assertEquals(ErrorCode.BAD_REQUEST, ex.errorCode)
//        }
//    }
//
//    @Test
//    fun confirmUserAsync_invalidServerArgsThrows() {
//        val provider = app.emailPassword
//        looperThread.runBlocking {
//            provider.confirmUserAsync("invalid-email", "1234") { result ->
//                if (result.isSuccess) {
//                    Assert.fail()
//                } else {
//                    assertEquals(ErrorCode.BAD_REQUEST, result.error.errorCode)
//                    looperThread.testComplete()
//                }
//            }
//        }
//    }
//
//    @Test
//    fun confirmUser_invalidArgumentsThrows() {
//        val provider: EmailPasswordAuth = app.emailPassword
//        assertFailsWith<IllegalArgumentException> { provider.confirmUser(TestHelper.getNull(), "token-id") }
//        assertFailsWith<IllegalArgumentException> { provider.confirmUser("token", TestHelper.getNull()) }
//        looperThread.runBlocking {
//            provider.confirmUserAsync(TestHelper.getNull(), "token-id", checkNullArgCallback)
//        }
//        looperThread.runBlocking {
//            provider.confirmUserAsync("token", TestHelper.getNull(), checkNullArgCallback)
//        }
//    }
//
//    @Test
//    fun resendConfirmationEmail() {
//        // We only test that the server successfully accepts the request. We have no way of knowing
//        // if the Email was actually sent.
//        // FIXME Figure out a way to check if this actually happened. Perhaps a custom SMTP server?
//        val email = "test@10gen.com"
//        admin.setAutomaticConfirmation(false)
//        try {
//            val provider = app.emailPassword
//            provider.registerUser(email, "123456")
//            provider.resendConfirmationEmail(email)
//        } finally {
//            admin.setAutomaticConfirmation(true)
//        }
//    }
//
//    @Test
//    fun resendConfirmationEmailAsync() {
//        // We only test that the server successfully accepts the request. We have no way of knowing
//        // if the Email was actually sent.
//        val email = "test@10gen.com"
//        admin.setAutomaticConfirmation(false)
//        try {
//            looperThread.runBlocking {
//                val provider = app.emailPassword
//                provider.registerUser(email, "123456")
//                provider.resendConfirmationEmailAsync(email) { result ->
//                    when (result.isSuccess) {
//                        true -> looperThread.testComplete()
//                        false -> Assert.fail(result.error.toString())
//                    }
//                }
//            }
//        } finally {
//            admin.setAutomaticConfirmation(true)
//        }
//    }
//
//    @Test
//    fun resendConfirmationEmail_invalidServerArgsThrows() {
//        val email = "test@10gen.com"
//        admin.setAutomaticConfirmation(false)
//        val provider = app.emailPassword
//        provider.registerUser(email, "123456")
//        try {
//            provider.resendConfirmationEmail("foo")
//            Assert.fail()
//        } catch (error: AppException) {
//            assertEquals(ErrorCode.USER_NOT_FOUND, error.errorCode)
//        } finally {
//            admin.setAutomaticConfirmation(true)
//        }
//    }
//
//    @Test
//    fun resendConfirmationEmailAsync_invalidServerArgsThrows() {
//        val email = "test@10gen.com"
//        admin.setAutomaticConfirmation(false)
//        val provider = app.emailPassword
//        provider.registerUser(email, "123456")
//        try {
//            looperThread.runBlocking {
//                provider.resendConfirmationEmailAsync("foo") { result ->
//                    if (result.isSuccess) {
//                        Assert.fail()
//                    } else {
//                        assertEquals(ErrorCode.USER_NOT_FOUND, result.error.errorCode)
//                        looperThread.testComplete()
//                    }
//                }
//            }
//        } finally {
//            admin.setAutomaticConfirmation(true)
//        }
//    }
//
//    @Test
//    fun resendConfirmationEmail_invalidArgumentsThrows() {
//        val provider: EmailPasswordAuth = app.emailPassword
//        assertFailsWith<IllegalArgumentException> { provider.resendConfirmationEmail(TestHelper.getNull()) }
//        looperThread.runBlocking {
//            provider.resendConfirmationEmailAsync(TestHelper.getNull(), checkNullArgCallback)
//        }
//    }
//
//    @Test
//    fun retryCustomConfirmation() {
//        val email = "test_realm_tests_do_autoverify@10gen.com"
//        admin.setAutomaticConfirmation(false)
//        try {
//            val provider = app.emailPassword
//            provider.registerUser(email, "123456")
//            admin.setCustomConfirmation(true)
//
//            provider.retryCustomConfirmation(email)
//        } finally {
//            admin.setCustomConfirmation(false)
//        }
//    }
//
//    @Test
//    fun retryCustomConfirmation_failConfirmation() {
//        // Only emails containing realm_tests_do_autoverify will be confirmed
//        val email = "test@10gen.com"
//        admin.setAutomaticConfirmation(false)
//        try {
//            val provider = app.emailPassword
//            provider.registerUser(email, "123456")
//            admin.setCustomConfirmation(true)
//
//            val exception = assertFailsWith<AppException> {
//                provider.retryCustomConfirmation(email)
//            }
//
//            Assert.assertEquals("failed to confirm user test@10gen.com", exception.errorMessage)
//
//        } finally {
//            admin.setCustomConfirmation(false)
//        }
//    }
//
//    @Test
//    fun retryCustomConfirmationAsync() {
//        val email = "test_realm_tests_do_autoverify@10gen.com"
//        admin.setAutomaticConfirmation(false)
//        try {
//            looperThread.runBlocking {
//                val provider = app.emailPassword
//                provider.registerUser(email, "123456")
//                admin.setCustomConfirmation(true)
//
//                provider.retryCustomConfirmationAsync(email) { result ->
//                    when (result.isSuccess) {
//                        true -> looperThread.testComplete()
//                        false -> Assert.fail(result.error.toString())
//                    }
//                }
//            }
//        } finally {
//            admin.setCustomConfirmation(false)
//        }
//    }
//
//    @Test
//    fun retryCustomConfirmation_invalidServerArgsThrows() {
//        val email = "test@10gen.com"
//        admin.setAutomaticConfirmation(false)
//        val provider = app.emailPassword
//        provider.registerUser(email, "123456")
//        admin.setCustomConfirmation(true)
//
//        try {
//            provider.retryCustomConfirmation("foo")
//            Assert.fail()
//        } catch (error: AppException) {
//            assertEquals(ErrorCode.USER_NOT_FOUND, error.errorCode)
//        } finally {
//            admin.setCustomConfirmation(false)
//        }
//    }
//
//    @Test
//    fun retryCustomConfirmationAsync_invalidServerArgsThrows() {
//        val email = "test@10gen.com"
//        admin.setAutomaticConfirmation(false)
//        val provider = app.emailPassword
//        provider.registerUser(email, "123456")
//        admin.setCustomConfirmation(true)
//        try {
//            looperThread.runBlocking {
//                provider.retryCustomConfirmationAsync("foo") { result ->
//                    if (result.isSuccess) {
//                        Assert.fail()
//                    } else {
//                        assertEquals(ErrorCode.USER_NOT_FOUND, result.error.errorCode)
//                        looperThread.testComplete()
//                    }
//                }
//            }
//        } finally {
//            admin.setCustomConfirmation(false)
//        }
//    }
//
//    @Test
//    fun retryCustomConfirmation_invalidArgumentsThrows() {
//        val provider: EmailPasswordAuth = app.emailPassword
//        assertFailsWith<IllegalArgumentException> { provider.retryCustomConfirmation(TestHelper.getNull()) }
//        looperThread.runBlocking {
//            provider.retryCustomConfirmationAsync(TestHelper.getNull(), checkNullArgCallback)
//        }
//    }
//
//    @Test
//    fun sendResetPasswordEmail() {
//        val provider = app.emailPassword
//        val email: String = "test@10gen.com" // Must be a valid email, otherwise the server will fail
//        provider.registerUser(email, "123456")
//        provider.sendResetPasswordEmail(email)
//    }
//
//    @Test
//    fun sendResetPasswordEmailAsync() {
//        val provider = app.emailPassword
//        val email: String = "test@10gen.com" // Must be a valid email, otherwise the server will fail
//        provider.registerUser(email, "123456")
//        looperThread.runBlocking {
//            provider.sendResetPasswordEmailAsync(email) { result ->
//                when (result.isSuccess) {
//                    true -> looperThread.testComplete()
//                    false -> Assert.fail(result.error.toString())
//                }
//
//            }
//        }
//    }
//
//    @Test
//    fun sendResetPasswordEmail_invalidServerArgsThrows() {
//        val provider = app.emailPassword
//        try {
//            provider.sendResetPasswordEmail("unknown@10gen.com")
//            Assert.fail()
//        } catch (error: AppException) {
//            assertEquals(ErrorCode.USER_NOT_FOUND, error.errorCode)
//        }
//    }
//
//    @Test
//    fun sendResetPasswordEmailAsync_invalidServerArgsThrows() {
//        val provider = app.emailPassword
//        looperThread.runBlocking {
//            provider.sendResetPasswordEmailAsync("unknown@10gen.com") { result ->
//                if (result.isSuccess) {
//                    Assert.fail()
//                } else {
//                    assertEquals(ErrorCode.USER_NOT_FOUND, result.error.errorCode)
//                    looperThread.testComplete()
//                }
//            }
//        }
//    }
//
//    @Test
//    fun sendResetPasswordEmail_invalidArgumentsThrows() {
//        val provider = app.emailPassword
//        assertFailsWith<IllegalArgumentException> { provider.sendResetPasswordEmail(TestHelper.getNull()) }
//        looperThread.runBlocking {
//            provider.sendResetPasswordEmailAsync(TestHelper.getNull(), checkNullArgCallback)
//        }
//    }
//
//    @Test
//    fun callResetPasswordFunction() {
//        val provider = app.emailPassword
//        admin.setResetFunction(enabled = true)
//        val email = TestHelper.getRandomEmail()
//        provider.registerUser(email, "123456")
//        try {
//            provider.callResetPasswordFunction(email, "new-password", "say-the-magic-word", 42)
//            val user = app.login(Credentials.emailPassword(email, "new-password"))
//            user.logOut()
//        } finally {
//            admin.setResetFunction(enabled = false)
//        }
//    }
//
//    @Test
//    fun callResetPasswordFunctionAsync() {
//        val provider = app.emailPassword
//        admin.setResetFunction(enabled = true)
//        val email = TestHelper.getRandomEmail()
//        provider.registerUser(email, "123456")
//        try {
//            looperThread.runBlocking {
//                provider.callResetPasswordFunctionAsync(email,
//                    "new-password",
//                    arrayOf("say-the-magic-word", 42)) { result ->
//                    if (result.isSuccess) {
//                        val user = app.login(Credentials.emailPassword(email, "new-password"))
//                        user.logOut()
//                        looperThread.testComplete()
//                    } else {
//                        Assert.fail(result.error.toString())
//                    }
//                }
//            }
//        } finally {
//            admin.setResetFunction(enabled = false)
//        }
//    }
//
//    @Test
//    fun callResetPasswordFunction_invalidServerArgsThrows() {
//        val provider = app.emailPassword
//        admin.setResetFunction(enabled = true)
//        val email = TestHelper.getRandomEmail()
//        provider.registerUser(email, "123456")
//        try {
//            provider.callResetPasswordFunction(email, "new-password", "wrong-magic-word")
//        } catch (error: AppException) {
//            assertEquals(ErrorCode.SERVICE_UNKNOWN, error.errorCode)
//        } finally {
//            admin.setResetFunction(enabled = false)
//        }
//    }
//
//    @Test
//    fun callResetPasswordFunctionAsync_invalidServerArgsThrows() {
//        val provider = app.emailPassword
//        admin.setResetFunction(enabled = true)
//        val email = TestHelper.getRandomEmail()
//        provider.registerUser(email, "123456")
//        try {
//            looperThread.runBlocking {
//                provider.callResetPasswordFunctionAsync(
//                    email,
//                    "new-password",
//                    arrayOf("wrong-magic-word")) { result ->
//                    if (result.isSuccess) {
//                        Assert.fail()
//                    } else {
//                        assertEquals(ErrorCode.SERVICE_UNKNOWN, result.error.errorCode)
//                        looperThread.testComplete()
//                    }
//                }
//            }
//        } finally {
//            admin.setResetFunction(enabled = false)
//        }
//    }
//
//    @Test
//    fun callResetPasswordFunction_invalidArgumentsThrows() {
//        val provider = app.emailPassword
//        assertFailsWith<IllegalArgumentException> { provider.callResetPasswordFunction(TestHelper.getNull(), "password") }
//        assertFailsWith<IllegalArgumentException> { provider.callResetPasswordFunction("foo@bar.baz", TestHelper.getNull()) }
//        looperThread.runBlocking {
//            provider.callResetPasswordFunctionAsync(TestHelper.getNull(), "new-password", arrayOf(), checkNullArgCallback)
//        }
//        looperThread.runBlocking {
//            provider.callResetPasswordFunctionAsync("foo@bar.baz", TestHelper.getNull(), arrayOf(), checkNullArgCallback)
//        }
//    }
//
//    @Ignore("Find a way to automate this")
//    @Test
//    fun resetPassword() {
//        TODO("How to test this manually?")
//    }
//
//    @Ignore("Find a way to automate this")
//    @Test
//    fun resetPasswordAsync() {
//        TODO("How to test this manually?")
//    }
//
//    @Test
//    fun resetPassword_invalidServerArgsThrows() {
//        val provider = app.emailPassword
//        try {
//            provider.resetPassword("invalid-token", "invalid-token-id", "new-password")
//        } catch (error: AppException) {
//            assertEquals(ErrorCode.BAD_REQUEST, error.errorCode)
//        }
//    }
//
//    @Test
//    fun resetPasswordASync_invalidServerArgsThrows() {
//        val provider = app.emailPassword
//        looperThread.runBlocking {
//            provider.resetPasswordAsync("invalid-token", "invalid-token-id", "new-password") { result ->
//                if (result.isSuccess) {
//                    Assert.fail()
//                } else {
//                    assertEquals(ErrorCode.BAD_REQUEST, result.error.errorCode)
//                    looperThread.testComplete()
//                }
//            }
//        }
//    }
//
//    @Test
//    fun resetPassword_invalidArgumentsThrows() {
//        val provider = app.emailPassword
//        assertFailsWith<IllegalArgumentException> { provider.resetPassword(TestHelper.getNull(), "token-id", "password") }
//        assertFailsWith<IllegalArgumentException> { provider.resetPassword("token", TestHelper.getNull(), "password") }
//        assertFailsWith<IllegalArgumentException> { provider.resetPassword("token", "token-id", TestHelper.getNull()) }
//        looperThread.runBlocking {
//            provider.resetPasswordAsync(TestHelper.getNull(), "token-id", "password", checkNullArgCallback)
//        }
//        looperThread.runBlocking {
//            provider.resetPasswordAsync("token", TestHelper.getNull(), "password", checkNullArgCallback)
//        }
//        looperThread.runBlocking {
//            provider.resetPasswordAsync("token", "token-id", TestHelper.getNull(), checkNullArgCallback)
//        }
//    }
//
//    @Test
//    @UiThreadTest
//    fun callMethodsOnMainThreadThrows() {
//        val provider: EmailPasswordAuth = app.emailPassword
//        val email: String = TestHelper.getRandomEmail()
//        for (method in Method.values()) {
//            try {
//                when (method) {
//                    Method.REGISTER_USER -> provider.registerUser(email, "123456")
//                    Method.CONFIRM_USER -> provider.confirmUser("token", "tokenId")
//                    Method.RESEND_CONFIRMATION_EMAIL -> provider.resendConfirmationEmail(email)
//                    Method.SEND_RESET_PASSWORD_EMAIL -> provider.sendResetPasswordEmail(email)
//                    Method.CALL_RESET_PASSWORD_FUNCTION -> provider.callResetPasswordFunction(email, "123456")
//                    Method.RETRY_CUSTOM_CONFIRMATION -> provider.retryCustomConfirmation(email)
//                    Method.RESET_PASSWORD -> provider.resetPassword("token", "token-id", "password")
//                }
//                Assert.fail("$method should have thrown an exception")
//            } catch (error: AppException) {
//                assertEquals(ErrorCode.NETWORK_UNKNOWN, error.errorCode)
//            }
//        }
//    }
//
//    @Test
//    fun callAsyncMethodsOnNonLooperThreadThrows() {
//        val provider: EmailPasswordAuth = app.emailPassword
//        val email: String = TestHelper.getRandomEmail()
//        val callback = App.Callback<Void> { Assert.fail() }
//        for (method in Method.values()) {
//            try {
//                when (method) {
//                    Method.REGISTER_USER -> provider.registerUserAsync(email, "123456", callback)
//                    Method.CONFIRM_USER -> provider.confirmUserAsync("token", "tokenId", callback)
//                    Method.RESEND_CONFIRMATION_EMAIL -> provider.resendConfirmationEmailAsync(email, callback)
//                    Method.SEND_RESET_PASSWORD_EMAIL -> provider.sendResetPasswordEmailAsync(email, callback)
//                    Method.CALL_RESET_PASSWORD_FUNCTION -> provider.callResetPasswordFunctionAsync(email, "123456", arrayOf(), callback)
//                    Method.RETRY_CUSTOM_CONFIRMATION -> provider.retryCustomConfirmationAsync(email, callback)
//                    Method.RESET_PASSWORD -> provider.resetPasswordAsync("token", "token-id", "password", callback)
//                }
//                Assert.fail("$method should have thrown an exception")
//            } catch (ignore: IllegalStateException) {
//            }
//        }
//    }
}
