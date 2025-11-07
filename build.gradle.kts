plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
}

group = "com.fkcoding"
version = "0.0.1-SNAPSHOT"
description = "Payroll for Another Point Of View"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot - Using catalog ✅
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.web)

    // Kotlin - Using catalog ✅
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    // Database - Using catalog ✅
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.h2)

    // Google Calendar API - Using catalog ✅
    implementation(libs.google.api.client)
    implementation(libs.google.oauth.client.jetty)
    implementation(libs.google.api.services.calendar)
    implementation(libs.google.auth.library.oauth2)

    // PDF Generation
    implementation(libs.itext.core)
    implementation(libs.itext.layout)

    // Excel Generation
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)

    // Google Sheets & Drive API
    implementation(libs.google.api.services.sheets)
    implementation(libs.google.api.services.drive)

    // Apache POI για Excel parsing
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // Testing - Using catalog ✅
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}