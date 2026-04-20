package com.reservas.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta pública de un negocio para la página de reservas.
 * Solo incluye la información necesaria para que el cliente agende una cita.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicNegocioResponse {

    private UUID negocioId;
    private String nombre;
    private String descripcion;
    private String telefono;
    private String ciudad;
    private String logoUrl;
    private List<PublicServicioResponse> servicios;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicServicioResponse {
        private UUID id;
        private String nombre;
        private String descripcion;
        private Integer duracionMinutos;
        private BigDecimal precio;
    }
}
