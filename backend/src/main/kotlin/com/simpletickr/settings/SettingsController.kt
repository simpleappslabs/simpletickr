package com.simpletickr.settings

import com.simpletickr.generated.api.SettingsApi
import com.simpletickr.generated.model.Settings
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class SettingsController(
    private val userSettingsRepository: UserSettingsRepository,
) : SettingsApi {

    override fun getSettings(): ResponseEntity<Settings> =
        ResponseEntity.ok(userSettingsRepository.find().toModel())

    override fun updateSettings(settings: Settings): ResponseEntity<Settings> {
        userSettingsRepository.update(UserSettings(baseCurrency = settings.baseCurrency))
        return ResponseEntity.ok(settings)
    }

    private fun UserSettings.toModel() = Settings(baseCurrency = baseCurrency)
}
