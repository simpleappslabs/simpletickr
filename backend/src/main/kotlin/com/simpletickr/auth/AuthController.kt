package com.simpletickr.auth

import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.oidc.OidcSettings
import com.simpletickr.auth.persistence.IdentityRepository
import com.simpletickr.auth.usecase.ChangePasswordUseCase
import com.simpletickr.generated.api.AuthApi
import com.simpletickr.generated.model.AuthConfig
import com.simpletickr.generated.model.ChangePasswordRequest
import com.simpletickr.generated.model.LoginRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import com.simpletickr.generated.model.CurrentUser as CurrentUserModel

@RestController
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val identityRepository: IdentityRepository,
    private val oidcSettings: OidcSettings,
    private val sessionPrincipalEstablisher: SessionPrincipalEstablisher,
) : AuthApi {

    override fun login(loginRequest: LoginRequest): ResponseEntity<CurrentUserModel> {
        val authResult = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password)
        )
        val localDetails = authResult.principal as LocalUserDetails
        val principal = Principal.Local(id = localDetails.id, username = localDetails.username)

        val (request, response) = currentServletRequestResponse()
        sessionPrincipalEstablisher.establish(principal, request, response)

        return ResponseEntity.ok(toModel(principal))
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

    override fun getCurrentUser(): ResponseEntity<CurrentUserModel> = ResponseEntity.ok(toModel(currentUser()))

    override fun getAuthConfig(): ResponseEntity<AuthConfig> =
        ResponseEntity.ok(AuthConfig(oidcEnabled = oidcSettings.enabled))

    private fun toModel(principal: Principal): CurrentUserModel {
        val localLinked = identityRepository.findByUserIdAndProviderType(principal.id, ProviderType.LOCAL) != null
        val oidcLinked = identityRepository.findByUserIdAndProviderType(principal.id, ProviderType.OIDC) != null
        return CurrentUserModel(
            id = principal.id, username = principal.username, localLinked = localLinked, oidcLinked = oidcLinked,
        )
    }

    private fun currentServletRequestResponse() =
        (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes)
            .let { it.request to it.response!! }
}
