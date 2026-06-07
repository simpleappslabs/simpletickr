package com.simpletickr.shared

import com.simpletickr.generated.model.Error
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateKeyException::class)
    fun handleDuplicateKey(): ResponseEntity<Error> =
        ResponseEntity.status(409).body(Error("A resource with that identifier already exists"))
}
