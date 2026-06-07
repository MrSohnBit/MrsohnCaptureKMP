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

            windows {
                shortcut = true // 윈도우 시작 메뉴 바로가기 자동 생성
                menu = true
//                iconFile.set(project.file("icons/app_icon.ico")) // 윈도우용 아이콘
//                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
            macOS {
                dockName = "MrSohnCapture"
//                iconFile.set(project.file("icons/app_icon.icns")) // Mac용 아이콘
            }
//            windows {
//                iconFile.set(project.file("desktopApp.ico")) // 윈도우 전용 아이콘 경로
//                menu = true
//                shortcut = true
//            }
//
//            macOS {
//                iconFile.set(project.file("desktopApp.icns"))
//            }
//
//            linux {
//                iconFile.set(project.file("desktopApp.png"))
//            }
        }
    }
}
