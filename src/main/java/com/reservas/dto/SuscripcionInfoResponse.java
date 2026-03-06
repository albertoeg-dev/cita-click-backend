package com.reservas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para enviar información del estado de suscripción al cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuscripcionInfoResponse {

    private String plan;                        // starter, professional, enterprise
    private String estadoPago;                  // trial, activo, vencido, suspendido, pendiente_pago
    private boolean enPeriodoPrueba;
    private boolean cuentaActiva;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaFinPrueba;
    private LocalDateTime fechaProximoPago;
    private Integer diasRestantesPrueba;
    private Integer diasRestantesVencimiento;
    private boolean necesitaNotificacion;
    private boolean onboardingCompleto;
    private String mensaje;                     // Mensaje descriptivo del estado

    /**
     * Genera un mensaje descriptivo según el estado de la suscripción
     */
    public void generarMensaje() {
        if (!cuentaActiva) {
            if ("vencido".equals(estadoPago)) {
                this.mensaje = "Tu suscripción ha vencido. Por favor, realiza el pago para continuar usando el servicio.";
            } else if ("suspendido".equals(estadoPago)) {
                this.mensaje = "Tu cuenta está suspendida. Contacta con soporte para más información.";
            } else if ("pendiente_pago".equals(estadoPago)) {
                this.mensaje = "Tu cuenta requiere un pago inicial para activarse.";
            }
        } else if (enPeriodoPrueba) {
            if (diasRestantesPrueba != null) {
                if (diasRestantesPrueba == 0) {
                    this.mensaje = "Tu período de prueba termina hoy. ¡No olvides realizar tu pago!";
                } else if (diasRestantesPrueba == 1) {
                    this.mensaje = "Tu período de prueba termina mañana. Realiza tu pago para continuar sin interrupciones.";
                } else {
                    this.mensaje = String.format("Te quedan %d días de prueba gratuita.", diasRestantesPrueba);
                }
            }
        } else if ("activo".equals(estadoPago)) {
            if (diasRestantesVencimiento != null && diasRestantesVencimiento <= 5) {
                if (diasRestantesVencimiento == 0) {
                    this.mensaje = "Tu suscripción vence hoy. Por favor, realiza el pago para continuar.";
                } else if (diasRestantesVencimiento == 1) {
                    this.mensaje = "Tu suscripción vence mañana. Realiza tu pago para evitar interrupciones.";
                } else {
                    this.mensaje = String.format("Tu suscripción vence en %d días. Prepara tu pago.", diasRestantesVencimiento);
                }
            } else {
                this.mensaje = "Tu suscripción está activa. ¡Todo está en orden!";
            }
        }
    }
}
