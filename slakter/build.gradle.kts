val rapidsAndRiversVersion = "2024112412131732446804.1b3dcc636bed"
val tbdLibsVersion = "2024.11.23-14.22-c649da38"
val mockkVersion = "1.13.13"

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("com.github.navikt.tbd-libs:azure-token-client-default:$tbdLibsVersion")
    api("com.github.navikt.tbd-libs:retry:$tbdLibsVersion")

    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:mock-http-client:$tbdLibsVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}
