plugins {
    id("no.nav.helse.sas.sas-deployable")
}

sasDeployable {
    mainClass = "no.nav.helse.spekemat.slakter.AppKt"
    imageName = "${rootProject.name}-slakter"
}

dependencies {
    implementation(libs.rapids.and.rivers)
    implementation(libs.tbd.libs.azure.token.client.default)
    implementation(libs.tbd.libs.retry)

    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
    testImplementation(libs.tbd.libs.mock.http.client)
    testImplementation(libs.mockk)
}
