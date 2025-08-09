plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"// Needed for Glide
}

android {
    namespace = "com.leoworks.pikago"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leoworks.pikago"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // AndroidX + Material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // ✅ Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ✅ Supabase Kotlin SDK (OFFICIAL - LATEST)
    implementation("io.github.jan-tennert.supabase:auth-kt:3.1.1")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.1.1")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.1.1")
    implementation("io.github.jan-tennert.supabase:realtime-kt:3.1.1")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")
    implementation ("de.hdodenhof:circleimageview:3.1.0")
    // ✅ Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

}
