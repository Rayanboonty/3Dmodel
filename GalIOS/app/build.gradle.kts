plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "dev.galios"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.galios"
        // Build.SOC_MODEL is API 31; the Galaxy S25 Ultra ships with Android 15.
        minSdk = 31
        targetSdk = 35
        versionCode = 16
        versionName = "1.16"
        // The Galaxy S25 Ultra and S24 Ultra are arm64: leave out zstd's libraries for other CPUs.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with this machine's debug key: a personal build, not for a store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // The staging test runs OneOS's real root script when its path is given (see StageProtocolTest).
                System.getProperty("galios.rootScript")?.let { v -> it.systemProperty("galios.rootScript", v) }
                System.getProperty("galios.switchScript")?.let { v -> it.systemProperty("galios.switchScript", v) }
                System.getProperty("galios.returnScript")?.let { v -> it.systemProperty("galios.returnScript", v) }
                System.getProperty("galios.studioSwitch")?.let { v -> it.systemProperty("galios.studioSwitch", v) }
                // The stand-in phone's getprop and getevent, first on PATH.
                System.getProperty("galios.fakePath")?.let { v -> it.environment("PATH", v + ":" + System.getenv("PATH")) }
            }
        }
    }
    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
    }
}

// The on-phone image build: the same scripts that run on a PC (tools/), bundled as assets/galios-build/.
val galiosBuildAssets = layout.buildDirectory.dir("generated/galios-build-assets")
val copyBuildScripts by tasks.registering(Copy::class) {
    from(rootProject.file("tools/phone-build.sh"), rootProject.file("tools/build-ios-image-wsl.sh"))
    into(galiosBuildAssets.map { it.dir("galios-build") })
}
android.sourceSets["main"].assets.srcDir(galiosBuildAssets)
tasks.named("preBuild") { dependsOn(copyBuildScripts) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // .tar.zst: zstd (the @aar carries the Android native libraries) and tar.
    implementation("com.github.luben:zstd-jni:1.5.7-4@aar")
    implementation("org.apache.commons:commons-compress:1.27.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // The plain jar carries the desktop native library for the JVM tests.
    testImplementation("com.github.luben:zstd-jni:1.5.7-4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
