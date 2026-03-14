package com.reservas.service;

import com.reservas.dto.request.HorarioTrabajoRequest;
import com.reservas.dto.request.NegocioRequest;
import com.reservas.dto.response.NegocioResponse;
import com.reservas.entity.HorarioTrabajo;
import com.reservas.entity.Negocio;
import com.reservas.entity.Usuario;
import com.reservas.exception.BadRequestException;
import com.reservas.exception.ResourceNotFoundException;
import com.reservas.exception.UnauthorizedException;
import com.reservas.repository.HorarioTrabajoRepository;
import com.reservas.repository.NegocioRepository;
import com.reservas.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class NegocioService {

    private final NegocioRepository negocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final HorarioTrabajoRepository horarioTrabajoRepository;

    public NegocioService(NegocioRepository negocioRepository,
                          UsuarioRepository usuarioRepository,
                          HorarioTrabajoRepository horarioTrabajoRepository) {
        this.negocioRepository = negocioRepository;
        this.usuarioRepository = usuarioRepository;
        this.horarioTrabajoRepository = horarioTrabajoRepository;
    }

    /**
     * Obtener negocio del usuario autenticado
     */
    @Transactional(readOnly = true)
    public NegocioResponse obtenerNegocioDelUsuario(String email) {
        // JOIN FETCH previene LazyInitializationException
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        return mapToResponse(usuario.getNegocio());
    }

    /**
     * Marca el onboarding como completado para el negocio del usuario autenticado.
     */
    @Transactional
    public void completarOnboarding(String email) {
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) throw new RuntimeException("Negocio no encontrado");

        negocio.setOnboardingCompleto(true);
        negocioRepository.save(negocio);
        log.info("[Onboarding] Completado para negocio: {}", negocio.getNombre());
    }

    /**
     * Actualizar datos del negocio
     */
    @Transactional
    public NegocioResponse actualizarNegocio(String email, NegocioRequest request) {
        // JOIN FETCH previene LazyInitializationException
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();

        if (request.getNombre() != null) {
            negocio.setNombre(request.getNombre());
        }
        if (request.getDescripcion() != null) {
            negocio.setDescripcion(request.getDescripcion());
        }
        if (request.getTelefono() != null) {
            negocio.setTelefono(request.getTelefono());
        }
        if (request.getEmail() != null) {
            negocio.setEmail(request.getEmail());
        }
        if (request.getTipo() != null) {
            negocio.setTipo(request.getTipo());
        }

        // Handle nested direccion object from frontend
        if (request.getDireccion() != null) {
            NegocioRequest.DireccionRequest direccion = request.getDireccion();

            // Save individual address fields for structured storage
            negocio.setDireccionCalle(direccion.getCalle());
            negocio.setDireccionColonia(direccion.getColonia());
            negocio.setDireccionCodigoPostal(direccion.getCodigoPostal());
            negocio.setDireccionEstado(direccion.getEstado());

            // Build domicilio string from direccion object (for legacy compatibility)
            StringBuilder domicilioBuilder = new StringBuilder();
            if (direccion.getCalle() != null && !direccion.getCalle().isEmpty()) {
                domicilioBuilder.append(direccion.getCalle());
            }
            if (direccion.getColonia() != null && !direccion.getColonia().isEmpty()) {
                if (domicilioBuilder.length() > 0) domicilioBuilder.append(", ");
                domicilioBuilder.append(direccion.getColonia());
            }
            if (direccion.getCodigoPostal() != null && !direccion.getCodigoPostal().isEmpty()) {
                if (domicilioBuilder.length() > 0) domicilioBuilder.append(", ");
                domicilioBuilder.append("CP ").append(direccion.getCodigoPostal());
            }
            if (direccion.getEstado() != null && !direccion.getEstado().isEmpty()) {
                if (domicilioBuilder.length() > 0) domicilioBuilder.append(", ");
                domicilioBuilder.append(direccion.getEstado());
            }

            if (domicilioBuilder.length() > 0) {
                negocio.setDomicilio(domicilioBuilder.toString());
            }

            // Set ciudad and pais from direccion object
            if (direccion.getCiudad() != null) {
                negocio.setCiudad(direccion.getCiudad());
            }
            if (direccion.getPais() != null) {
                negocio.setPais(direccion.getPais());
            }
        }

        // Legacy support: handle flat domicilio, ciudad, pais fields
        if (request.getDomicilio() != null) {
            negocio.setDomicilio(request.getDomicilio());
        }
        if (request.getCiudad() != null) {
            negocio.setCiudad(request.getCiudad());
        }
        if (request.getPais() != null) {
            negocio.setPais(request.getPais());
        }

        Negocio negocioActualizado = negocioRepository.save(negocio);
        log.info("Negocio actualizado: {}", negocio.getId());

        return mapToResponse(negocioActualizado);
    }

    /**
     * Obtener horarios de trabajo
     */
    @Transactional(readOnly = true)
    public List<HorarioTrabajo> obtenerHorarios(String email) {
        // JOIN FETCH previene LazyInitializationException
        Usuario usuario = usuarioRepository.findByEmailWithNegocio(email)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        return horarioTrabajoRepository.findByNegocio(usuario.getNegocio());
    }

    /**
     * Crear o actualizar horario de trabajo
     */
    public HorarioTrabajo guardarHorario(String email, Integer diaSemana, HorarioTrabajoRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();

        // Validar que la hora de cierre sea posterior a la de apertura
        if (request.getHoraCierre().isBefore(request.getHoraApertura()) ||
            request.getHoraCierre().equals(request.getHoraApertura())) {
            throw new BadRequestException(
                "La hora de cierre debe ser posterior a la hora de apertura"
            );
        }

        // Validar que el día de la semana esté en rango válido (0-6)
        if (diaSemana < 0 || diaSemana > 6) {
            throw new BadRequestException(
                "El día de la semana debe estar entre 0 (Lunes) y 6 (Domingo)"
            );
        }

        // Verificar si ya existe un horario para este día
        List<HorarioTrabajo> horariosExistentes = horarioTrabajoRepository
                .findByNegocioAndDiaSemana(negocio, diaSemana);

        // Validar que no haya solapamiento con otros horarios del mismo día
        for (HorarioTrabajo existente : horariosExistentes) {
            if (existente.isActivo() && haySolapamiento(
                    request.getHoraApertura(), request.getHoraCierre(),
                    existente.getHoraApertura(), existente.getHoraCierre())) {
                log.warn("Intento de crear horario solapado en día {}: nuevo {}-{}, existente {}-{}",
                        diaSemana, request.getHoraApertura(), request.getHoraCierre(),
                        existente.getHoraApertura(), existente.getHoraCierre());
                throw new BadRequestException(
                    String.format("El horario %s-%s se solapa con un horario existente %s-%s",
                            request.getHoraApertura(), request.getHoraCierre(),
                            existente.getHoraApertura(), existente.getHoraCierre())
                );
            }
        }

        // Eliminar horario anterior del mismo día si existe
        horarioTrabajoRepository.deleteByNegocioAndDiaSemana(negocio, diaSemana);

        // Crear nuevo horario
        HorarioTrabajo horario = HorarioTrabajo.builder()
                .negocio(negocio)
                .diaSemana(diaSemana)
                .horaApertura(request.getHoraApertura())
                .horaCierre(request.getHoraCierre())
                .activo(request.isActivo())
                .build();

        HorarioTrabajo horarioGuardado = horarioTrabajoRepository.save(horario);
        log.info(" Horario guardado para negocio: {}, día: {}, {}-{}",
                negocio.getId(), diaSemana, request.getHoraApertura(), request.getHoraCierre());

        return horarioGuardado;
    }

    /**
     * Verificar si dos rangos de tiempo se solapan
     */
    private boolean haySolapamiento(java.time.LocalTime inicio1, java.time.LocalTime fin1,
                                     java.time.LocalTime inicio2, java.time.LocalTime fin2) {
        return inicio1.isBefore(fin2) && fin1.isAfter(inicio2);
    }

    /**
     * Eliminar horario de trabajo
     */
    public void eliminarHorario(UUID horarioId, String email) {
        HorarioTrabajo horario = horarioTrabajoRepository.findById(horarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Horario no encontrado"));

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        if (!horario.getNegocio().getId().equals(usuario.getNegocio().getId())) {
            throw new UnauthorizedException("No tienes permiso para eliminar este horario");
        }

        horarioTrabajoRepository.deleteById(horarioId);
        log.info("Horario eliminado: {}", horarioId);
    }

    /**
     * Actualizar horario de trabajo existente
     */
    public HorarioTrabajo actualizarHorario(UUID horarioId, String email, HorarioTrabajoRequest request) {
        // Verificar que el horario existe
        HorarioTrabajo horario = horarioTrabajoRepository.findById(horarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Horario no encontrado"));

        // Verificar permisos
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        if (!horario.getNegocio().getId().equals(usuario.getNegocio().getId())) {
            throw new UnauthorizedException("No tienes permiso para actualizar este horario");
        }

        // Validar que la hora de cierre sea posterior a la de apertura
        if (request.getHoraCierre().isBefore(request.getHoraApertura()) ||
            request.getHoraCierre().equals(request.getHoraApertura())) {
            throw new BadRequestException(
                "La hora de cierre debe ser posterior a la hora de apertura"
            );
        }

        // Validar solapamiento con otros horarios del mismo día (excepto el actual)
        if (request.getDiaSemana() != null) {
            List<HorarioTrabajo> horariosExistentes = horarioTrabajoRepository
                    .findByNegocioAndDiaSemana(usuario.getNegocio(), request.getDiaSemana());

            for (HorarioTrabajo existente : horariosExistentes) {
                if (!existente.getId().equals(horarioId) && existente.isActivo() &&
                    haySolapamiento(request.getHoraApertura(), request.getHoraCierre(),
                                  existente.getHoraApertura(), existente.getHoraCierre())) {
                    throw new BadRequestException(
                        String.format("El horario %s-%s se solapa con un horario existente %s-%s",
                                request.getHoraApertura(), request.getHoraCierre(),
                                existente.getHoraApertura(), existente.getHoraCierre())
                    );
                }
            }
        }

        // Actualizar campos
        if (request.getDiaSemana() != null) {
            if (request.getDiaSemana() < 0 || request.getDiaSemana() > 6) {
                throw new BadRequestException(
                    "El día de la semana debe estar entre 0 (Lunes) y 6 (Domingo)"
                );
            }
            horario.setDiaSemana(request.getDiaSemana());
        }
        horario.setHoraApertura(request.getHoraApertura());
        horario.setHoraCierre(request.getHoraCierre());
        horario.setActivo(request.isActivo());

        HorarioTrabajo horarioActualizado = horarioTrabajoRepository.save(horario);
        log.info(" Horario actualizado: {}, día: {}, {}-{}",
                horarioId, horario.getDiaSemana(),
                request.getHoraApertura(), request.getHoraCierre());

        return horarioActualizado;
    }

    /**
     * Mapear Negocio a NegocioResponse
     */
    private NegocioResponse mapToResponse(Negocio negocio) {
        // Build nested direccion object from individual fields
        NegocioResponse.DireccionResponse direccion = null;
        if (negocio.getDireccionCalle() != null || negocio.getDireccionColonia() != null ||
            negocio.getCiudad() != null || negocio.getDireccionCodigoPostal() != null ||
            negocio.getDireccionEstado() != null || negocio.getPais() != null) {

            direccion = NegocioResponse.DireccionResponse.builder()
                    .calle(negocio.getDireccionCalle())
                    .colonia(negocio.getDireccionColonia())
                    .ciudad(negocio.getCiudad())
                    .codigoPostal(negocio.getDireccionCodigoPostal())
                    .estado(negocio.getDireccionEstado())
                    .pais(negocio.getPais())
                    .build();
        }

        return NegocioResponse.builder()
                .id(negocio.getId().toString())
                .nombre(negocio.getNombre())
                .descripcion(negocio.getDescripcion())
                .email(negocio.getEmail())
                .telefono(negocio.getTelefono())
                .tipo(negocio.getTipo())
                // Legacy flat fields
                .domicilio(negocio.getDomicilio())
                .ciudad(negocio.getCiudad())
                .pais(negocio.getPais())
                // Nested direccion object
                .direccion(direccion)
                .plan(negocio.getPlan())
                .estadoPago(negocio.getEstadoPago())
                .onboardingCompleto(negocio.isOnboardingCompleto())
                .build();
    }
}