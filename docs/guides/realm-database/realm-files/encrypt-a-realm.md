# Encrypt a Realm - Kotlin SDK
You can encrypt your realms to ensure that the data stored to disk can't be
read outside of your application. You encrypt the realm file on
disk with AES-256 + SHA-2 by supplying a 64-byte encryption key when first
opening the realm.

Realm transparently encrypts and decrypts data with standard
[AES-256 encryption](https://en.wikipedia.org/wiki/Advanced_Encryption_Standard) using the
first 256 bits of the given 512-bit encryption key. Realm
uses the other 256 bits of the 512-bit encryption key to validate
integrity using a [hash-based message authentication code
(HMAC)](https://en.wikipedia.org/wiki/HMAC).

> **WARNING:**
> Do not use cryptographically-weak hashes for realm encryption keys.
For optimal security, we recommend generating random rather than derived
encryption keys.
>

> **NOTE:**
> You must encrypt a realm the first time you open it.
If you try to open an existing unencrypted realm using a configuration
that contains an encryption key, Realm throws an error.
>
> Alternatively, you can copy the unencrypted realm data to a new
encrypted realm using the
`Realm.writeCopyTo()`
method.
Refer to Copy Data into a New Realm for more information.
>

## Encrypt a Local Realm
To encrypt a local realm, pass your encryption key to the
`encryptionKey`
property in the
`RealmConfiguration.Builder()`
used to open the realm.

The following code demonstrates how to generate an encryption key and
open an encrypted local realm:

```kotlin
// return a random key from the given seed
fun getRandomKey(seed: Long? = null): ByteArray {
    // generate a new 64-byte encryption key
    val key = ByteArray(64)
    if (seed != null) {
        // If there is a seed provided, create a random number with that seed
        // and fill the byte array with random bytes
        Random(seed).nextBytes(key)
    } else {
        // fill the byte array with random bytes
        Random.nextBytes(key)
    }
    return key
}

runBlocking {
    // Create the configuration
    val config = RealmConfiguration.Builder(setOf(Frog::class))
        // Specify the encryption key
        .encryptionKey(generatedKey)
        .build()
    // Open the realm with the configuration
    val realm = Realm.open(config)
    Log.v("Successfully opened encrypted realm: ${realm.configuration.name}")
}

```

## Store & Reuse Keys
You **must** pass the same encryption key every time you open the encrypted realm.
If you don't provide a key or specify the wrong key for an encrypted
realm, the Realm SDK throws an error.

Apps should store the encryption key securely, typically in the target
platform's secure key/value storage, so that other apps cannot read the key. For
example, you can use the [Android Keystore system](https://developer.android.com/training/articles/keystore) or Apple's
[Keychain](https://developer.apple.com/documentation/security/certificate_key_and_trust_services/keys/storing_keys_in_the_keychain).
It is the developer's responsibility to ensure that attackers cannot access the
key.

## Performance Impact
Reads and writes on encrypted realms can be up to 10% slower than unencrypted realms.

## Access Encrypted Realms from Multiple Processes
> Version changed: 10.8.0

Starting with Realm Kotlin SDK version 10.8.0, Realm supports opening
the same encrypted realm in multiple processes.

If your app uses Realm Kotlin SDK version 10.7.1 or earlier, attempting to
open an encrypted realm from multiple processes throws this error:
`Encrypted interprocess sharing is currently unsupported.`
