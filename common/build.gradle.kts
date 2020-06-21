@file:Suppress("SpellCheckingInspection", "PropertyName")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kolor_version: String by project
val result_version: String by project

plugins {
    maven

    `java-library`

    kotlin("jvm")
}

group = "blogify"
version = "alpha-1"

repositories {
    mavenCentral()
    mavenLocal()
    jcenter()
    maven("https://jitpack.io")
    maven { url = uri("https://dl.bintray.com/kittinunf/maven") }
    maven { url = uri("https://kotlin.bintray.com/ktor") }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")

    // Kolor

    implementation("com.andreapivetta.kolor", "kolor", kolor_version)

    // Result

    api("com.github.kittinunf.result", "result", result_version)
    api("com.github.kittinunf.result", "result-coroutines", result_version)

    // Logback

    implementation("ch.qos.logback:logback-classic:1.2.3")

    // Testing

    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.5.2")
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", "5.5.2")
}

artifacts {
    archives(tasks.kotlinSourcesJar)
}

kotlin.sourceSets["main"].kotlin.srcDirs("src")
kotlin.sourceSets["test"].kotlin.srcDirs("test")

tasks {
    withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "1.8"
    }

    withType<Test>().configureEach {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
