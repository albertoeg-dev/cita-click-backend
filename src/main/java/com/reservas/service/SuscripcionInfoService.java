package com.reservas.service;

import com.reservas.dto.SuscripcionInfoResponse;
import com.reservas.entity.Negocio;
import com.reservas.entity.Usuario;
import com.reservas.repository.UsuarioRepository;
import com.reservas.service.ModuloService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio dedicado para obtener información de suscripciones.
 * Maneja correctamente las transacciones y previene LazyInitializationException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuscripcionInfoService {

    private final UsuarioRepository usuarioRepository;
    private final ModuloService moduloService;

    /**
     * Obtiene la información completa de suscripción del usuario.
     *
     * @param email Email del usuario autenticado
     * @return Response con información de la suscripción
     * @throws RuntimeException si el usuario o negocio no existen
     */
    @Transactional(readOnly = true)
    public SuscripcionInfoResponse obtenerInfoSuscripcion(String email) {
        log.info("[SuscripcionInfoService] Obteniendo información de suscripción para: {}", email);

        // JOIN FETCH previene LazyInitializationException
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> {
                    log.error("[SuscripcionInfoService] Usuario no encontrado: {}", email);
                    return new RuntimeException("Usuario no encontrado");
                });

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            log.error("[SuscripcionInfoService] Usuario sin negocio asociado: {}", email);
            throw new RuntimeException("El usuario no tiene un negocio asociado");
        }

        // Dentro del contexto transaccional, todos los accesos son seguros
        SuscripcionInfoResponse response = SuscripcionInfoResponse.builder()
                .plan(negocio.getPlan())
                .estadoPago(negocio.getEstadoPago())
                .enPeriodoPrueba(negocio.isEnPeriodoPrueba())
                .cuentaActiva(negocio.isCuentaActiva())
                .fechaRegistro(negocio.getFechaRegistro())
                .fechaFinPrueba(negocio.getFechaFinPrueba())
                .fechaProximoPago(negocio.getFechaProximoCobro())
                .diasRestantesPrueba(negocio.isEnPeriodoPrueba() ? (int) negocio.diasRestantesPrueba() : null)
                .diasRestantesVencimiento(!negocio.isEnPeriodoPrueba() && "activo".equals(negocio.getEstadoPago())
                        ? (int) negocio.diasRestantesVencimiento() : null)
                .necesitaNotificacion(negocio.necesitaNotificacionPrueba() || negocio.necesitaNotificacionVencimiento())
                .modulosActivos(moduloService.listarModulosConEstado(negocio.getId()))
                .build();

        // Generar mensaje descriptivo
        response.generarMensaje();

        log.info("[SuscripcionInfoService] Información obtenida exitosamente - Plan: {}, Estado: {}",
                negocio.getPlan(), negocio.getEstadoPago());

        return response;
    }

    /**
     * Obtiene el negocio del usuario autenticado.
     *
     * @param email Email del usuario
     * @return Negocio asociado al usuario
     * @throws RuntimeException si el usuario o negocio no existen
     */
    @Transactional(readOnly = true)
    public Negocio obtenerNegocioPorEmail(String email) {
        log.debug("[SuscripcionInfoService] Obteniendo negocio para usuario: {}", email);

        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            throw new RuntimeException("El usuario no tiene un negocio asociado");
        }

        return negocio;
    }
}
