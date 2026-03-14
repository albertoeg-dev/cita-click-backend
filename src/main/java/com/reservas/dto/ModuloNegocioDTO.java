package com.reservas.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Módulo del marketplace con estado activo/inactivo para un negocio específico.
 * Usado en la vista del marketplace para mostrar qué módulos tiene contratados el negocio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuloNegocioDTO {

    private UUID moduloId;
    private String clave;
    private String nombre;
    private String descripcion;
    private BigDecimal precioMensual;

    /** true si el módulo está activo (comprado individualmente o incluido en plan) */
    private boolean activado;

    /** true si el módulo está activo por estar incluido en el Plan COMPLETO (no comprado por separado) */
    private boolean incluidoEnPlan;

    private LocalDateTime fechaActivacion;

    /** Fecha en que se canceló el módulo, null si sigue activo */
    private LocalDateTime fechaCancelacion;

    /** ID de suscripción Stripe que factura este módulo, null si no está activo */
    private String stripeSubscriptionId;

    /** Próxima fecha estimada de cobro, calculada mensualmente desde fechaActivacion */
    private LocalDate proximaFechaCobro;
}
