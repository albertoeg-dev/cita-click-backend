package com.reservas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO para guardar / actualizar datos fiscales del negocio (CFDI 4.0 México).
 */
@Data
public class DatosFiscalesRequest {

    @NotBlank(message = "El tipo de persona es requerido")
    @Pattern(regexp = "FISICA|MORAL", message = "Tipo de persona debe ser FISICA o MORAL")
    private String tipoPersona;

    @NotBlank(message = "El RFC es requerido")
    @Pattern(
        regexp = "[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}",
        message = "RFC inválido. Formato esperado: 3-4 letras, 6 dígitos, 3 alfanuméricos"
    )
    private String rfc;

    @NotBlank(message = "La Razón Social es requerida")
    @Size(max = 300, message = "La Razón Social no puede exceder 300 caracteres")
    private String razonSocial;

    @NotBlank(message = "El Régimen Fiscal es requerido")
    private String regimenFiscalClave;

    private String regimenFiscalDescripcion;

    @NotBlank(message = "El Código Postal fiscal es requerido")
    @Pattern(regexp = "\\d{5}", message = "El Código Postal debe tener 5 dígitos")
    private String codigoPostalFiscal;

    @NotBlank(message = "El Uso CFDI es requerido")
    private String usoCfdiClave;

    private String usoCfdiDescripcion;

    @NotBlank(message = "El correo para facturación es requerido")
    @Email(message = "El correo para facturación no es válido")
    private String emailFacturacion;

    // Domicilio fiscal (opcional)
    private String domicilioCalle;
    private String domicilioColonia;
    private String domicilioMunicipio;
    private String domicilioEstado;
}
