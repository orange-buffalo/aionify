plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.allopen") version "2.2.20"
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Database persistence
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.quarkiverse.jdbi:quarkus-jdbi:1.7.0")
    implementation("org.jdbi:jdbi3-kotlin:3.49.1")
    implementation("org.jdbi:jdbi3-kotlin-sqlobject:3.49.1")

    // Security / Password hashing
    implementation("io.quarkus:quarkus-elytron-security-common")

    // JWT authentication
    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")

    // Validation
    implementation("io.quarkus:quarkus-hibernate-validator")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("com.microsoft.playwright:playwright:1.52.0")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.rest-assured:kotlin-extensions")
}

group = "io.orange-buffalo"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        javaParameters = true
    }
}

// Frontend build tasks using Bun
tasks.register<Exec>("bunInstall") {
    workingDir = file("frontend")
    commandLine("bun", "install")
}

tasks.register<Exec>("bunBuild") {
    workingDir = file("frontend")
    commandLine("bun", "run", "build")
    dependsOn("bunInstall")
}

tasks.named("processResources") {
    dependsOn("bunBuild")
}

// Copy frontend dist to META-INF/resources
tasks.named<ProcessResources>("processResources") {
    from("frontend/dist") {
        into("META-INF/resources")
    }
}

// Playwright browser installation task
val playwrightConfig by configurations.creating {
    extendsFrom(configurations.testImplementation.get())
}

tasks.register<JavaExec>("installPlaywrightBrowsers") {
    classpath = playwrightConfig
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "--with-deps", "chromium")
}
