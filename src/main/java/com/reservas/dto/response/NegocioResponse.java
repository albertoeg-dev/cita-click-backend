package com.reservas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegocioResponse {
    private String id;
    private String nombre;
    private String descripcion;
    private String email;
    private String telefono;
    private String tipo;

    // Legacy flat fields for backward compatibility
    private String domicilio;
    private String ciudad;
    private String pais;

    // Nested direccion object for frontend
    private DireccionResponse direccion;

    private String plan;
    private String estadoPago;
    private boolean onboardingCompleto;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DireccionResponse {
        private String calle;
        private String colonia;
        private String ciudad;
        private String codigoPostal;
        private String estado;
        private String pais;
    }
}