plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "club.adstream.skyjumper"
    compileSdk = 34

    defaultConfig {
        applicationId = "club.adstream.skyjumper"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}

dependencies {

    implementation("com.google.android.gms:play-services-ads:22.6.0")
    implementation("com.google.android.gms:play-services-ads:22.6.0")
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")

    implementation("androidx.core:core:1.13.1")        // без ktx
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    testImplementation("junit:junit:4.13.2")

    //implementation(libs.appcompat)
    //implementation(libs.material)
    //implementation(libs.activity)
    //implementation(libs.constraintlayout)
    //testImplementation(libs.junit)
    //androidTestImplementation(libs.ext.junit)
    //androidTestImplementation(libs.espresso.core)
}