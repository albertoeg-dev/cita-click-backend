package com.reservas.service;

import com.reservas.dto.request.DatosFiscalesRequest;
import com.reservas.dto.response.DatosFiscalesResponse;
import com.reservas.entity.Negocio;
import com.reservas.exception.NotFoundException;
import com.reservas.repository.NegocioRepository;
import com.reservas.repository.UsuarioRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerUpdateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Servicio para gestionar datos fiscales (CFDI 4.0 México).
 * Los datos se almacenan en el metadata del Customer de Stripe para
 * facilitar la consulta manual al momento de emitir facturas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatosFiscalesService {

    @Value("${stripe.api.key}")
    private String stripeSecretKey;

    private final UsuarioRepository usuarioRepository;
    private final NegocioRepository negocioRepository;

    @PostConstruct
    private void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    /**
     * Guarda o actualiza los datos fiscales del negocio en el metadata del Customer de Stripe.
     */
    public DatosFiscalesResponse guardar(String emailUsuario, DatosFiscalesRequest request) {
        Negocio negocio = obtenerNegocio(emailUsuario);

        if (negocio.getStripeCustomerId() == null) {
            throw new IllegalStateException("El negocio no tiene un Customer de Stripe asociado. Realiza una compra primero.");
        }

        try {
            CustomerUpdateParams params = CustomerUpdateParams.builder()
                    .putMetadata("factura_tipo_persona",           request.getTipoPersona())
                    .putMetadata("factura_rfc",                    request.getRfc().toUpperCase())
                    .putMetadata("factura_razon_social",           request.getRazonSocial())
                    .putMetadata("factura_regimen_fiscal_clave",   request.getRegimenFiscalClave())
                    .putMetadata("factura_regimen_fiscal_desc",    nvl(request.getRegimenFiscalDescripcion()))
                    .putMetadata("factura_cp_fiscal",              request.getCodigoPostalFiscal())
                    .putMetadata("factura_uso_cfdi_clave",         request.getUsoCfdiClave())
                    .putMetadata("factura_uso_cfdi_desc",          nvl(request.getUsoCfdiDescripcion()))
                    .putMetadata("factura_email",                  request.getEmailFacturacion())
                    .putMetadata("factura_domicilio_calle",        nvl(request.getDomicilioCalle()))
                    .putMetadata("factura_domicilio_colonia",      nvl(request.getDomicilioColonia()))
                    .putMetadata("factura_domicilio_municipio",    nvl(request.getDomicilioMunicipio()))
                    .putMetadata("factura_domicilio_estado",       nvl(request.getDomicilioEstado()))
                    .build();

            Customer customer = Customer.retrieve(negocio.getStripeCustomerId());
            customer.update(params);

            log.info("[DatosFiscales] Metadata actualizada en Stripe Customer {} para negocio {}",
                    negocio.getStripeCustomerId(), negocio.getNombre());

            return toResponse(request, negocio.getStripeCustomerId());

        } catch (StripeException e) {
            log.error("[DatosFiscales] Error al actualizar Customer en Stripe: {}", e.getMessage());
            throw new RuntimeException("Error al guardar datos fiscales en Stripe: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene los datos fiscales actuales del Customer de Stripe.
     */
    public DatosFiscalesResponse obtener(String emailUsuario) {
        Negocio negocio = obtenerNegocio(emailUsuario);

        if (negocio.getStripeCustomerId() == null) {
            return null; // Sin Customer de Stripe aún = sin datos fiscales
        }

        try {
            Customer customer = Customer.retrieve(negocio.getStripeCustomerId());
            Map<String, String> meta = customer.getMetadata();

            if (meta == null || !meta.containsKey("factura_rfc")) {
                return null; // No hay datos fiscales registrados
            }

            return DatosFiscalesResponse.builder()
                    .tipoPersona(meta.get("factura_tipo_persona"))
                    .rfc(meta.get("factura_rfc"))
                    .razonSocial(meta.get("factura_razon_social"))
                    .regimenFiscalClave(meta.get("factura_regimen_fiscal_clave"))
                    .regimenFiscalDescripcion(meta.get("factura_regimen_fiscal_desc"))
                    .codigoPostalFiscal(meta.get("factura_cp_fiscal"))
                    .usoCfdiClave(meta.get("factura_uso_cfdi_clave"))
                    .usoCfdiDescripcion(meta.get("factura_uso_cfdi_desc"))
                    .emailFacturacion(meta.get("factura_email"))
                    .domicilioCalle(meta.get("factura_domicilio_calle"))
                    .domicilioColonia(meta.get("factura_domicilio_colonia"))
                    .domicilioMunicipio(meta.get("factura_domicilio_municipio"))
                    .domicilioEstado(meta.get("factura_domicilio_estado"))
                    .build();

        } catch (StripeException e) {
            log.error("[DatosFiscales] Error al obtener Customer de Stripe: {}", e.getMessage());
            throw new RuntimeException("Error al obtener datos fiscales: " + e.getMessage(), e);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private Negocio obtenerNegocio(String emailUsuario) {
        var usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + emailUsuario));
        return negocioRepository.findById(usuario.getNegocio().getId())
                .orElseThrow(() -> new NotFoundException("Negocio no encontrado"));
    }

    private DatosFiscalesResponse toResponse(DatosFiscalesRequest req, String stripeCustomerId) {
        return DatosFiscalesResponse.builder()
                .tipoPersona(req.getTipoPersona())
                .rfc(req.getRfc().toUpperCase())
                .razonSocial(req.getRazonSocial())
                .regimenFiscalClave(req.getRegimenFiscalClave())
                .regimenFiscalDescripcion(req.getRegimenFiscalDescripcion())
                .codigoPostalFiscal(req.getCodigoPostalFiscal())
                .usoCfdiClave(req.getUsoCfdiClave())
                .usoCfdiDescripcion(req.getUsoCfdiDescripcion())
                .emailFacturacion(req.getEmailFacturacion())
                .domicilioCalle(req.getDomicilioCalle())
                .domicilioColonia(req.getDomicilioColonia())
                .domicilioMunicipio(req.getDomicilioMunicipio())
                .domicilioEstado(req.getDomicilioEstado())
                .build();
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
