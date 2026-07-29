package com.simpletickr.settings

import org.springframework.stereotype.Service

@Service
class SettingsService(private val userSettingsRepository: UserSettingsRepository) {

    fun getSettings(userId: Long): UserSettings = userSettingsRepository.find(userId)

    fun updateSettings(userId: Long, settings: UserSettings) {
        userSettingsRepository.update(userId, settings)
    }
}
