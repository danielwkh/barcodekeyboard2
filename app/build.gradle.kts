plugins { id("com.android.application") }

android {
    namespace = "com.danielwkh.barcodekeyboard2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.danielwkh.barcodekeyboard2"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
