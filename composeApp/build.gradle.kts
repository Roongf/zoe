plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.sqldelight.desktop)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.compose.desktop.ui.test)
            }
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android)
        }
    }
}

sqldelight {
    databases {
        create("QianDuoDuoDatabase") {
            packageName.set("com.qianduoduo.database")
        }
    }
}

android {
    namespace = "com.qingji.ledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qingji.ledger"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.0"
        manifestPlaceholders["appDisplayName"] = "@string/app_name"
        buildConfigField("boolean", "DEMO_LEDGER", "false")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            matchingFallbacks += "debug"
            manifestPlaceholders["appDisplayName"] = "钱多多·体验"
            buildConfigField("boolean", "DEMO_LEDGER", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.zoe.ledger.MainKt"
    }
}
