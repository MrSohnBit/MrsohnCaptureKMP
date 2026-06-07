plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(libs.compose.ui)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons.extended)
                implementation(libs.compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "MrSohnCapture"
            packageVersion = "1.0.7"

            // 외부 파일(sdk 등)을 패키지에 포함시키기 위한 설정
            appResourcesRootDir.set(project.file("../sdk/windows/platform-tools/"))

            windows {
                shortcut = true
                menu = true
//                iconFile.set(project.file("desktopApp.ico"))
            }
            macOS {
                dockName = "MrSohnCapture"
//                iconFile.set(project.file("desktopApp.icns"))
            }
            linux {
//                iconFile.set(project.file("desktopApp.png"))
            }
        }
    }
}
