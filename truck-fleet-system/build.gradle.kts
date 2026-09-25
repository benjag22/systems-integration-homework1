plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
    alias(libs.plugins.protobuf)
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "truck-fleet-system"


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.grpc.server)
    implementation(libs.spring.data.jpa)
    implementation (libs.redis)
    runtimeOnly(libs.postgresql)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

protobuf {
    plugins {
        create("grpc")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
