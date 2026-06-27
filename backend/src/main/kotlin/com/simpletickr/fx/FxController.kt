package com.simpletickr.fx

import com.simpletickr.fx.usecase.SyncFxRatesUseCase
import com.simpletickr.generated.api.FXApi
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.sync.SyncTrigger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import com.simpletickr.generated.model.FxRatePoint as FxRatePointModel
import com.simpletickr.generated.model.SyncResult as SyncResultModel

@RestController
class FxController(
    private val fxRateService: FxRateService,
    private val syncFxRatesUseCase: SyncFxRatesUseCase,
) : FXApi {

    override fun lookupFxRate(base: String, quote: String, date: LocalDate): ResponseEntity<FxRatePointModel> {
        val baseCcy = try { CurrencyCode(base) } catch (_: IllegalArgumentException) { return ResponseEntity.badRequest().build() }
        val quoteCcy = try { CurrencyCode(quote) } catch (_: IllegalArgumentException) { return ResponseEntity.badRequest().build() }
        val result = fxRateService.lookupOrFetch(baseCcy, quoteCcy, date)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(FxRatePointModel(rate = result.rate.toDouble(), date = result.date))
    }

    override fun syncFxRates(from: LocalDate?, to: LocalDate?): ResponseEntity<SyncResultModel> {
        val result = syncFxRatesUseCase.execute(from, to, SyncTrigger.MANUAL)
        return ResponseEntity.ok(SyncResultModel(synced = result.synced, failed = result.failed))
    }
}
