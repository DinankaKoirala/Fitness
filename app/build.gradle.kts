plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.example.fitness"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.fitness"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.room.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.play.location)
    implementation(libs.maplibre)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    ksp(libs.room.compiler)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.test.manifest)
}
