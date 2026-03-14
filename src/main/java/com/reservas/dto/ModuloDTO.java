package com.reservas.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representa un módulo del catálogo del marketplace.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuloDTO {

    private UUID id;
    private String clave;
    private String nombre;
    private String descripcion;
    private BigDecimal precioMensual;
    private boolean activo;
}
