import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

fun findCacioProjects(): List<File> {
    val rootDir = project.rootDir
    return rootDir.listFiles { file ->
        file.isDirectory && file.name.startsWith("cacio") && file.name != "cacio"
    }?.toList() ?: emptyList()
}

tasks.register("buildRuntime") {
    doLast {
        val cacioZip = rootDir.resolve("app/src/main/assets/runtime_libs/cacio.zip")
        if (!cacioZip.exists()) cacioZip.createNewFile()

        val cacioProjects = findCacioProjects()
        println("Found ${cacioProjects.size} cacio projects:")

        var allJarFiles = mutableListOf<File>()
        cacioProjects.forEach { projectDir ->
            val buildDir = File(projectDir, "build/libs")
            if (buildDir.exists()) {
                val jarFiles = buildDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".jar")
                }

                allJarFiles.addAll(jarFiles)
                println("${projectDir.name}: ${jarFiles?.size ?: 0} JAR files")
            } else {
                println("${projectDir.name}: No build directory found")
            }
        }

        ZipOutputStream(FileOutputStream(cacioZip)).use { out ->
            for (jarFile in allJarFiles)
                FileInputStream(jarFile).use { fi ->
                    val entry = ZipEntry("cacio/${jarFile.name}")
                    out.putNextEntry(entry)
                    fi.copyTo(out, 1024)
                    out.closeEntry()
                }
        }
    }
}

afterEvaluate {
    tasks.named("mergeDebugAssets") {
        dependsOn(
            ":cacio-argent:jar", ":cacio-shared:jar", ":cacio-tta:jar", ":app:buildRuntime"
        )
    }

}

android {
    namespace = "io.github.eurya.awt"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.eurya.awt"
        minSdk = 26
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
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
        viewBinding = false
    }
    buildToolsVersion = "36.0.0"
    ndkVersion = "27.0.12077973"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.core.android)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}