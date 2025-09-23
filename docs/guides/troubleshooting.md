# Troubleshooting - Kotlin SDK
## iOS/iPad OS Bad Alloc/Not Enough Memory Available
In iOS or iPad devices with little available memory, or where you have a
memory-intensive application that uses multiple realms or many notifications,
you may encounter the following error:

```console
libc++abi: terminating due to an uncaught exception of type std::bad_alloc: std::bad_alloc
```

This error typically indicates that a resource cannot be allocated because
not enough memory is available.

If you are building for iOS 15+ or iPad 15+, you can add the
[Extended Virtual Addressing Entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com_apple_developer_kernel_extended-virtual-addressing)
to resolve this issue.

Add these keys to your Property List, and set the values to `true`:

```xml
<key>com.apple.developer.kernel.extended-virtual-addressing</key>
<true/>
<key>com.apple.developer.kernel.increased-memory-limit</key>
<true/>
```

## Use in System Apps on Custom Android ROMs
Realm SDKs use named pipes to support notifications and access to
the Realm file from multiple processes. While this is allowed by
default for normal user apps, it is disallowed for system apps.

System apps are defined by setting `android:sharedUserId="android.uid.system"`
in the Android manifest. For system apps, you may see a security violation in
Logcat that looks something like this:

```bash
05-24 14:08:08.984  6921  6921 W .realmsystemapp: type=1400 audit(0.0:99): avc: denied { write } for name="realm.testapp.com.realmsystemapp-Bfqpnjj4mUvxWtfMcOXBCA==" dev="vdc" ino=14660 scontext=u:r:system_app:s0 tcontext=u:object_r:apk_data_file:s0 tclass=dir permissive=0
05-24 14:08:08.984  6921  6921 W .realmsystemapp: type=1400 audit(0.0:100): avc: denied { write } for name="realm.testapp.com.realmsystemapp-Bfqpnjj4mUvxWtfMcOXBCA==" dev="vdc" ino=14660 scontext=u:r:system_app:s0 tcontext=u:object_r:apk_data_file:s0 tclass=dir permissive=0
```

To fix this, you need to adjust the SELinux security rules in the ROM. This can
be done by using the tool `audit2allow`. This tool ships as part of
[AOSP](https://source.android.com/).

1. Pull the current policy from the device: `adb pull /sys/fs/selinux/policy`.
2. Copy the SELinux error inside a text file called `input.txt`.
3. Run the `audit2allow` tool: `audit2allow -p policy -i input.txt`.
4. The tool should output a rule you can add to your existing policy.
The rule allows you to access the Realm file from multiple processes.

`audit2allow` is produced when compiling AOSP/ROM and only runs on
Linux. Check out the details in the [Android Source documentation](https://source.android.com/security/selinux/validate#using_audit2allow).
Also note that since Android Oreo, Google changed the way it configures
SELinux and the default security policies are now more modularized. More details
are in the [Android Source documentation](https://source.android.com/security/selinux/images/SELinux_Treble.pdf).
