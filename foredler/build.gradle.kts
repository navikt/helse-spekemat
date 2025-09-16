import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

val tbdLibsVersion = "2025.08.16-09.21-71db7cad"
val logbackClassicVersion = "1.5.18"
val logbackEncoderVersion = "8.0"
val jacksonVersion = "2.18.3"
val ktorVersion = "3.2.3"
val flywayCoreVersion = "11.5.0"
val hikariCPVersion = "6.3.0"
val postgresqlVersion = "42.7.7"
val kotliqueryVersion = "1.9.0"
val mockKVersion = "1.13.17"

plugins {
    id("com.bmuschko.docker-remote-api") version "9.4.0"
}

dependencies {
    api(project(":fabrikk"))

    implementation("com.github.navikt.tbd-libs:naisful-app:$tbdLibsVersion")

    api("ch.qos.logback:logback-classic:$logbackClassicVersion")
    api("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")

    api("io.ktor:ktor-server-auth:$ktorVersion")
    api("io.ktor:ktor-server-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }

    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    api("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("com.github.navikt.tbd-libs:naisful-test-app:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:postgres-testdatabaser:$tbdLibsVersion")
    testImplementation("io.mockk:mockk:$mockKVersion")
}

tasks {
    withType<Jar>() {
        finalizedBy(":foredler:remove_db_container")
    }

    withType<Test> {
        systemProperty("junit.jupiter.execution.parallel.enabled", "true")
        systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
        systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
        systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "4")
    }
}

tasks.create("remove_db_container", DockerRemoveContainer::class) {
    targetContainerId("spekemat")
    dependsOn(":foredler:test")
    setProperty("force", true)
    onError {
        if (!this.message!!.contains("No such container"))
            throw this
    }
}
