//package io.realm.mongodb.internal
//
//import android.content.Context
//
//actual fun getFileContext(context: Any?) {
//    if (context is Context) {
//        context.filesDir.path
//    }
//    throw IllegalArgumentException("Context must be an Android context.")
//}
//
//actual fun getSyncBasePath(context: Any?): String {
//    if (context is Context) {
//        return context.filesDir.path
//    }
//    throw IllegalArgumentException("Context must be an Android context.")
//}
//
//actual fun getUserAgentBindingInfo(): String {
//    TODO()
//}
//
//actual fun getAppDefinedUserAgent(): String {
//    TODO()
//}
