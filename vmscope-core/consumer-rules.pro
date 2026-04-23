# Consumer R8/ProGuard rules for vmscope-core.
# Merged into the consumer app's R8 config automatically via the AAR.

# 1. The App Startup initializer is referenced by fully-qualified name from the manifest.
#    It must not be renamed or removed.
-keep class org.velkos.vmscope.VmScopeInitializer { <init>(); }

# 2. VmScopeConfig.Provider is checked via `app as? Provider` in the initializer and
#    implemented on the consumer's Application. Keep the interface and its members so
#    the runtime cast + getter call survive minification even across build boundaries.
-keep interface org.velkos.vmscope.VmScopeConfig$Provider { *; }

# 3. UnhandledViewModelException is a stable symbol in crash reports — don't rename.
-keepnames class org.velkos.vmscope.UnhandledViewModelException
