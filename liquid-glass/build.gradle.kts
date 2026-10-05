group = "com.github.liquidglass"
version = "1.0.0"

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "com.liquidglass"
    compileSdk = 35

    defaultConfig {
        // Compose 官方最低支持 API 21
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        // 公开 API 的 KDoc 与元数据
        freeCompilerArgs += listOf(
            "-Xexplicit-api=warning",
        )
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.liquidglass"
            artifactId = "liquid-glass-compose"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Liquid Glass Compose")
                description.set(
                    "Android 上的液态玻璃效果库：API 33+ AGSL 折射 + 7 路色散，" +
                        "API 21-32 自动降级到 RenderEffect / CPU / 渐变。",
                )
                url.set("https://github.com/liquidglass/liquid-glass-compose")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                scm {
                    url.set("https://github.com/liquidglass/liquid-glass-compose")
                    connection.set("scm:git:https://github.com/liquidglass/liquid-glass-compose.git")
                }
            }
        }
    }

    repositories {
        // JitPack 会读取本仓库的根目录 build 配置进行构建
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
}
