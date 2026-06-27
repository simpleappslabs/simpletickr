package com.simpletickr.price.usecase

import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.price.SetPriceMappingCommand
import com.simpletickr.price.model.PriceProviderMapping
import com.simpletickr.price.persistence.PriceProviderMappingRepository
import com.simpletickr.sync.SyncTrigger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SetPriceMappingUseCase(
    private val listingRepository: ListingRepository,
    private val mappingRepository: PriceProviderMappingRepository,
    private val syncPricesUseCase: SyncPricesUseCase,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(command: SetPriceMappingCommand): PriceProviderMapping? {
        log.info("Setting price mapping for listing id={}: {}={}", command.listingId, command.provider, command.externalId)
        if (listingRepository.findById(command.listingId) == null) return null
        val mapping = mappingRepository.upsert(command.listingId, command.provider, command.externalId)
        syncPricesUseCase.execute(
            from = LocalDate.now().minusYears(1),
            listingId = command.listingId,
            trigger = SyncTrigger.MANUAL,
        )
        return mapping
    }
}
