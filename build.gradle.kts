plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "io.mockhub"
version = "1.0.0"

val wiremockVersion = "3.13.2"

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
}

val jettyVersion = "12.0.40"

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.3"))
    implementation(platform("org.eclipse.jetty:jetty-bom:$jettyVersion"))
    implementation(platform("org.eclipse.jetty.ee10:jetty-ee10-bom:$jettyVersion"))
    implementation("org.wiremock:wiremock-jetty12:$wiremockVersion")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    constraints {
        implementation("org.apache.httpcomponents.client5:httpclient5:5.6.5")
        implementation("org.apache.httpcomponents.core5:httpcore5:5.4.4")
        implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.4.4")
        implementation("com.github.jknack:handlebars:4.5.5")
        implementation("org.apache.commons:commons-lang3:3.21.0")
    }

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val uiInstall by tasks.registering(Exec::class) {
    group = "ui"
    workingDir = file("ui")
    commandLine(npm, "ci", "--no-audit", "--no-fund")
    inputs.file("ui/package-lock.json")
    outputs.dir("ui/node_modules")
}

val uiTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the UI unit and component tests"
    dependsOn(uiInstall)
    workingDir = file("ui")
    commandLine(npm, "test")
}

val uiBuild by tasks.registering(Exec::class) {
    group = "ui"
    dependsOn(uiInstall)
    workingDir = file("ui")
    commandLine(npm, "run", "build")
    inputs.dir("ui/src")
    inputs.file("ui/index.html")
    inputs.file("ui/vite.config.ts")
    outputs.dir("ui/dist")
}

if (providers.gradleProperty("withUi").isPresent) {
    tasks.processResources { dependsOn(uiBuild) }
    tasks.check { dependsOn(uiTest) }
}

tasks.processResources {
    from("ui/dist") {
        into("mockhub-ui")
    }
}

val shadowJarPath = tasks.shadowJar.flatMap { it.archiveFile }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

tasks.test {
    dependsOn(tasks.shadowJar)
    systemProperty("mockhub.jar", shadowJarPath.get().asFile.absolutePath)
}

fun registerTestSlice(name: String, description: String, configure: Test.() -> Unit) =
    tasks.register<Test>(name) {
        group = "verification"
        this.description = description
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        configure()
    }

registerTestSlice("unitTest", "Runs unit tests only") {
    filter { excludeTestsMatching("io.mockhub.integration.*"); excludeTestsMatching("io.mockhub.system.*") }
}
registerTestSlice("integrationTest", "Runs in-process server integration tests") {
    filter { includeTestsMatching("io.mockhub.integration.*") }
}
registerTestSlice("systemTest", "Runs the packaged fat JAR as a separate process") {
    dependsOn(tasks.shadowJar)
    systemProperty("mockhub.jar", shadowJarPath.get().asFile.absolutePath)
    filter { includeTestsMatching("io.mockhub.system.*") }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "io.mockhub.Main",
            "Implementation-Version" to project.version,
        )
    }
}

tasks.shadowJar {
    archiveFileName.set("mockhub.jar")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class", "META-INF/versions/*/module-info.class")
    manifest {
        attributes(
            "Main-Class" to "io.mockhub.Main",
            "Implementation-Version" to project.version,
            "Multi-Release" to "true",
        )
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
