plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.spring") version "2.2.10"
    id("org.springframework.boot") version "3.2.1"  // STABLE VERSION!
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.2.10"
}

group = "com.fkcoding"
version = "0.0.1-SNAPSHOT"
description = "Payroll for Another Point Of View"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    // REMOVE το snapshot repository για stable versions
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
//    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")  // ΠΡΟΣΘΗΚΗ για development

    // Google Calendar API
    implementation("com.google.api-client:google-api-client:2.0.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20220715-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")  // ΛΕΙΠΕ!

    // PDF Generation
    implementation("com.itextpdf:itext7-core:7.2.5")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
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