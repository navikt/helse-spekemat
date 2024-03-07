plugins {
    `maven-publish`
}

configure<JavaPluginExtension> {
    withSourcesJar()
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.github.navikt.spekemat"
            artifactId = project.name
            version = "${project.version}"
        }
    }
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/navikt/helse-spekemat")
            credentials {
                username = "x-access-token"
                password = System.getenv("GITHUB_PASSWORD")
            }
        }
    }
}