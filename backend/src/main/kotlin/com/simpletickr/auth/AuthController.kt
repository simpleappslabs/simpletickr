package com.simpletickr.auth

import com.simpletickr.auth.usecase.ChangePasswordUseCase
import com.simpletickr.generated.api.AuthApi
import com.simpletickr.generated.model.ChangePasswordRequest
import com.simpletickr.generated.model.LoginRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import com.simpletickr.generated.model.CurrentUser as CurrentUserModel

@RestController
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val changePasswordUseCase: ChangePasswordUseCase,
) : AuthApi {

    private val securityContextRepository: SecurityContextRepository = HttpSessionSecurityContextRepository()

    override fun login(loginRequest: LoginRequest): ResponseEntity<CurrentUserModel> {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password)
        )

        val (request, response) = currentServletRequestResponse()
        val existingSession = request.getSession(false)
        request.getSession(true)
        if (existingSession != null) request.changeSessionId()

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)

        val principal = authentication.principal as CurrentUser
        return ResponseEntity.ok(CurrentUserModel(id = principal.id, username = principal.username))
    }

    override fun logout(): ResponseEntity<Unit> {
        val (request, _) = currentServletRequestResponse()
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }

    override fun changePassword(changePasswordRequest: ChangePasswordRequest): ResponseEntity<Unit> {
        val principal = currentUser()
        changePasswordUseCase.execute(principal.id, changePasswordRequest.currentPassword, changePasswordRequest.newPassword)
        return ResponseEntity.noContent().build()
    }

    override fun getCurrentUser(): ResponseEntity<CurrentUserModel> {
        val principal = currentUser()
        return ResponseEntity.ok(CurrentUserModel(id = principal.id, username = principal.username))
    }

    private fun currentServletRequestResponse() =
        (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes)
            .let { it.request to it.response!! }
}
