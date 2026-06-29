import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.net.URI
import java.util.zip.GZIPInputStream

plugins {
  id("java")
  id("org.springframework.boot") version "4.1.0"
  id("io.freefair.lombok") version "9.5.0"
}

group = "de.insulink.api"
version = "1.0.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_25
java.targetCompatibility = JavaVersion.VERSION_25

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(platform("org.junit:junit-bom:6.1.0"))
  testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.1")

  implementation("com.google.guava:guava:33.6.0-jre")

  implementation("org.projectlombok:lombok:1.18.46")
  annotationProcessor("org.projectlombok:lombok:1.18.46")
  testImplementation("org.projectlombok:lombok:1.18.46")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.46")

  implementation("org.json:json:20260522")
  implementation("commons-io:commons-io:2.22.0")

  implementation("org.apache.commons:commons-configuration2:2.15.1")
  implementation("commons-beanutils:commons-beanutils:1.11.0")

  implementation("org.postgresql:postgresql:42.7.11")
  implementation("org.hibernate.orm:hibernate-core:7.4.1.Final")
  implementation("org.reflections:reflections:0.10.2")

  implementation("org.springframework.boot:spring-boot-starter-web:4.1.0")
  implementation("org.springframework:spring-core:7.0.8")
  implementation("org.springframework.data:spring-data-jpa:4.1.0")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa:4.1.0")
  implementation("com.h2database:h2:2.4.240")

  implementation("de.mkammerer:argon2-jvm:2.12")

  implementation("io.jsonwebtoken:jjwt:0.13.0")

  implementation("com.nimbusds:nimbus-jose-jwt:10.9.1")

  implementation("com.maxmind.geoip2:geoip2:5.1.0")

  implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20260313.1")
}

tasks.test {
  useJUnitPlatform()
}

tasks.bootJar {
  mainClass = "de.insulink.api.ApiApplication"
}

tasks.register("downloadGeoLite2Database") {
  val licenseKey = System.getenv("GEOLITE2_LICENSE_KEY")
    ?: findProperty("geolite2.license.key") as String?
  val databaseUrl = "https://download.maxmind.com/app/geoip_download?" +
    "edition_id=GeoLite2-City&license_key=$licenseKey&suffix=tar.gz"
  val resourcesDir = File("geo")
  val downloadFile =
    layout.buildDirectory.file("GeoLite2-City.tar.gz").get().asFile
  doLast {
    resourcesDir.mkdirs()
    downloadFile.parentFile.mkdirs()
    if (downloadFile.exists()) {
      downloadFile.delete()
    }
    URI(databaseUrl).toURL().openStream().use { input ->
      downloadFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }
    extract(downloadFile, resourcesDir)
    downloadFile.delete()
  }
}

fun extract(file: File, destination: File) {
  GZIPInputStream(file.inputStream()).use { gis ->
    TarArchiveInputStream(gis).use { tis ->
      var entry = tis.nextEntry
      while (entry != null) {
        if (!entry.isDirectory && entry.name.endsWith(".mmdb")) {
          val outputFile = File(destination, "GeoLite2-City.mmdb")
          outputFile.outputStream().use { os ->
            tis.copyTo(os)
          }
        }
        entry = tis.nextEntry
      }
    }
  }
}