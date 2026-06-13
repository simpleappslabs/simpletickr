package com.simpletickr.shared

import org.springframework.dao.DuplicateKeyException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.badRequest().body(mapOf("message" to e.message))

    @ExceptionHandler(DuplicateKeyException::class)
    fun handleDuplicateKey(e: DuplicateKeyException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(409).body(mapOf("message" to e.message))
}
