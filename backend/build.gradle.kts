import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jetbrains.kotlin.jvm") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.allopen") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.jpa") version "2.2.10"
}

group = "com.mohanlv"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // MySQL
    runtimeOnly("com.mysql:mysql-connector-j")

    // H2 for development
    runtimeOnly("com.h2database:h2")

    // GitHub API
    implementation("org.kohsuke:github-api:1.323")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("org.springframework.stereotype.Component")
    annotation("org.springframework.stereotype.Service")
    annotation("org.springframework.stereotype.Repository")
    annotation("org.springframework.stereotype.Controller")
    annotation("org.springframework.web.bind.annotation.RestController")
    annotation("org.springframework.context.annotation.Configuration")
}