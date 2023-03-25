val mavenGroup: String by rootProject
val buildVersion: String by rootProject

plugins {
    java
    idea
    kotlin("jvm") version("1.6.10")
    id("su.plo.voice.plugin") version("1.0.0")
}

group = mavenGroup
version = buildVersion

dependencies {
    compileOnly("su.plo.voice.api:server:2.0.0+ALPHA")

    annotationProcessor("org.projectlombok:lombok:1.18.24")
}

tasks {
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }
}
