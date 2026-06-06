plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.openapi.generator") version "7.12.0"
}

group = "com.simpletickr"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}


dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	runtimeOnly("org.postgresql:postgresql")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	implementation("org.springframework.boot:spring-boot-starter-validation")
}

openApiGenerate {
	generatorName.set("kotlin-spring")
	inputSpec.set("$rootDir/../openapi.yaml")
	outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
	apiPackage.set("com.simpletickr.generated.api")
	modelPackage.set("com.simpletickr.generated.model")
	configOptions.set(mapOf(
		"interfaceOnly" to "true",
		"useSpringBoot3" to "true",
		"useTags" to "true",
		"documentationProvider" to "none",
	))
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

sourceSets {
	main {
		kotlin {
			srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
		}
	}
}

tasks.compileKotlin {
	dependsOn(tasks.openApiGenerate)
}

tasks.withType<Test> {
	useJUnitPlatform()
	// docker-java defaults to API 1.32; Docker 29+ requires >= 1.40
	systemProperty("api.version", "1.44")
}
