val rapidsAndRiversVersion = "2024020507581707116327.1c34df474331"
val tbdLibsVersion = "2024.02.09-10.44-24d5802f"
val mockkVersion = "1.13.9"

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("com.github.navikt.tbd-libs:azure-token-client-default:$tbdLibsVersion")

    testImplementation("com.github.navikt.tbd-libs:mock-http-client:$tbdLibsVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}
