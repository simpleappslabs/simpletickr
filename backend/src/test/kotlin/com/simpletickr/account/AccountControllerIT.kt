package com.simpletickr.account

import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.usecase.CreateAccountUseCase
import com.simpletickr.account.usecase.DeleteAccountUseCase
import com.simpletickr.account.usecase.UpdateAccountUseCase
import com.simpletickr.auth.CurrentUser
import com.simpletickr.shared.SecurityConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AccountController::class)
@Import(SecurityConfig::class)
class AccountControllerIT {

    private val owner = CurrentUser(1L, "test-user", "hash")
    private val other = CurrentUser(2L, "other-user", "hash")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var accountService: AccountService

    @MockitoBean
    private lateinit var createAccountUseCase: CreateAccountUseCase

    @MockitoBean
    private lateinit var updateAccountUseCase: UpdateAccountUseCase

    @MockitoBean
    private lateinit var deleteAccountUseCase: DeleteAccountUseCase

    private val account = Account(
        id = 1L, userId = 1L, name = "Fidelity", broker = null, accountType = AccountType.BROKERAGE,
        currency = null, accountNumber = "12345", institution = "Fidelity Investments",
    )

    @Test
    fun `GET accounts returns the current user's accounts`() {
        whenever(accountService.listAccounts(1L)).thenReturn(listOf(account))

        mockMvc.perform(get("/accounts").with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Fidelity"))
    }

    @Test
    fun `GET account by id returns 200 when owned by the caller`() {
        whenever(accountService.getAccount(1L, 1L)).thenReturn(account)

        mockMvc.perform(get("/accounts/1").with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accountNumber").value("12345"))
    }

    @Test
    fun `GET account by id returns 404 when owned by another user`() {
        whenever(accountService.getAccount(1L, 2L)).thenReturn(null)

        mockMvc.perform(get("/accounts/1").with(user(other)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST account assigns the current user as owner`() {
        whenever(createAccountUseCase.execute(any(), eq(1L))).thenReturn(account)

        mockMvc.perform(
            post("/accounts")
                .with(user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Fidelity","accountType":"BROKERAGE"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Fidelity"))
    }

    @Test
    fun `PUT account returns 404 when owned by another user`() {
        whenever(updateAccountUseCase.execute(eq(1L), any(), eq(2L))).thenReturn(null)

        mockMvc.perform(
            put("/accounts/1")
                .with(user(other))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Renamed","accountType":"BROKERAGE"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE account returns 404 when owned by another user`() {
        whenever(deleteAccountUseCase.execute(1L, 2L)).thenReturn(false)

        mockMvc.perform(delete("/accounts/1").with(user(other)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE account returns 204 when owned by the caller`() {
        whenever(deleteAccountUseCase.execute(1L, 1L)).thenReturn(true)

        mockMvc.perform(delete("/accounts/1").with(user(owner)))
            .andExpect(status().isNoContent)
    }
}
