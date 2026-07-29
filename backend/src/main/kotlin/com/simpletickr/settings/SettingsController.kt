package com.simpletickr.settings

import com.simpletickr.auth.currentUser
import com.simpletickr.generated.api.SettingsApi
import com.simpletickr.generated.model.Settings
import com.simpletickr.shared.CurrencyCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class SettingsController(
    private val settingsService: SettingsService,
) : SettingsApi {

    override fun getSettings(): ResponseEntity<Settings> =
        ResponseEntity.ok(settingsService.getSettings(currentUser().id).toModel())

    override fun updateSettings(settings: Settings): ResponseEntity<Settings> {
        val code = try { CurrencyCode(settings.baseCurrency) }
                   catch (_: IllegalArgumentException) { return ResponseEntity.badRequest().build() }
        settingsService.updateSettings(currentUser().id, UserSettings(baseCurrency = code))
        return ResponseEntity.ok(settings)
    }

    private fun UserSettings.toModel() = Settings(baseCurrency = baseCurrency.value)
}
