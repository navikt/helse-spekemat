val rapidsAndRiversVersion = "2024111211071731406062.648687519469"
val tbdLibsVersion = "2024.11.12-11.09-16cf2599"
val mockkVersion = "1.13.13"

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("com.github.navikt.tbd-libs:azure-token-client-default:$tbdLibsVersion")
    api("com.github.navikt.tbd-libs:retry:$tbdLibsVersion")

    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:mock-http-client:$tbdLibsVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}
