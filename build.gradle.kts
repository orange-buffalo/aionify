import org.jreleaser.model.Active
import java.time.Duration

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.allopen") version "2.3.0"
    id("com.google.devtools.ksp") version "2.3.4"
    id("io.micronaut.application") version "4.6.1"
    id("io.micronaut.docker") version "4.6.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("com.github.jmongard.git-semver-plugin") version "0.13.0"
    id("org.jreleaser") version "1.22.0"
}

val micronautVersion: String by project

group = "io.orange-buffalo"

semver {
    // tags managed by jreleaser
    createReleaseTag = false
}
val ver = semver.version
allprojects {
    version = ver
}

repositories {
    mavenCentral()
}

dependencies {
    // Micronaut core
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.serde:micronaut-serde-jackson") // Required for native image serialization
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.micronaut.reactor:micronaut-reactor") // For reactive filters

    // Database persistence with Micronaut Data JDBC
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Micronaut annotation processors
    ksp("io.micronaut:micronaut-inject-kotlin") // CRITICAL: For @Controller, @Singleton, etc.
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

    // OpenAPI / Swagger
    implementation("io.micronaut.openapi:micronaut-openapi")
    ksp("io.micronaut.openapi:micronaut-openapi")

    // Logging
    implementation("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.microsoft.playwright:playwright:1.57.0")
    testImplementation("io.micronaut.test:micronaut-test-rest-assured")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.rest-assured:kotlin-extensions")
    testImplementation("org.testcontainers:testcontainers:2.0.3")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.3")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.3")
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
    e2eTestImplementation("org.testcontainers:testcontainers:2.0.3")
    e2eTestImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.3")
    e2eTestImplementation("com.microsoft.playwright:playwright:1.57.0")
    e2eTestImplementation("org.junit.jupiter:junit-jupiter:6.0.2")
    e2eTestImplementation("org.junit.platform:junit-platform-launcher:6.0.2")
}

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

val dockerImage = "ghcr.io/orange-buffalo/aionify:${project.version}"
tasks.dockerBuildNative {
    images.set(listOf(dockerImage))
}
tasks.dockerPushNative {
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

    // Global test timeout: 15 minutes
    timeout.set(Duration.ofMinutes(15))

    // Enable parallel test execution based on available CPUs, capped at 4 forks
    val availableProcessors = Runtime.getRuntime().availableProcessors()
    maxParallelForks = minOf(availableProcessors, 4)

    // Log the fork configuration
    doFirst {
        logger.lifecycle("Running tests with maxParallelForks = $maxParallelForks (available processors: $availableProcessors)")
    }

    // Configure inputs to invalidate cache when test files change
    // This ensures new test classes are detected and cache is invalidated
    inputs
        .files(sourceSets["test"].allSource)
        .withPropertyName("testSourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
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

// Ktlint configuration
ktlint {
    version.set("1.8.0")
    android.set(false)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}

// Make check task depend on ktlint check
tasks.named("check") {
    dependsOn("ktlintCheck")
}

// Frontend build tasks using Bun
tasks.register<Exec>("bunInstall") {
    workingDir = file("frontend")
    commandLine("bun", "install")

    // Define inputs and outputs for incremental build support
    inputs
        .files("frontend/package.json", "frontend/bun.lock")
        .withPropertyName("packageFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    outputs
        .dir("frontend/node_modules")
        .withPropertyName("nodeModules")
}

tasks.register<Exec>("bunBuild") {
    workingDir = file("frontend")
    commandLine("bun", "run", "build")
    dependsOn("bunInstall")

    // Define inputs and outputs for incremental build support
    inputs
        .files("frontend/package.json", "frontend/bun.lock")
        .withPropertyName("packageFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs
        .file("frontend/build.ts")
        .withPropertyName("buildScript")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs
        .file("frontend/tsconfig.json")
        .withPropertyName("tsconfig")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs
        .file("frontend/components.json")
        .withPropertyName("componentsConfig")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs
        .dir("frontend/src")
        .withPropertyName("sourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs
        .dir("frontend/public")
        .withPropertyName("publicFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .optional()

    outputs
        .dir("frontend/dist")
        .withPropertyName("distDirectory")
}

// Prettier tasks for frontend code formatting
tasks.register<Exec>("prettierCheck") {
    description = "Check frontend code formatting with Prettier"
    group = "verification"
    workingDir = file("frontend")
    commandLine("bun", "run", "format:check")
    dependsOn("bunInstall")
}

tasks.register<Exec>("prettierFormat") {
    description = "Format frontend code with Prettier"
    group = "formatting"
    workingDir = file("frontend")
    commandLine("bun", "run", "format")
    dependsOn("bunInstall")
}

// Make check task depend on prettier check
tasks.named("check") {
    dependsOn("prettierCheck")
}

// Combined formatting task
tasks.register("format") {
    description = "Format all code (Kotlin and frontend)"
    group = "formatting"
    dependsOn("ktlintFormat", "prettierFormat")
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

jreleaser {
    gitRootSearch = true
    release {
        github {
            dryrun = project.version.get().endsWith("-SNAPSHOT")

            uploadAssets = Active.NEVER
            prerelease {
                enabled = true
            }
            changelog {
                formatted = Active.ALWAYS
                preset = "conventional-commits"
                skipMergeCommits = true
                hide {
                    uncategorized = true
                    contributor("[bot]")
                    contributor("orange-buffalo")
                    contributor("GitHub")
                    contributor("Copilot")
                }
            }
        }
    }
    signing {
        active = Active.NEVER
    }
    deploy {
        active = Active.NEVER
    }
}
