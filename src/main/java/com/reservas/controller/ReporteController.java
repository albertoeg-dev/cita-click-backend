package com.reservas.controller;

import com.reservas.dto.response.ApiResponse;
import com.reservas.dto.response.ReporteResponse;
import com.reservas.entity.Negocio;
import com.reservas.security.RequiresPlanFeature;
import com.reservas.service.ExcelService;
import com.reservas.service.PdfService;
import com.reservas.service.ReporteService;
import com.reservas.service.SuscripcionInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/reportes")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@Slf4j
public class ReporteController {

    @Autowired
    private ReporteService reporteService;

    @Autowired
    private PdfService pdfService;

    @Autowired
    private ExcelService excelService;

    @Autowired
    private SuscripcionInfoService suscripcionInfoService;

    @GetMapping("/diario")
    @RequiresPlanFeature(value = "reportes_avanzados", message = "Los reportes avanzados solo están disponibles en el plan Premium. Actualice su plan para acceder a esta funcionalidad.")
    public ResponseEntity<ApiResponse<ReporteResponse>> reporteDiario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.<ReporteResponse>builder()
                    .success(true).message("Reporte diario generado")
                    .data(reporteService.generarReporteDiario(auth.getName(), fecha)).build());
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.<ReporteResponse>builder()
                    .success(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/semanal")
    @RequiresPlanFeature(value = "reportes_avanzados", message = "Los reportes avanzados solo están disponibles en el plan Premium. Actualice su plan para acceder a esta funcionalidad.")
    public ResponseEntity<ApiResponse<ReporteResponse>> reporteSemanal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.<ReporteResponse>builder()
                    .success(true).message("Reporte semanal generado")
                    .data(reporteService.generarReporteSemanal(auth.getName(), fechaInicio)).build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<ReporteResponse>builder()
                    .success(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/mensual")
    @RequiresPlanFeature(value = "reportes_avanzados", message = "Los reportes avanzados solo están disponibles en el plan Premium. Actualice su plan para acceder a esta funcionalidad.")
    public ResponseEntity<ApiResponse<ReporteResponse>> reporteMensual(
            @RequestParam int mes,
            @RequestParam int anio,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.<ReporteResponse>builder()
                    .success(true).message("Reporte mensual generado")
                    .data(reporteService.generarReporteMensual(auth.getName(), mes, anio)).build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<ReporteResponse>builder()
                    .success(false).message(e.getMessage()).build());
        }
    }

    // ==================== ENDPOINTS DE EXPORTACIÓN PDF ====================

    @GetMapping("/diario/pdf")
    @RequiresPlanFeature(value = "reportes_avanzados", message = "La exportación de reportes a PDF solo está disponible en el plan Premium. Actualice su plan para acceder a esta funcionalidad.")
    public ResponseEntity<byte[]> reporteDiarioPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication auth) {
        try {
            String email = auth.getName();
            Negocio negocio = suscripcionInfoService.obtenerNegocioPorEmail(email);

            ReporteResponse reporte = reporteService.generarReporteDiario(email, fecha);
            byte[] pdfBytes = pdfService.generarReporteDiarioPdf(reporte, negocio.getNombre(), fecha);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    String.format("reporte_diario_%s.pdf", fecha.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
            headers.setContentLength(pdfBytes.length);

            log.info(" Reporte diario PDF descargado para fecha: {}", fecha);
            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            log.error(" Error al generar PDF: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/semanal/pdf")
    @RequiresPlanFeature(value = "reportes_avanzados", message = "La exportación de reportes a PDF solo está disponible en el plan Premium. Actualice su plan para acceder a esta funcionalidad.")
    public ResponseEntity<byte[]> reporteSemanalPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            Authentication auth) {
        try {
            String email = auth.getName();
            Negocio negocio = suscripcionInfoService.obtenerNegocioPorEmail(email);

            ReporteResponse reporte = reporteService.generarReporteSemanal(email, fechaInicio);
            byte[] pdfBytes = pdfService.generarReporteSemanalPdf(reporte, negocio.getNombre());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    String.format("reporte_semanal_%s.pdf", fechaInicio.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
            headers.setContentLength(pdfBytes.length);

            log.info(" Reporte semanal PDF descargado");
            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            log.error(" Error al generar PDF: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/mensual/pdf")
    @RequiresPlanFeature(value = "reportes_avanzados", message = "La exportación de reportes a PDF solo está disponible en el plan Premium. Actualice su plan para acceder a esta funcionalidad.")
    public ResponseEntity<byte[]> reporteMensualPdf(
            @RequestParam int mes,
            @RequestParam int anio,
            Authentication auth) {
        try {
            String email = auth.getName();
            Negocio negocio = suscripcionInfoService.obtenerNegocioPorEmail(email);

            ReporteResponse reporte = reporteService.generarReporteMensual(email, mes, anio);
            byte[] pdfBytes = pdfService.generarReporteMensualPdf(reporte, negocio.getNombre());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    String.format("reporte_mensual_%d-%02d.pdf", anio, mes));
            headers.setContentLength(pdfBytes.length);

            log.info(" Reporte mensual PDF descargado para {}/{}", mes, anio);
            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            log.error(" Error al generar PDF: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    // ==================== ENDPOINTS DE EXPORTACIÓN EXCEL ====================

    @GetMapping("/diario/excel")
    @RequiresPlanFeature(value = "reportes_avanzados", message = "La exportación de reportes a Excel solo está disponible en el plan Premium. Actualice su plan para acceder a esta funcionalidad.")
    public ResponseEntity<byte[]> reporteDiarioExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication auth) {
        try {
            String email = auth.getName();
            Negocio negocio = suscripcionInfoService.obtenerNegocioPorEmail(email);

            ReporteResponse reporte = reporteService.generarReporteDiario(email, fecha);
            byte[] excelBytes = excelService.generarReporteDiarioExcel(reporte, negocio.getNombre(), fecha);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    String.format("reporte_diario_%s.xlsx", fecha.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
            headers.setContentLength(excelBytes.length);

            log.info(" Reporte diario Excel descargado para fecha: {}", fecha);
            return ResponseEntity.ok().headers(headers).body(excelBytes);

        } catch (Exception e) {
            log.error(" Error al generar Excel: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/semanal/excel")
    @RequiresPlanFeature(value = "reportes_avanzados", message = "La exportación de reportes a Excel solo está disponible en el plan Premium. Actualice su plan para acceder a esta funcionalidad.")
    public ResponseEntity<byte[]> reporteSemanalExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            Authentication auth) {
        try {
            String email = auth.getName();
            Negocio negocio = suscripcionInfoService.obtenerNegocioPorEmail(email);

            ReporteResponse reporte = reporteService.generarReporteSemanal(email, fechaInicio);
            byte[] excelBytes = excelService.generarReporteSemanalExcel(reporte, negocio.getNombre());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    String.format("reporte_semanal_%s.xlsx", fechaInicio.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
            headers.setContentLength(excelBytes.length);

            log.info(" Reporte semanal Excel descargado");
            return ResponseEntity.ok().headers(headers).body(excelBytes);

        } catch (Exception e) {
            log.error(" Error al generar Excel: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/mensual/excel")
    @RequiresPlanFeature(value = "reportes_avanzados", message = "La exportación de reportes a Excel solo está disponible en el plan Premium. Actualice su plan para acceder a esta funcionalidad.")
    public ResponseEntity<byte[]> reporteMensualExcel(
            @RequestParam int mes,
            @RequestParam int anio,
            Authentication auth) {
        try {
            String email = auth.getName();
            Negocio negocio = suscripcionInfoService.obtenerNegocioPorEmail(email);

            ReporteResponse reporte = reporteService.generarReporteMensual(email, mes, anio);
            byte[] excelBytes = excelService.generarReporteMensualExcel(reporte, negocio.getNombre());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    String.format("reporte_mensual_%d-%02d.xlsx", anio, mes));
            headers.setContentLength(excelBytes.length);

            log.info(" Reporte mensual Excel descargado para {}/{}", mes, anio);
            return ResponseEntity.ok().headers(headers).body(excelBytes);

        } catch (Exception e) {
            log.error(" Error al generar Excel: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
}
