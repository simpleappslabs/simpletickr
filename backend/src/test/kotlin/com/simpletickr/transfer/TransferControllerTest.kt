package com.simpletickr.transfer

import com.simpletickr.account.AccountService
import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

@WebMvcTest(TransferController::class)
class TransferControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var recordTransferUseCase: RecordTransferUseCase
    @MockitoBean private lateinit var deleteTransferUseCase: DeleteTransferUseCase
    @MockitoBean private lateinit var transferRepository: TransferRepository
    @MockitoBean private lateinit var accountService: AccountService

    private val sourceAccount = Account(id = 1L, name = "Exchange", broker = null, accountType = AccountType.CRYPTO, currency = null, accountNumber = null, institution = null)
    private val destinationAccount = Account(id = 2L, name = "Cold Wallet", broker = null, accountType = AccountType.CRYPTO, currency = null, accountNumber = null, institution = null)

    private val sample = Transfer(
        id = 900L, portfolioId = 10L, listingId = 5L, assetId = 2L,
        quantity = BigDecimal("1.0"), assetFeeQuantity = BigDecimal("0.005"),
        date = LocalDate.of(2024, 6, 1), sourceAccountId = 1L, destinationAccountId = 2L,
    )

    @BeforeEach
    fun stubAccounts() {
        whenever(accountService.listAccounts()).thenReturn(listOf(sourceAccount, destinationAccount))
        whenever(accountService.getAccount(1L)).thenReturn(sourceAccount)
        whenever(accountService.getAccount(2L)).thenReturn(destinationAccount)
    }

    @Test
    fun `GET transfers for portfolio returns 200`() {
        whenever(transferRepository.findAllForPortfolio(10L)).thenReturn(listOf(sample))

        mockMvc.perform(get("/portfolios/10/transfers"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(900))
            .andExpect(jsonPath("$[0].sourceAccount.name").value("Exchange"))
            .andExpect(jsonPath("$[0].destinationAccount.name").value("Cold Wallet"))
    }

    @Test
    fun `POST transfer returns 201 with no price field`() {
        whenever(recordTransferUseCase.execute(eq(10L), any())).thenReturn(sample)

        mockMvc.perform(
            post("/portfolios/10/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"listingId":5,"quantity":1.0,"assetFeeQuantity":0.005,"date":"2024-06-01","sourceAccountId":1,"destinationAccountId":2}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(900))
            .andExpect(jsonPath("$.quantity").value(1.0))
            .andExpect(jsonPath("$.price").doesNotExist())
    }

    @Test
    fun `POST transfer returns 400 when use case throws`() {
        whenever(recordTransferUseCase.execute(eq(10L), any()))
            .thenThrow(IllegalArgumentException("Cannot transfer 1.0: only 0.5 held"))

        mockMvc.perform(
            post("/portfolios/10/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"listingId":5,"quantity":1.0,"date":"2024-06-01","sourceAccountId":1,"destinationAccountId":2}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE transfer returns 204`() {
        whenever(deleteTransferUseCase.execute(10L, 900L)).thenReturn(true)

        mockMvc.perform(delete("/portfolios/10/transfers/900"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE transfer returns 404 when not found`() {
        whenever(deleteTransferUseCase.execute(10L, 999L)).thenReturn(false)

        mockMvc.perform(delete("/portfolios/10/transfers/999"))
            .andExpect(status().isNotFound)
    }
}
