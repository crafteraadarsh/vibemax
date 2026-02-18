// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

plugins {
    id("com.android.library") version "8.7.2" apply false
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}

fun Project.cloudstream(configuration: com.lagradost.gradle.CloudstreamExtension.() -> Unit) =
    extensions.getByName<com.lagradost.gradle.CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: com.android.build.gradle.LibraryExtension.() -> Unit) =
    extensions.getByName<com.android.build.gradle.LibraryExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    android {
        namespace = "com.vibemax"
        compileSdk = 35

        defaultConfig {
            minSdk = 21
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    dependencies {
        val implementation by configurations
        val apk by configurations

        // CloudStream API stubs
        apk("com.github.recloudstream:cloudstream:pre-release")

        // Common dependencies
        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.11")
        implementation("org.jsoup:jsoup:1.18.1")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
