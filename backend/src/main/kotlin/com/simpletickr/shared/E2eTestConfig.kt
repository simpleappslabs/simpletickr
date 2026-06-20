package com.simpletickr.shared

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("e2etest")
class E2eTestConfig {
    @Bean
    fun cleanMigrationStrategy(): FlywayMigrationStrategy = FlywayMigrationStrategy { flyway ->
        flyway.clean()
        flyway.migrate()
    }
}
