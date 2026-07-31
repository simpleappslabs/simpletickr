package com.simpletickr.user.dashboard

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(DashboardController::class)
@Import(SecurityConfig::class)
class DashboardControllerIT {

    private val owner = CurrentUser(1L, "test-user", "hash")
    private val other = CurrentUser(2L, "other-user", "hash")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: DashboardService

    private val widget = DashboardWidget(
        id = 1L, type = DashboardWidgetType.PORTFOLIO_VALUE,
        config = PortfolioValueConfig(targetId = 10L, range = "1M"),
        label = "My Portfolio", currency = null,
    )

    @Test
    fun `GET widgets returns the current user's widgets`() {
        whenever(service.listWidgets(1L)).thenReturn(listOf(widget))

        mockMvc.perform(get("/dashboard/widgets").with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].label").value("My Portfolio"))
    }

    @Test
    fun `POST widget adds it for the current user`() {
        whenever(service.addWidget(eq(DashboardWidgetType.PORTFOLIO_VALUE), any(), eq(1L))).thenReturn(widget)

        mockMvc.perform(
            post("/dashboard/widgets")
                .with(user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"PORTFOLIO_VALUE","config":{"targetId":10,"range":"1M"}}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.label").value("My Portfolio"))
    }

    @Test
    fun `POST widget returns 400 when the referenced portfolio is not owned by the caller`() {
        whenever(service.addWidget(eq(DashboardWidgetType.PORTFOLIO_VALUE), any(), eq(2L)))
            .thenThrow(IllegalArgumentException("Portfolio 10 not found"))

        mockMvc.perform(
            post("/dashboard/widgets")
                .with(user(other))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"PORTFOLIO_VALUE","config":{"targetId":10,"range":"1M"}}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH widget returns 404 when owned by another user`() {
        whenever(service.updateWidgetRange(1L, "3M", 2L)).thenReturn(null)

        mockMvc.perform(
            patch("/dashboard/widgets/1")
                .with(user(other))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"config":{"range":"3M"}}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE widget returns 404 when owned by another user`() {
        whenever(service.removeWidget(1L, 2L)).thenReturn(false)

        mockMvc.perform(delete("/dashboard/widgets/1").with(user(other)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE widget returns 204 when owned by the caller`() {
        whenever(service.removeWidget(1L, 1L)).thenReturn(true)

        mockMvc.perform(delete("/dashboard/widgets/1").with(user(owner)))
            .andExpect(status().isNoContent)
    }
}
