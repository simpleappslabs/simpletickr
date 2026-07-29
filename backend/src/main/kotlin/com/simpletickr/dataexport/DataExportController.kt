package com.simpletickr.dataexport

import com.fasterxml.jackson.databind.ObjectMapper
import com.simpletickr.auth.currentUser
import com.simpletickr.dataexport.model.ImportAnalysis
import com.simpletickr.dataexport.model.ImportResult
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

@RestController
class DataExportController(
    private val exportService: ExportService,
    private val importDataUseCase: ImportDataUseCase,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping("/data-export")
    fun exportData(
        @RequestParam("portfolioIds", required = false) portfolioIds: List<Long>?,
    ): ResponseEntity<ByteArray> {
        val export = exportService.buildExport(currentUser().id, portfolioIds)
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export)
        val filename = "simpletickr-export-${LocalDate.now()}.json"
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
    }

    @PostMapping("/data-import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importData(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("dryRun", required = false, defaultValue = "false") dryRun: Boolean,
    ): ResponseEntity<*> =
        if (dryRun) ResponseEntity.ok<ImportAnalysis>(importDataUseCase.analyze(file.bytes, currentUser().id))
        else ResponseEntity.ok<ImportResult>(importDataUseCase.apply(file.bytes, currentUser().id))
}
