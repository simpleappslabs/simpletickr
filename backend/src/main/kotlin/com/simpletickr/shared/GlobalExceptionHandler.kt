package com.simpletickr.shared

import org.springframework.dao.DataIntegrityViolationException
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

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(e: DataIntegrityViolationException): ResponseEntity<Map<String, String?>> {
        val message = when {
            e.message?.contains("fk_transactions_listing") == true ->
                "This asset cannot be deleted because it has linked transactions."
            else -> "This operation cannot be completed due to a data integrity constraint."
        }
        return ResponseEntity.status(409).body(mapOf("message" to message))
    }
}
