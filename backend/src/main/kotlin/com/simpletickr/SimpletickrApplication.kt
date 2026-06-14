package com.simpletickr

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SimpletickrApplication

fun main(args: Array<String>) {
	runApplication<SimpletickrApplication>(*args)
}
