plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    `maven-publish`
}
val distributionVersion = providers.gradleProperty("distributionVersion").orElse("0.1.0")
android {
    namespace = "com.example.fitness"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.fitness"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("distributionVersionCode").orElse("1").get().toInt()
        versionName = distributionVersion.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main").assets.srcDir("$rootDir/licenses")
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
publishing {
    publications {
        create<MavenPublication>("hiFitliApk") {
            groupId = "io.github.dinankakoirala"
            artifactId = "hifitli"
            version = distributionVersion.get()
            artifact(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")) {
                extension = "apk"
                builtBy(providers.provider { tasks.getByName("assembleDebug") })
            }
            pom {
                name.set("HiFitli")
                description.set("HiFitli Android fitness tracker — development APK")
                url.set("https://github.com/DinankaKoirala/Fitness")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/" + providers.environmentVariable("GITHUB_REPOSITORY").orElse("DinankaKoirala/Fitness").get().lowercase())
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
        maven {
            name = "DistributionCheck"
            url = layout.buildDirectory.dir("distribution-check").get().asFile.toURI()
        }
    }
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
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.tesseract)
    implementation(libs.zxing)
    testImplementation(libs.junit)
    ksp(libs.room.compiler)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.test.manifest)
}
