@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    wasmJs {
        outputModuleName = "painter"
        browser()
        binaries.executable()
        generateTypeScriptDefinitions()
        compilerOptions {
            target = "es2015"
            optIn.add("kotlin.js.ExperimentalJsExport")
        }
    }


    sourceSets {
        wasmJsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation(libs.kotlinx.coroutines.core)
        }
        wasmJsTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
