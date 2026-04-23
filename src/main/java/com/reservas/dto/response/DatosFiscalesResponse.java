package com.reservas.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * DTO de respuesta con los datos fiscales del negocio.
 * Los datos provienen del metadata del Customer de Stripe.
 */
@Data
@Builder
public class DatosFiscalesResponse {

    private String tipoPersona;
    private String rfc;
    private String razonSocial;
    private String regimenFiscalClave;
    private String regimenFiscalDescripcion;
    private String codigoPostalFiscal;
    private String usoCfdiClave;
    private String usoCfdiDescripcion;
    private String emailFacturacion;
    private String domicilioCalle;
    private String domicilioColonia;
    private String domicilioMunicipio;
    private String domicilioEstado;
}
