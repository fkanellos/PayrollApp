package com.fkcoding.PayrollApp.app.controller

import com.fkcoding.PayrollApp.app.service.PayrollCacheService
import com.fkcoding.PayrollApp.app.service.PdfGenerationService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/export")
@CrossOrigin(origins = ["http://localhost:3000"])
class ExportController(
    private val payrollCacheService: PayrollCacheService,
    private val pdfGenerationService: PdfGenerationService
) {

    @GetMapping("/payroll/{id}/pdf")
    fun downloadPdf(@PathVariable id: String): ResponseEntity<ByteArray> {
        val cached = payrollCacheService.retrieve(id)
            ?: return ResponseEntity.notFound().build()

        val pdfBytes = pdfGenerationService.generatePayrollPdf(cached.data)

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_PDF
            setContentDispositionFormData("attachment", "payroll-$id.pdf")
            contentLength = pdfBytes.size.toLong()
        }

        return ResponseEntity(pdfBytes, headers, HttpStatus.OK)
    }

}