import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

val mainClass = "no.nav.helse.spekemat.AppKt"

val logbackClassicVersion = "1.4.14"
val logbackEncoderVersion = "7.4"
val jacksonVersion = "2.16.1"
val ktorVersion = "2.3.7"
val flywayCoreVersion = "10.6.0"
val hikariCPVersion = "5.1.0"
val postgresqlVersion = "42.7.1"
val kotliqueryVersion = "1.9.0"
val micrometerRegistryPrometheusVersion = "1.12.0"
val testcontainersVersion = "1.19.3"

plugins {
    id("com.bmuschko.docker-remote-api") version "9.4.0"
}

dependencies {
    api("ch.qos.logback:logback-classic:$logbackClassicVersion")
    api("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")

    api("io.ktor:ktor-server-cio:$ktorVersion")
    api("io.ktor:ktor-server-call-id:$ktorVersion")
    api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-serialization-jackson:$ktorVersion")
    api("io.ktor:ktor-server-auth:$ktorVersion")
    api("io.ktor:ktor-server-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }
    api("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    api("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")

    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    api("org.flywaydb:flyway-core:$flywayCoreVersion")
    api("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion") {
        exclude("junit", "junit")
    }
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
}

tasks {
    withType<Jar>() {
        finalizedBy(":foredler:remove_db_container")
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
