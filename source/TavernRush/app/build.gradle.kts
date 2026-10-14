plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "com.tavernrush.game"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.tavernrush.game"
        minSdk = 26
        targetSdk = 34
        versionCode = 1018
        versionName = "0.18.0-r18"
    }
    val localPreviewKey = providers.environmentVariable("TAVERNRUSH_DEBUG_KEYSTORE").orNull
    if (localPreviewKey != null) {
        signingConfigs.getByName("debug") {
            storeFile = file(localPreviewKey)
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            // Ключи установленных R04/R05 отсутствуют. R06 устанавливается рядом, без удаления их данных.
            applicationIdSuffix = ".preview.r06"
        }
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { aidl = false; resValues = false; shaders = false }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1024m"
            it.systemProperty("screenshots.dir", rootProject.file("screenshots").absolutePath)
            // PNG — тоже выход тестовой задачи: verify удаляет их и должен получать новый прогон.
            it.outputs.dir(rootProject.file("screenshots"))
            it.systemProperty("java.awt.headless", "true")
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
        }
    }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.5.0")
}
