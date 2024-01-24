val rapidsAndRiversVersion = "2024010209171704183456.6d035b91ffb4"
val tbdLibsVersion = "2024.01.19-14.15-4080c4c8"
val mockkVersion = "1.13.9"

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("com.github.navikt.tbd-libs:azure-token-client-default:$tbdLibsVersion")

    testImplementation("com.github.navikt.tbd-libs:mock-http-client:$tbdLibsVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}
