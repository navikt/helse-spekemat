plugins {
    id("no.nav.helse.sas.sas-deployable")
}

sasDeployable {
    mainClass = "no.nav.helse.spekemat.foredler.AppKt"
    imageName = "${rootProject.name}-foredler"
}

dependencies {
    implementation(project(":fabrikk"))

    implementation(libs.tbd.libs.naisful.app)

    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt) {
        exclude(group = "junit")
    }

    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310)

    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.kotliquery)

    testImplementation(libs.tbd.libs.naisful.test.app)
    testImplementation(libs.tbd.libs.postgres.testdatabaser)
    testImplementation(libs.mockk)
}
