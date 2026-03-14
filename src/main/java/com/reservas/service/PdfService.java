package com.reservas.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.reservas.dto.response.ReporteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class PdfService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Brand colors ──────────────────────────────────────────────────────────
    private static final DeviceRgb INDIGO_700   = new DeviceRgb(67,  56,  202); // header bg
    private static final DeviceRgb INDIGO_500   = new DeviceRgb(99,  102, 241); // accent
    private static final DeviceRgb INDIGO_100   = new DeviceRgb(224, 231, 255); // table header bg
    private static final DeviceRgb WHITE        = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb SLATE_900    = new DeviceRgb(15,  23,  42);
    private static final DeviceRgb SLATE_600    = new DeviceRgb(71,  85,  105);
    private static final DeviceRgb SLATE_50     = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(226, 232, 240);
    private static final DeviceRgb GREEN_600    = new DeviceRgb(22,  163, 74);
    private static final DeviceRgb GREEN_100    = new DeviceRgb(220, 252, 231);
    private static final DeviceRgb GREEN_700    = new DeviceRgb(21,  128, 61);
    private static final DeviceRgb RED_600      = new DeviceRgb(220, 38,  38);
    private static final DeviceRgb RED_100      = new DeviceRgb(254, 226, 226);
    private static final DeviceRgb AMBER_600    = new DeviceRgb(217, 119, 6);
    private static final DeviceRgb AMBER_100    = new DeviceRgb(254, 243, 199);

    // ── Page margins ─────────────────────────────────────────────────────────
    private static final float MARGIN_H = 36f;   // horizontal margin for content
    private static final float MARGIN_B = 36f;   // bottom margin

    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generarReportePdf(ReporteResponse reporte, String nombreNegocio) {
        log.info("Generando reporte PDF para negocio: {}", nombreNegocio);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter writer   = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            // Set page margins: top=0 so header is full-bleed, sides and bottom normal
            Document document  = new Document(pdfDoc);
            document.setMargins(0, 0, MARGIN_B, 0);

            // ── 1. HEADER ─────────────────────────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginBottom(20);

            Cell headerCell = new Cell()
                    .setBackgroundColor(INDIGO_700)
                    .setPaddingTop(30)
                    .setPaddingBottom(30)
                    .setPaddingLeft(MARGIN_H)
                    .setPaddingRight(MARGIN_H)
                    .setBorder(Border.NO_BORDER);

            headerCell.add(new Paragraph("REPORTE DE CITAS")
                    .setFontSize(9)
                    .setFontColor(INDIGO_100)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(6)
                    .setCharacterSpacing(1.5f));

            headerCell.add(new Paragraph(nombreNegocio)
                    .setFontSize(22)
                    .setFontColor(WHITE)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            String periodoTexto = formatearPeriodoLegible(reporte.getPeriodo());
            headerCell.add(new Paragraph(periodoTexto)
                    .setFontSize(11)
                    .setFontColor(INDIGO_100)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(4));

            if (reporte.getFechaInicio() != null && reporte.getFechaFin() != null) {
                String fechas = reporte.getFechaInicio().format(DATE_FORMATTER)
                        + "  –  " + reporte.getFechaFin().format(DATE_FORMATTER);
                headerCell.add(new Paragraph(fechas)
                        .setFontSize(9)
                        .setFontColor(INDIGO_100)
                        .setTextAlignment(TextAlignment.CENTER));
            }

            header.addCell(headerCell);
            document.add(header);

            // ── 2. KPI CARDS ──────────────────────────────────────────────────
            Table kpi = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginLeft(MARGIN_H)
                    .setMarginRight(MARGIN_H)
                    .setMarginBottom(18);

            addKpiCard(kpi, "Total Citas",     String.valueOf(reporte.getTotalCitas()),          INDIGO_100, INDIGO_700);
            addKpiCard(kpi, "Completadas",      String.valueOf(reporte.getCitasCompletadas()),    GREEN_100,  GREEN_700);
            addKpiCard(kpi, "Canceladas",       String.valueOf(reporte.getCitasCanceladas()),     RED_100,    RED_600);
            addKpiCard(kpi, "Pendientes",       String.valueOf(reporte.getCitasPendientes()),     AMBER_100,  AMBER_600);

            document.add(kpi);

            // ── 3. INCOME CARDS ───────────────────────────────────────────────
            boolean hasEstimado = reporte.getIngresoEstimado() != null
                    && reporte.getIngresoEstimado().compareTo(java.math.BigDecimal.ZERO) > 0;

            float[] incomeColWidths = hasEstimado ? new float[]{1, 1} : new float[]{1};
            Table incomeRow = new Table(UnitValue.createPercentArray(incomeColWidths))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginLeft(MARGIN_H)
                    .setMarginRight(MARGIN_H)
                    .setMarginBottom(22);

            addIncomeCard(incomeRow, "INGRESO TOTAL",
                    String.format("$%.2f MXN", reporte.getIngresoTotal()),
                    GREEN_100, GREEN_600, GREEN_700);

            if (hasEstimado) {
                addIncomeCard(incomeRow, "INGRESO ESTIMADO",
                        String.format("$%.2f MXN", reporte.getIngresoEstimado()),
                        AMBER_100, AMBER_600, AMBER_600);
            }

            document.add(incomeRow);

            // ── 4. DETAIL TABLE ───────────────────────────────────────────────
            document.add(new Paragraph("Detalle del Reporte")
                    .setFontSize(12)
                    .setBold()
                    .setFontColor(SLATE_900)
                    .setMarginLeft(MARGIN_H)
                    .setMarginRight(MARGIN_H)
                    .setMarginBottom(8));

            Table detail = new Table(UnitValue.createPercentArray(new float[]{3, 2}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginLeft(MARGIN_H)
                    .setMarginRight(MARGIN_H)
                    .setMarginBottom(20)
                    .setBorder(new SolidBorder(BORDER_COLOR, 1));

            // Table header
            detail.addHeaderCell(tableHeaderCell("Concepto", TextAlignment.LEFT));
            detail.addHeaderCell(tableHeaderCell("Valor",    TextAlignment.RIGHT));

            // Rows
            boolean alt = false;
            alt = addRow(detail, "Total de Citas",    String.valueOf(reporte.getTotalCitas()),         alt);
            alt = addRow(detail, "Citas Completadas", String.valueOf(reporte.getCitasCompletadas()),   alt);
            alt = addRow(detail, "Citas Canceladas",  String.valueOf(reporte.getCitasCanceladas()),    alt);
            alt = addRow(detail, "Citas Pendientes",  String.valueOf(reporte.getCitasPendientes()),    alt);
            alt = addRow(detail, "Ingreso Total",     String.format("$%.2f MXN", reporte.getIngresoTotal()), alt);

            if (hasEstimado) {
                alt = addRow(detail, "Ingreso Estimado",
                        String.format("$%.2f MXN", reporte.getIngresoEstimado()), alt);
            }

            alt = addRow(detail, "Clientes Totales", String.valueOf(reporte.getClientesTotales()), alt);

            if (reporte.getClientesNuevos() != null) {
                alt = addRow(detail, "Clientes Nuevos", String.valueOf(reporte.getClientesNuevos()), alt);
            }

            if (reporte.getServicioMasPopular() != null) {
                addRow(detail, "Servicio Más Popular", reporte.getServicioMasPopular(), alt);
            }

            document.add(detail);

            // ── 5. FOOTER ─────────────────────────────────────────────────────
            Table footer = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginLeft(MARGIN_H)
                    .setMarginRight(MARGIN_H);

            footer.addCell(new Cell()
                    .add(new Paragraph("Cita Click · Sistema de Gestión de Citas")
                            .setFontSize(8).setFontColor(SLATE_600))
                    .setBorder(Border.NO_BORDER)
                    .setBorderTop(new SolidBorder(BORDER_COLOR, 0.5f))
                    .setPaddingTop(8));

            footer.addCell(new Cell()
                    .add(new Paragraph("Generado el " + LocalDate.now().format(DATE_FORMATTER))
                            .setFontSize(8).setFontColor(SLATE_600)
                            .setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER)
                    .setBorderTop(new SolidBorder(BORDER_COLOR, 0.5f))
                    .setPaddingTop(8));

            document.add(footer);
            document.close();

            log.info("✅ Reporte PDF generado exitosamente - {} bytes", baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("❌ Error al generar reporte PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar reporte PDF: " + e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void addKpiCard(Table table, String label, String value,
                            DeviceRgb bgColor, DeviceRgb textColor) {
        Cell card = new Cell()
                .setBackgroundColor(bgColor)
                .setBorder(new SolidBorder(textColor, 1f))
                .setPadding(14)
                .setMargin(3)
                .setTextAlignment(TextAlignment.CENTER);

        card.add(new Paragraph(value)
                .setFontSize(24).setFontColor(textColor).setBold().setMarginBottom(2));
        card.add(new Paragraph(label)
                .setFontSize(8).setFontColor(textColor));

        table.addCell(card);
    }

    private void addIncomeCard(Table table, String title, String amount,
                               DeviceRgb bg, DeviceRgb borderColor, DeviceRgb textColor) {
        Cell card = new Cell()
                .setBackgroundColor(bg)
                .setBorder(new SolidBorder(borderColor, 1.5f))
                .setPadding(16)
                .setMargin(3)
                .setTextAlignment(TextAlignment.CENTER);

        card.add(new Paragraph(title)
                .setFontSize(8).setFontColor(textColor).setBold()
                .setCharacterSpacing(0.8f).setMarginBottom(6));
        card.add(new Paragraph(amount)
                .setFontSize(20).setFontColor(textColor).setBold());

        table.addCell(card);
    }

    private Cell tableHeaderCell(String text, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontSize(10)
                        .setTextAlignment(align))
                .setBackgroundColor(INDIGO_100)
                .setPaddingTop(10).setPaddingBottom(10)
                .setPaddingLeft(12).setPaddingRight(12)
                .setBorderTop(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER)
                .setBorderRight(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(INDIGO_500, 1.5f));
    }

    private boolean addRow(Table table, String label, String value, boolean alternate) {
        DeviceRgb bg = alternate ? SLATE_50 : WHITE;

        Cell c1 = new Cell()
                .add(new Paragraph(label).setFontSize(10).setFontColor(SLATE_900))
                .setBackgroundColor(bg)
                .setPaddingTop(9).setPaddingBottom(9)
                .setPaddingLeft(12).setPaddingRight(8)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(BORDER_COLOR, 0.5f));

        Cell c2 = new Cell()
                .add(new Paragraph(value).setFontSize(10).setFontColor(SLATE_900)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(bg)
                .setPaddingTop(9).setPaddingBottom(9)
                .setPaddingLeft(8).setPaddingRight(12)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(BORDER_COLOR, 0.5f));

        table.addCell(c1);
        table.addCell(c2);
        return !alternate;
    }

    private String formatearPeriodoLegible(String periodo) {
        if (periodo == null || periodo.isBlank()) return "";
        String[] MESES = {"Enero","Febrero","Marzo","Abril","Mayo","Junio",
                "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        if (periodo.contains("-")) {
            String[] parts = periodo.split("-");
            if (parts.length == 2) {
                try {
                    return MESES[Integer.parseInt(parts[1]) - 1] + " " + parts[0];
                } catch (Exception ignored) {}
            }
        }
        return periodo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public byte[] generarReporteDiarioPdf(ReporteResponse reporte, String nombreNegocio, LocalDate fecha) {
        log.info("Generando reporte diario PDF para fecha: {}", fecha);
        return generarReportePdf(reporte, nombreNegocio);
    }

    public byte[] generarReporteSemanalPdf(ReporteResponse reporte, String nombreNegocio) {
        log.info("Generando reporte semanal PDF");
        return generarReportePdf(reporte, nombreNegocio);
    }

    public byte[] generarReporteMensualPdf(ReporteResponse reporte, String nombreNegocio) {
        log.info("Generando reporte mensual PDF");
        return generarReportePdf(reporte, nombreNegocio);
    }
}
