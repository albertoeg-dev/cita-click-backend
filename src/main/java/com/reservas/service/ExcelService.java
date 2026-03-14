package com.reservas.service;

import com.reservas.dto.response.ReporteResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ExcelService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Brand colors (RGB bytes – Java signed bytes, POI masks with 0xFF) ────
    private static final byte[] INDIGO_700  = {(byte) 67, (byte) 56,  (byte) 202};
    private static final byte[] INDIGO_100  = {(byte) 224,(byte) 231, (byte) 255};
    private static final byte[] GREEN_700   = {(byte) 21, (byte) 128, (byte) 61};
    private static final byte[] GREEN_100   = {(byte) 220,(byte) 252, (byte) 231};
    private static final byte[] AMBER_700   = {(byte) 180,(byte) 83,  (byte) 9};
    private static final byte[] AMBER_100   = {(byte) 254,(byte) 243, (byte) 199};
    private static final byte[] SLATE_900   = {(byte) 15, (byte) 23,  (byte) 42};
    private static final byte[] SLATE_700   = {(byte) 51, (byte) 65,  (byte) 85};
    private static final byte[] SLATE_600   = {(byte) 71, (byte) 85,  (byte) 105};
    private static final byte[] SLATE_200   = {(byte) 226,(byte) 232, (byte) 240};
    private static final byte[] SLATE_50    = {(byte) 248,(byte) 250, (byte) 252};
    private static final byte[] WHITE       = {(byte) 255,(byte) 255, (byte) 255};

    // ── Column indices ────────────────────────────────────────────────────────
    private static final int COL_LABEL = 0;
    private static final int COL_VALUE = 1;
    private static final int COL_LAST  = 3; // 4 columns total (0-3) for KPI row

    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generarReporteExcel(ReporteResponse reporte, String nombreNegocio) {
        log.info("Generando reporte Excel para negocio: {}", nombreNegocio);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Reporte");
            sheet.setDefaultColumnWidth(22);

            // ── Pre-build common styles ────────────────────────────────────────
            XSSFCellStyle styleBrandRow   = buildStyle(wb, INDIGO_700,  WHITE,     10, true,  HorizontalAlignment.RIGHT,  VerticalAlignment.CENTER);
            XSSFCellStyle styleTitleRow   = buildStyle(wb, WHITE,       SLATE_900, 18, true,  HorizontalAlignment.LEFT,   VerticalAlignment.CENTER);
            XSSFCellStyle styleReportType = buildStyle(wb, INDIGO_100,  SLATE_700, 10, true,  HorizontalAlignment.LEFT,   VerticalAlignment.CENTER);
            XSSFCellStyle styleMeta       = buildStyle(wb, WHITE,       SLATE_600, 10, false, HorizontalAlignment.LEFT,   VerticalAlignment.CENTER);
            XSSFCellStyle styleKpiHeader  = buildStyle(wb, INDIGO_700,  WHITE,     10, true,  HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            XSSFCellStyle styleKpiValue   = buildStyle(wb, INDIGO_100,  INDIGO_700,18, true,  HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            XSSFCellStyle styleTblHeader  = buildStyle(wb, INDIGO_700,  WHITE,     11, true,  HorizontalAlignment.LEFT,   VerticalAlignment.CENTER);
            XSSFCellStyle styleTblHdrR    = buildStyle(wb, INDIGO_700,  WHITE,     11, true,  HorizontalAlignment.RIGHT,  VerticalAlignment.CENTER);
            XSSFCellStyle styleRowEven    = buildStyle(wb, WHITE,       SLATE_700, 10, false, HorizontalAlignment.LEFT,   VerticalAlignment.CENTER);
            XSSFCellStyle styleRowEvenR   = buildStyle(wb, WHITE,       SLATE_700, 10, false, HorizontalAlignment.RIGHT,  VerticalAlignment.CENTER);
            XSSFCellStyle styleRowOdd     = buildStyle(wb, SLATE_50,    SLATE_700, 10, false, HorizontalAlignment.LEFT,   VerticalAlignment.CENTER);
            XSSFCellStyle styleRowOddR    = buildStyle(wb, SLATE_50,    SLATE_700, 10, false, HorizontalAlignment.RIGHT,  VerticalAlignment.CENTER);
            XSSFCellStyle styleFooter     = buildStyle(wb, SLATE_50,    SLATE_600,  8, false, HorizontalAlignment.LEFT,   VerticalAlignment.CENTER);

            // ── ROW 0: Brand label ────────────────────────────────────────────
            Row r0 = sheet.createRow(0);
            r0.setHeightInPoints(20);
            setCell(r0, COL_LABEL, "CITA CLICK", styleBrandRow);

            // ── ROW 1: Business name ──────────────────────────────────────────
            Row r1 = sheet.createRow(1);
            r1.setHeightInPoints(38);
            setCell(r1, COL_LABEL, nombreNegocio, styleTitleRow);

            // ── ROW 2: Report type ────────────────────────────────────────────
            Row r2 = sheet.createRow(2);
            r2.setHeightInPoints(22);
            setCell(r2, COL_LABEL, "REPORTE DE CITAS", styleReportType);

            // ── ROW 3: Period ─────────────────────────────────────────────────
            Row r3 = sheet.createRow(3);
            r3.setHeightInPoints(20);
            setCell(r3, COL_LABEL, "Período:   " + formatearPeriodoLegible(reporte.getPeriodo()), styleMeta);

            // ── ROW 4: Date range ─────────────────────────────────────────────
            Row r4 = sheet.createRow(4);
            r4.setHeightInPoints(18);
            if (reporte.getFechaInicio() != null && reporte.getFechaFin() != null) {
                String fechas = "Fechas:   " + reporte.getFechaInicio().format(DATE_FORMATTER)
                        + "  –  " + reporte.getFechaFin().format(DATE_FORMATTER);
                setCell(r4, COL_LABEL, fechas, styleMeta);
            }

            int rowNum = 6; // blank separator

            // ── KPI SECTION ───────────────────────────────────────────────────
            String[] kpiLabels = {"Total de Citas", "Completadas", "Canceladas", "Pendientes"};
            int[]    kpiVals   = {reporte.getTotalCitas(), reporte.getCitasCompletadas(),
                    reporte.getCitasCanceladas(), reporte.getCitasPendientes()};

            Row kpiLblRow = sheet.createRow(rowNum++);
            kpiLblRow.setHeightInPoints(22);
            Row kpiValRow = sheet.createRow(rowNum++);
            kpiValRow.setHeightInPoints(34);

            for (int i = 0; i <= COL_LAST; i++) {
                setCell(kpiLblRow, i, kpiLabels[i], styleKpiHeader);
                setCell(kpiValRow, i, (double) kpiVals[i], styleKpiValue);
            }
            rowNum++; // space

            // ── INCOME HIGHLIGHT ──────────────────────────────────────────────
            boolean hasEstimado = reporte.getIngresoEstimado() != null
                    && reporte.getIngresoEstimado().compareTo(java.math.BigDecimal.ZERO) > 0;

            XSSFCellStyle styleIncLbl  = buildStyle(wb, GREEN_700,  WHITE,      10, true, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            XSSFCellStyle styleIncVal  = buildCurrencyStyle(wb, GREEN_100, GREEN_700);
            XSSFCellStyle styleEstLbl  = buildStyle(wb, AMBER_700,  WHITE,      10, true, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            XSSFCellStyle styleEstVal  = buildCurrencyStyle(wb, AMBER_100, AMBER_700);

            Row incLblRow = sheet.createRow(rowNum++);
            incLblRow.setHeightInPoints(20);
            Row incValRow = sheet.createRow(rowNum++);
            incValRow.setHeightInPoints(30);

            setCell(incLblRow, COL_LABEL, "INGRESO TOTAL", styleIncLbl);
            setCell(incValRow, COL_LABEL, reporte.getIngresoTotal().doubleValue(), styleIncVal);

            if (hasEstimado) {
                setCell(incLblRow, COL_VALUE, "INGRESO ESTIMADO", styleEstLbl);
                setCell(incValRow, COL_VALUE, reporte.getIngresoEstimado().doubleValue(), styleEstVal);
            }
            rowNum++; // space

            // ── DETAIL TABLE ──────────────────────────────────────────────────
            Row tblHdrRow = sheet.createRow(rowNum++);
            tblHdrRow.setHeightInPoints(24);
            setCell(tblHdrRow, COL_LABEL, "Concepto", styleTblHeader);
            setCell(tblHdrRow, COL_VALUE, "Valor",    styleTblHdrR);

            List<String[]> rows = buildDataRows(reporte);
            for (int i = 0; i < rows.size(); i++) {
                boolean isOdd = (i % 2 != 0);
                Row dataRow = sheet.createRow(rowNum++);
                dataRow.setHeightInPoints(20);
                setCell(dataRow, COL_LABEL, rows.get(i)[0], isOdd ? styleRowOdd  : styleRowEven);
                setCell(dataRow, COL_VALUE, rows.get(i)[1], isOdd ? styleRowOddR : styleRowEvenR);
            }
            rowNum++; // space

            // ── FOOTER ────────────────────────────────────────────────────────
            Row footerRow = sheet.createRow(rowNum);
            footerRow.setHeightInPoints(18);
            String footerText = "Generado el " + LocalDate.now().format(DATE_FORMATTER)
                    + "   ·   Cita Click – Sistema de Gestión de Citas";
            setCell(footerRow, COL_LABEL, footerText, styleFooter);

            // ── Column widths ─────────────────────────────────────────────────
            sheet.setColumnWidth(COL_LABEL, 42 * 256);
            sheet.setColumnWidth(COL_VALUE, 24 * 256);
            sheet.setColumnWidth(2,          24 * 256);
            sheet.setColumnWidth(COL_LAST,   24 * 256);

            // ── Freeze header rows ────────────────────────────────────────────
            sheet.createFreezePane(0, 6);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            log.info("✅ Reporte Excel generado exitosamente - {} bytes", baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("❌ Error al generar reporte Excel: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar reporte Excel: " + e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<String[]> buildDataRows(ReporteResponse r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Total de Citas",    String.valueOf(r.getTotalCitas())});
        rows.add(new String[]{"Citas Completadas", String.valueOf(r.getCitasCompletadas())});
        rows.add(new String[]{"Citas Canceladas",  String.valueOf(r.getCitasCanceladas())});
        rows.add(new String[]{"Citas Pendientes",  String.valueOf(r.getCitasPendientes())});
        rows.add(new String[]{"Ingreso Total",      String.format("$%.2f MXN", r.getIngresoTotal())});
        if (r.getIngresoEstimado() != null && r.getIngresoEstimado().compareTo(java.math.BigDecimal.ZERO) > 0) {
            rows.add(new String[]{"Ingreso Estimado", String.format("$%.2f MXN", r.getIngresoEstimado())});
        }
        rows.add(new String[]{"Clientes Totales", String.valueOf(r.getClientesTotales())});
        if (r.getClientesNuevos() != null) {
            rows.add(new String[]{"Clientes Nuevos", String.valueOf(r.getClientesNuevos())});
        }
        if (r.getServicioMasPopular() != null) {
            rows.add(new String[]{"Servicio Más Popular", r.getServicioMasPopular()});
        }
        return rows;
    }

    private XSSFCellStyle buildStyle(XSSFWorkbook wb, byte[] bg, byte[] fg,
                                     int fontSize, boolean bold,
                                     HorizontalAlignment hAlign, VerticalAlignment vAlign) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(bg, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(hAlign);
        style.setVerticalAlignment(vAlign);
        style.setWrapText(false);

        XSSFFont font = wb.createFont();
        font.setColor(new XSSFColor(fg, null));
        font.setFontHeightInPoints((short) fontSize);
        font.setBold(bold);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle buildCurrencyStyle(XSSFWorkbook wb, byte[] bg, byte[] fg) {
        XSSFCellStyle style = buildStyle(wb, bg, fg, 14, true,
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        // Custom number format: $1,234.56 MXN
        XSSFDataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("\"$\"#,##0.00\" MXN\""));
        return style;
    }

    private void setCell(Row row, int col, String value, XSSFCellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setCell(Row row, int col, double value, XSSFCellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String formatearPeriodoLegible(String periodo) {
        if (periodo == null || periodo.isBlank()) return "";
        String[] MESES = {"Enero","Febrero","Marzo","Abril","Mayo","Junio",
                "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        if (periodo.contains("-")) {
            String[] p = periodo.split("-");
            if (p.length == 2) {
                try { return MESES[Integer.parseInt(p[1]) - 1] + " " + p[0]; }
                catch (Exception ignored) {}
            }
        }
        return periodo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public byte[] generarReporteDiarioExcel(ReporteResponse reporte, String nombreNegocio, LocalDate fecha) {
        log.info("Generando reporte diario Excel para fecha: {}", fecha);
        return generarReporteExcel(reporte, nombreNegocio);
    }

    public byte[] generarReporteSemanalExcel(ReporteResponse reporte, String nombreNegocio) {
        log.info("Generando reporte semanal Excel");
        return generarReporteExcel(reporte, nombreNegocio);
    }

    public byte[] generarReporteMensualExcel(ReporteResponse reporte, String nombreNegocio) {
        log.info("Generando reporte mensual Excel");
        return generarReporteExcel(reporte, nombreNegocio);
    }
}
