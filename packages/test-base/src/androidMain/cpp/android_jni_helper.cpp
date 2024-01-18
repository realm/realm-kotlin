#include <jni.h>

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_realm_kotlin_test_platform_PlatformUtils_nativeAllocateEncryptionKeyOnNativeMemory(
        JNIEnv *env, jclass, jbyteArray byteArray) {
    jsize arrayLength = env->GetArrayLength(byteArray);
    jbyte *nativeArray = new jbyte[arrayLength];
    // Copy the contents of the Kotlin ByteArray to the native array
    env->GetByteArrayRegion(byteArray, 0, arrayLength, nativeArray);

    // Return the address of the native array
    return reinterpret_cast<jlong>(nativeArray);
}

JNIEXPORT void JNICALL
Java_io_realm_kotlin_test_platform_PlatformUtils_nativeFreeEncryptionKeyFromNativeMemory(
        JNIEnv *env, jclass, jlong keyPtr) {
    delete[] reinterpret_cast<jbyte *>(keyPtr);
}

}