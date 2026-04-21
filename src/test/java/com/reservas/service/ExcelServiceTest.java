package com.reservas.service;

import com.reservas.dto.response.ReporteResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para ExcelService
 */
@ExtendWith(MockitoExtension.class)
class ExcelServiceTest {

    @InjectMocks
    private ExcelService excelService;

    @Test
    void testGenerarReporteExcel_ConDatosCompletos() throws IOException {
        // Given
        ReporteResponse reporte = ReporteResponse.builder()
                .periodo("MENSUAL")
                .fechaInicio(LocalDate.of(2026, 1, 1))
                .fechaFin(LocalDate.of(2026, 1, 31))
                .totalCitas(50)
                .citasCompletadas(40)
                .citasCanceladas(5)
                .citasPendientes(5)
                .ingresoTotal(BigDecimal.valueOf(15000.00))
                .ingresoEstimado(BigDecimal.valueOf(1500.00))
                .clientesTotales(35)
                .clientesNuevos(10)
                .servicioMasPopular("Corte de Cabello")
                .build();

        String nombreNegocio = "Barbería El Clásico";

        // When
        byte[] excelBytes = excelService.generarReporteExcel(reporte, nombreNegocio);

        // Then
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        // Verificar que es un archivo Excel válido
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            assertNotNull(workbook);
            assertEquals(1, workbook.getNumberOfSheets());

            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Reporte", sheet.getSheetName());

            // Verificar brand label en row 0
            Row brandRow = sheet.getRow(0);
            assertNotNull(brandRow);
            assertEquals("CITA CLICK", brandRow.getCell(0).getStringCellValue());

            // Verificar nombre del negocio en row 1
            Row businessRow = sheet.getRow(1);
            assertNotNull(businessRow);
            assertEquals(nombreNegocio, businessRow.getCell(0).getStringCellValue());
        }
    }

    @Test
    void testGenerarReporteExcel_ConDatosMinimos() throws IOException {
        // Given
        ReporteResponse reporte = ReporteResponse.builder()
                .periodo("DIARIO")
                .totalCitas(10)
                .citasCompletadas(8)
                .citasCanceladas(2)
                .citasPendientes(0)
                .ingresoTotal(BigDecimal.valueOf(2000.00))
                .clientesTotales(8)
                .build();

        String nombreNegocio = "Spa Bienestar";

        // When
        byte[] excelBytes = excelService.generarReporteExcel(reporte, nombreNegocio);

        // Then
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertNotNull(sheet);
        }
    }

    @Test
    void testGenerarReporteExcel_VerificarFormatoDeCeldas() throws IOException {
        // Given
        ReporteResponse reporte = ReporteResponse.builder()
                .periodo("SEMANAL")
                .fechaInicio(LocalDate.of(2026, 1, 6))
                .fechaFin(LocalDate.of(2026, 1, 12))
                .totalCitas(20)
                .citasCompletadas(15)
                .citasCanceladas(3)
                .citasPendientes(2)
                .ingresoTotal(BigDecimal.valueOf(5000.00))
                .clientesTotales(15)
                .build();

        String nombreNegocio = "Salón Belleza Total";

        // When
        byte[] excelBytes = excelService.generarReporteExcel(reporte, nombreNegocio);

        // Then
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);

            // Verificar que las columnas tienen el ancho correcto
            assertTrue(sheet.getColumnWidth(0) > 0);
            assertTrue(sheet.getColumnWidth(1) > 0);
        }
    }

    @Test
    void testGenerarReporteExcel_ConIngresoEstimadoCero() throws IOException {
        // Given
        ReporteResponse reporte = ReporteResponse.builder()
                .periodo("MENSUAL")
                .fechaInicio(LocalDate.of(2026, 1, 1))
                .fechaFin(LocalDate.of(2026, 1, 31))
                .totalCitas(30)
                .citasCompletadas(25)
                .citasCanceladas(3)
                .citasPendientes(2)
                .ingresoTotal(BigDecimal.valueOf(7500.00))
                .ingresoEstimado(BigDecimal.ZERO)
                .clientesTotales(20)
                .build();

        String nombreNegocio = "Consultorio Dental";

        // When
        byte[] excelBytes = excelService.generarReporteExcel(reporte, nombreNegocio);

        // Then
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
    }

    @Test
    void testGenerarReporteExcel_SinFechas() throws IOException {
        // Given
        ReporteResponse reporte = ReporteResponse.builder()
                .periodo("PERSONALIZADO")
                .totalCitas(15)
                .citasCompletadas(12)
                .citasCanceladas(2)
                .citasPendientes(1)
                .ingresoTotal(BigDecimal.valueOf(3500.00))
                .clientesTotales(12)
                .build();

        String nombreNegocio = "Clínica Veterinaria";

        // When
        byte[] excelBytes = excelService.generarReporteExcel(reporte, nombreNegocio);

        // Then
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
    }

    @Test
    void testGenerarReporteExcel_ConServicioMasPopular() throws IOException {
        // Given
        ReporteResponse reporte = ReporteResponse.builder()
                .periodo("MENSUAL")
                .fechaInicio(LocalDate.of(2026, 1, 1))
                .fechaFin(LocalDate.of(2026, 1, 31))
                .totalCitas(100)
                .citasCompletadas(90)
                .citasCanceladas(5)
                .citasPendientes(5)
                .ingresoTotal(BigDecimal.valueOf(25000.00))
                .ingresoEstimado(BigDecimal.valueOf(2500.00))
                .clientesTotales(70)
                .clientesNuevos(20)
                .servicioMasPopular("Masaje Relajante")
                .build();

        String nombreNegocio = "Centro de Masajes Zen";

        // When
        byte[] excelBytes = excelService.generarReporteExcel(reporte, nombreNegocio);

        // Then
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
    }

    @Test
    void testGenerarReporteExcel_LanzaExcepcionConReporteNull() {
        // Given
        ReporteResponse reporte = null;
        String nombreNegocio = "Negocio Test";

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            excelService.generarReporteExcel(reporte, nombreNegocio);
        });
    }

    @Test
    void testGenerarReporteDiarioExcel_Exitoso() throws IOException {
        // Given
        ReporteResponse reporte = ReporteResponse.builder()
                .periodo("DIARIO")
                .fechaInicio(LocalDate.of(2026, 1, 15))
                .fechaFin(LocalDate.of(2026, 1, 15))
                .totalCitas(8)
                .citasCompletadas(6)
                .citasCanceladas(1)
                .citasPendientes(1)
                .ingresoTotal(BigDecimal.valueOf(1800.00))
                .clientesTotales(6)
                .clientesNuevos(2)
                .build();

        String nombreNegocio = "Clínica Dental";
        LocalDate fecha = LocalDate.of(2026, 1, 15);

        // When
        byte[] excelBytes = excelService.generarReporteDiarioExcel(reporte, nombreNegocio, fecha);

        // Then
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
    }

    @Test
    void testGenerarReporteSemanalExcel_Exitoso() throws IOException {
        // Given
        ReporteResponse reporte = ReporteResponse.builder()
                .periodo("SEMANAL")
                .fechaInicio(LocalDate.of(2026, 1, 13))
                .fechaFin(LocalDate.of(2026, 1, 19))
                .totalCitas(35)
                .citasCompletadas(30)
                .citasCanceladas(3)
                .citasPendientes(2)
                .ingresoTotal(BigDecimal.valueOf(7500.00))
                .clientesTotales(28)
                .clientesNuevos(8)
                .build();

        String nombreNegocio = "Gimnasio PowerFit";

        // When
        byte[] excelBytes = excelService.generarReporteSemanalExcel(reporte, nombreNegocio);

        // Then
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
    }

    @Test
    void testGenerarReporteMensualExcel_Exitoso() throws IOException {
        // Given
        ReporteResponse reporte = ReporteResponse.builder()
                .periodo("MENSUAL")
                .fechaInicio(LocalDate.of(2026, 1, 1))
                .fechaFin(LocalDate.of(2026, 1, 31))
                .totalCitas(120)
                .citasCompletadas(100)
                .citasCanceladas(10)
                .citasPendientes(10)
                .ingresoTotal(BigDecimal.valueOf(30000.00))
                .ingresoEstimado(BigDecimal.valueOf(3000.00))
                .clientesTotales(85)
                .clientesNuevos(25)
                .servicioMasPopular("Consulta General")
                .build();

        String nombreNegocio = "Consultorio Médico";

        // When
        byte[] excelBytes = excelService.generarReporteMensualExcel(reporte, nombreNegocio);

        // Then
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
    }
}
