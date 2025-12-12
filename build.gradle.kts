plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.allopen") version "2.1.0"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    id("io.micronaut.application") version "4.6.1"
    id("io.micronaut.docker") version "4.6.1"
    id("io.micronaut.test-resources") version "4.6.1"
}

val micronautVersion: String by project

repositories {
    mavenCentral()
}

dependencies {
    // Micronaut core
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database persistence with Micronaut Data JDBC
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Micronaut annotation processors
    ksp("io.micronaut:micronaut-inject-kotlin")  // CRITICAL: For @Controller, @Singleton, etc.
    ksp("io.micronaut.data:micronaut-data-processor")
    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    ksp("io.micronaut.validation:micronaut-validation-processor")

    // Security - JWT authentication
    implementation("io.micronaut.security:micronaut-security-jwt")
    implementation("org.mindrot:jbcrypt:0.4")

    // Validation
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("jakarta.validation:jakarta.validation-api")

    // Logging
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.microsoft.playwright:playwright:1.52.0")
    testImplementation("io.micronaut.test:micronaut-test-rest-assured")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.rest-assured:kotlin-extensions")
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("io.micronaut.testresources:micronaut-test-resources-client")
    testRuntimeOnly("io.micronaut.testresources:micronaut-test-resources-testcontainers")
}

// E2E tests source set
sourceSets {
    create("e2eTest") {
        kotlin {
            srcDir("src/e2eTest/kotlin")
        }
        resources {
            srcDir("src/e2eTest/resources")
        }
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

// E2E test dependencies
val e2eTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

dependencies {
    e2eTestImplementation("org.testcontainers:testcontainers:1.20.4")
    e2eTestImplementation("org.testcontainers:junit-jupiter:1.20.4")
    e2eTestImplementation("com.microsoft.playwright:playwright:1.52.0")
    e2eTestImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

group = "io.orange-buffalo"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

micronaut {
    version.set(micronautVersion)
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("io.orangebuffalo.aionify.*")
    }
}

val dockerImageTag = System.getenv("DOCKER_IMAGE_TAG") ?: "latest"
val dockerImage = "ghcr.io/orange-buffalo/aionify:$dockerImageTag"
tasks.dockerBuild {
    images.set(listOf(dockerImage))
}

application {
    mainClass.set("io.orangebuffalo.aionify.Application")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}

allOpen {
    annotation("io.micronaut.aop.Around")
    annotation("jakarta.inject.Singleton")
    annotation("io.micronaut.data.annotation.Repository")
    annotation("io.micronaut.http.annotation.Controller")
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

// E2E test task
val e2eTest by tasks.registering(Test::class) {
    description = "Runs E2E tests against Docker image"
    group = "verification"

    testClassesDirs = sourceSets["e2eTest"].output.classesDirs
    classpath = sourceSets["e2eTest"].runtimeClasspath

    // Must run after the image is built
    mustRunAfter(tasks.build)

    // Pass the Docker image tag to tests via system property
    // Check is deferred to execution time via doFirst
    doFirst {
        val targetImage = System.getProperty("aionify.docker.image") ?: dockerImage
        systemProperty("aionify.docker.image", targetImage)
    }

    useJUnitPlatform()

    // Generate test reports
    reports {
        html.required.set(true)
        junitXml.required.set(true)
    }

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}
