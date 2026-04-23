import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "org.velkos.vmscope.sample.kmp"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.velkos.vmscope.sample.kmp"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        // Check transitive dependencies so vmscope-core's lint rules (shipped in its AAR via
        // lintPublish) fire on this consumer. In particular the `UseVmScope` rule fires on the
        // shared ViewModel's deliberate `viewModelScope.launch { ... }` sites — demonstrating
        // that lint rules cross the shared-code boundary correctly on the Android consumer.
        checkDependencies = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(project(":sample-kmp:shared"))
    implementation(project(":vmscope-core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.google.material)
}
