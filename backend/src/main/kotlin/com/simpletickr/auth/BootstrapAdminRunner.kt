package com.simpletickr.auth

import com.simpletickr.auth.usecase.BootstrapAdminUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class BootstrapAdminRunner(private val bootstrapAdminUseCase: BootstrapAdminUseCase) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val result = bootstrapAdminUseCase.execute() ?: return
        log.warn(
            "\n================================================================\n" +
                "  Bootstrap admin account created\n" +
                "  Username: {}\n" +
                "  Password: {}\n" +
                "  Log in and change this password immediately.\n" +
                "================================================================",
            result.username, result.password,
        )
    }
}
