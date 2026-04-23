# Release-mode R8 rules for the KMP Android sample app. vmscope ships its own consumer
# rules inside the AAR (keeps VmScopeInitializer, VmScopeConfig.Provider members, and
# UnhandledViewModelException) — they get applied automatically via consumerProguardFiles, so
# nothing extra is needed here for the library itself.
