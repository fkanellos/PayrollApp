package com.fkcoding.PayrollApp.app.service

import com.fkcoding.PayrollApp.app.controller.PayrollResponse
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class PdfGenerationService {

    private fun getGreekFont(): PdfFont {
        // Τοποθέτησε το αρχείο "DejaVuSans.ttf" στο src/main/resources/fonts/
        val resource = ClassPathResource("fonts/ttf/DejaVuLGCSans.ttf")
        val fontBytes = resource.inputStream.use { it.readBytes() }
        return PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
    }

    fun generatePayrollPdf(payroll: PayrollResponse): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val writer = PdfWriter(outputStream)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        val font = getGreekFont()

        addHeader(document, payroll, font)
        addSummary(document, payroll, font)
        addClientBreakdown(document, payroll, font)
        addFooter(document, payroll, font)

        document.close()
        return outputStream.toByteArray()
    }

    private fun addHeader(document: Document, payroll: PayrollResponse, font: PdfFont) {
        document.add(
            Paragraph("ΑΝΑΦΟΡΑ ΜΙΣΘΟΔΟΣΙΑΣ")
                .setFont(font)
                .setFontSize(20f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
        )
        document.add(Paragraph("").setFont(font))
        document.add(Paragraph("Εργαζόμενος: ${payroll.employee.name}").setFont(font))
        document.add(Paragraph("Email: ${payroll.employee.email}").setFont(font))
        document.add(Paragraph("Περίοδος: ${payroll.period}").setFont(font))
        document.add(Paragraph("Δημιουργήθηκε: ${payroll.generatedAt}").setFont(font))
        document.add(Paragraph("").setFont(font))
    }

    private fun addSummary(document: Document, payroll: PayrollResponse, font: PdfFont) {
        document.add(
            Paragraph("ΣΥΓΚΕΝΤΡΩΤΙΚΑ ΣΤΟΙΧΕΙΑ")
                .setFont(font)
                .setFontSize(16f)
                .setBold()
        )

        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(3f, 2f)))
            .useAllAvailableWidth()

        summaryTable.addCell(Cell().add(Paragraph("Συνολικές Συνεδρίες").setFont(font)))
        summaryTable.addCell(Cell().add(Paragraph(payroll.summary.totalSessions.toString()).setFont(font)))

        summaryTable.addCell(Cell().add(Paragraph("Συνολικά Έσοδα").setFont(font)))
        summaryTable.addCell(Cell().add(Paragraph("€${payroll.summary.totalRevenue}").setFont(font)))

        summaryTable.addCell(Cell().add(Paragraph("Μισθός Εργαζομένου").setFont(font)))
        summaryTable.addCell(Cell().add(Paragraph("€${payroll.summary.employeeEarnings}").setFont(font)))

        summaryTable.addCell(Cell().add(Paragraph("Κέρδη Εταιρίας").setFont(font)))
        summaryTable.addCell(Cell().add(Paragraph("€${payroll.summary.companyEarnings}").setFont(font)))

        document.add(summaryTable)
        document.add(Paragraph("").setFont(font))
    }

    private fun addClientBreakdown(document: Document, payroll: PayrollResponse, font: PdfFont) {
        document.add(
            Paragraph("ΑΝΑΛΥΤΙΚΑ ΑΝΑ ΠΕΛΑΤΗ")
                .setFont(font)
                .setFontSize(16f)
                .setBold()
        )

        payroll.clientBreakdown.forEach { client ->
            document.add(Paragraph(client.clientName).setFont(font).setBold())

            val clientTable = Table(UnitValue.createPercentArray(floatArrayOf(3f, 2f)))
                .useAllAvailableWidth()

            clientTable.addCell(Cell().add(Paragraph("Συνεδρίες").setFont(font)))
            clientTable.addCell(Cell().add(Paragraph(client.sessions.toString()).setFont(font)))

            clientTable.addCell(Cell().add(Paragraph("Τιμή/Συνεδρία").setFont(font)))
            clientTable.addCell(Cell().add(Paragraph("€${client.pricePerSession}").setFont(font)))

            clientTable.addCell(Cell().add(Paragraph("Συνολικά Έσοδα").setFont(font)))
            clientTable.addCell(Cell().add(Paragraph("€${client.totalRevenue}").setFont(font)))

            clientTable.addCell(Cell().add(Paragraph("Κέρδη Εργαζομένου").setFont(font)))
            clientTable.addCell(Cell().add(Paragraph("€${client.employeeEarnings}").setFont(font)))

            clientTable.addCell(Cell().add(Paragraph("Κέρδη Εταιρίας").setFont(font)))
            clientTable.addCell(Cell().add(Paragraph("€${client.companyEarnings}").setFont(font)))

            document.add(clientTable)
            document.add(Paragraph("").setFont(font))
        }
    }

    private fun addFooter(document: Document, payroll: PayrollResponse, font: PdfFont) {
        document.add(
            Paragraph("---")
                .setFont(font)
                .setTextAlignment(TextAlignment.CENTER)
        )
        document.add(
            Paragraph("Δημιουργήθηκε από το Σύστημα Μισθοδοσίας")
                .setFont(font)
                .setFontSize(10f)
                .setTextAlignment(TextAlignment.CENTER)
        )
    }
}
