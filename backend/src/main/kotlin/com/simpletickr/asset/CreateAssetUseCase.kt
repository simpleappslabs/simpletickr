package com.simpletickr.asset

import com.simpletickr.price.PriceProviderMappingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateAssetUseCase(
    private val assetRepository: AssetRepository,
    private val listingRepository: ListingRepository,
    private val mappingRepository: PriceProviderMappingRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(command: CreateAssetCommand): Asset {
        log.info("Creating asset: name={}, type={}, listings={}", command.name, command.type, command.listings.size)
        val saved = assetRepository.save(
            isin = command.isin,
            name = command.name,
            type = command.type,
        )
        for (listingCmd in command.listings) {
            val listing = listingRepository.save(saved.id, listingCmd.exchange, listingCmd.ticker, listingCmd.currency)
            listingCmd.priceMappings?.forEach { m ->
                mappingRepository.upsert(listing.id, m.provider, m.externalId)
            }
        }
        return assetRepository.findById(saved.id)!!
    }
}
