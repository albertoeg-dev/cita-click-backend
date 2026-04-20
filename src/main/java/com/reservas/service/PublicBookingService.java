package com.reservas.service;

import com.reservas.dto.request.PublicAgendarCitaRequest;
import com.reservas.dto.response.DisponibilidadResponse;
import com.reservas.dto.response.PublicNegocioResponse;
import com.reservas.entity.*;
import com.reservas.exception.BadRequestException;
import com.reservas.exception.NotFoundException;
import com.reservas.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio para la funcionalidad de reservas públicas.
 * Permite a clientes agendar citas sin autenticación, usando un token público único por negocio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicBookingService {

    private static final int INTERVALO_MINUTOS = 15;
    private static final int DIAS_DISPONIBLES = 30;

    private final PublicBookingTokenRepository publicBookingTokenRepository;
    private final NegocioRepository negocioRepository;
    private final ServicioRepository servicioRepository;
    private final HorarioTrabajoRepository horarioTrabajoRepository;
    private final DiaLibreRepository diaLibreRepository;
    private final CitaRepository citaRepository;
    private final CitaServicioRepository citaServicioRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;

    // ============================================================
    // GESTIÓN DE TOKENS
    // ============================================================

    /**
     * Obtiene el token público activo de un negocio, o crea uno nuevo si no existe.
     */
    @Transactional
    public PublicBookingToken obtenerOCrearToken(String emailOwner) {
        Usuario usuario = usuarioRepository.findByEmail(emailOwner)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            throw new NotFoundException("Negocio no encontrado para el usuario");
        }

        return publicBookingTokenRepository.findTokenActivoByNegocioId(negocio.getId())
                .orElseGet(() -> crearNuevoToken(negocio));
    }

    /**
     * Regenera (desactiva el actual y crea uno nuevo) el token público de un negocio.
     */
    @Transactional
    public PublicBookingToken regenerarToken(String emailOwner) {
        Usuario usuario = usuarioRepository.findByEmail(emailOwner)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        Negocio negocio = usuario.getNegocio();
        if (negocio == null) {
            throw new NotFoundException("Negocio no encontrado para el usuario");
        }

        // Desactivar token actual si existe
        publicBookingTokenRepository.findTokenActivoByNegocioId(negocio.getId())
                .ifPresent(tokenActivo -> {
                    tokenActivo.setActivo(false);
                    publicBookingTokenRepository.save(tokenActivo);
                    log.info("Token anterior desactivado para negocio {}", negocio.getId());
                });

        return crearNuevoToken(negocio);
    }

    private PublicBookingToken crearNuevoToken(Negocio negocio) {
        PublicBookingToken nuevoToken = PublicBookingToken.builder()
                .negocio(negocio)
                .build();
        PublicBookingToken saved = publicBookingTokenRepository.save(nuevoToken);
        log.info("Nuevo token público creado para negocio {}: {}", negocio.getId(), saved.getToken());
        return saved;
    }

    // ============================================================
    // INFORMACIÓN PÚBLICA DEL NEGOCIO
    // ============================================================

    /**
     * Valida el token y retorna la información pública del negocio con sus servicios activos.
     */
    @Transactional(readOnly = true)
    public PublicNegocioResponse obtenerInfoNegocio(String token) {
        PublicBookingToken bookingToken = validarToken(token);
        Negocio negocio = bookingToken.getNegocio();

        List<Servicio> servicios = servicioRepository.findByNegocioAndActivoTrue(negocio);

        List<PublicNegocioResponse.PublicServicioResponse> serviciosResponse = servicios.stream()
                .map(s -> PublicNegocioResponse.PublicServicioResponse.builder()
                        .id(s.getId())
                        .nombre(s.getNombre())
                        .descripcion(s.getDescripcion())
                        .duracionMinutos(s.getDuracionMinutos())
                        .precio(s.getPrecio())
                        .build())
                .collect(Collectors.toList());

        return PublicNegocioResponse.builder()
                .negocioId(negocio.getId())
                .nombre(negocio.getNombre())
                .descripcion(negocio.getDescripcion())
                .telefono(negocio.getTelefono())
                .ciudad(negocio.getCiudad())
                .logoUrl(null) // El negocio no tiene logo por ahora
                .servicios(serviciosResponse)
                .build();
    }

    // ============================================================
    // DISPONIBILIDAD
    // ============================================================

    /**
     * Retorna los horarios disponibles para una fecha y lista de servicios dados.
     */
    @Transactional(readOnly = true)
    public DisponibilidadResponse obtenerDisponibilidad(String token, List<UUID> servicioIds, LocalDate fecha) {
        PublicBookingToken bookingToken = validarToken(token);
        Negocio negocio = bookingToken.getNegocio();

        if (fecha.isBefore(LocalDate.now())) {
            throw new BadRequestException("No se pueden consultar fechas pasadas");
        }
        if (fecha.isAfter(LocalDate.now().plusDays(DIAS_DISPONIBLES))) {
            throw new BadRequestException("Solo se puede consultar disponibilidad para los próximos " + DIAS_DISPONIBLES + " días");
        }

        // Calcular duración total de los servicios seleccionados
        int duracionTotal = calcularDuracionTotal(negocio, servicioIds);

        // Verificar si es día libre
        if (!diaLibreRepository.findByNegocioAndFecha(negocio, fecha).isEmpty()) {
            return DisponibilidadResponse.builder()
                    .fecha(fecha.toString())
                    .duracionTotal(duracionTotal)
                    .horariosDisponibles(new ArrayList<>())
                    .build();
        }

        // Obtener horario del día
        int numeroDia = fecha.getDayOfWeek().getValue() - 1;
        List<HorarioTrabajo> horarios = horarioTrabajoRepository.findByNegocioAndDiaSemana(negocio, numeroDia);
        HorarioTrabajo horario = horarios.isEmpty() ? null : horarios.get(0);

        if (horario == null || !horario.isActivo()) {
            return DisponibilidadResponse.builder()
                    .fecha(fecha.toString())
                    .duracionTotal(duracionTotal)
                    .horariosDisponibles(new ArrayList<>())
                    .build();
        }

        // Obtener citas del día (excluyendo canceladas)
        LocalDateTime inicioDia = fecha.atStartOfDay();
        LocalDateTime finDia = fecha.atTime(23, 59, 59);
        List<Cita> citasDelDia = citaRepository.findByNegocio(negocio).stream()
                .filter(c -> c.getFechaHora().isAfter(inicioDia) && c.getFechaHora().isBefore(finDia))
                .filter(c -> c.getEstado() != Cita.EstadoCita.CANCELADA)
                .collect(Collectors.toList());

        List<DisponibilidadResponse.HorarioDisponible> horariosDisponibles =
                generarHorariosDisponibles(horario, citasDelDia, duracionTotal, fecha);

        return DisponibilidadResponse.builder()
                .fecha(fecha.toString())
                .duracionTotal(duracionTotal)
                .horariosDisponibles(horariosDisponibles)
                .build();
    }

    /**
     * Retorna un mapa de disponibilidad para los próximos 30 días (para el calendario).
     * Key: fecha (ISO), Value: true=tiene slots disponibles, false=sin disponibilidad.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Boolean> obtenerDisponibilidadMensual(String token, List<UUID> servicioIds) {
        PublicBookingToken bookingToken = validarToken(token);
        Negocio negocio = bookingToken.getNegocio();

        int duracionTotal = calcularDuracionTotal(negocio, servicioIds);
        java.util.Map<String, Boolean> resultado = new java.util.LinkedHashMap<>();

        LocalDate hoy = LocalDate.now();
        for (int i = 0; i < DIAS_DISPONIBLES; i++) {
            LocalDate fecha = hoy.plusDays(i);

            // Día libre?
            boolean esDiaLibre = !diaLibreRepository.findByNegocioAndFecha(negocio, fecha).isEmpty();
            if (esDiaLibre) {
                resultado.put(fecha.toString(), false);
                continue;
            }

            // Horario configurado?
            int numeroDia = fecha.getDayOfWeek().getValue() - 1;
            List<HorarioTrabajo> horarios = horarioTrabajoRepository.findByNegocioAndDiaSemana(negocio, numeroDia);
            HorarioTrabajo horario = horarios.isEmpty() ? null : horarios.get(0);
            if (horario == null || !horario.isActivo()) {
                resultado.put(fecha.toString(), false);
                continue;
            }

            // Tiene slots libres?
            LocalDateTime inicioDia = fecha.atStartOfDay();
            LocalDateTime finDia = fecha.atTime(23, 59, 59);
            List<Cita> citasDelDia = citaRepository.findByNegocio(negocio).stream()
                    .filter(c -> c.getFechaHora().isAfter(inicioDia) && c.getFechaHora().isBefore(finDia))
                    .filter(c -> c.getEstado() != Cita.EstadoCita.CANCELADA)
                    .collect(Collectors.toList());

            List<DisponibilidadResponse.HorarioDisponible> slots =
                    generarHorariosDisponibles(horario, citasDelDia, duracionTotal, fecha);
            resultado.put(fecha.toString(), !slots.isEmpty());
        }

        return resultado;
    }

    // ============================================================
    // AGENDAR CITA
    // ============================================================

    /**
     * Crea una cita pública (sin usuario autenticado).
     * Si el cliente ya existe en el negocio (mismo email), se reutiliza.
     * Si no existe, se crea un nuevo Cliente.
     */
    @Transactional
    public Cita agendarCita(String token, PublicAgendarCitaRequest request) {
        PublicBookingToken bookingToken = validarToken(token);
        Negocio negocio = bookingToken.getNegocio();

        // Validar fecha futura
        if (request.getFechaHora().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("La fecha y hora de la cita deben ser en el futuro");
        }

        // Validar servicios
        List<Servicio> servicios = new ArrayList<>();
        for (UUID servicioId : request.getServicioIds()) {
            Servicio servicio = servicioRepository.findById(servicioId)
                    .orElseThrow(() -> new NotFoundException("Servicio no encontrado: " + servicioId));
            if (!servicio.getNegocio().getId().equals(negocio.getId())) {
                throw new BadRequestException("El servicio no pertenece a este negocio");
            }
            if (!servicio.isActivo()) {
                throw new BadRequestException("El servicio '" + servicio.getNombre() + "' no está disponible");
            }
            servicios.add(servicio);
        }

        int duracionTotal = servicios.stream().mapToInt(Servicio::getDuracionMinutos).sum();
        LocalDateTime fechaFin = request.getFechaHora().plusMinutes(duracionTotal);

        // Verificar disponibilidad (sin traslapes)
        LocalDateTime inicioDia = request.getFechaHora().toLocalDate().atStartOfDay();
        LocalDateTime finDia = request.getFechaHora().toLocalDate().atTime(23, 59, 59);
        List<Cita> citasDelDia = citaRepository.findByNegocio(negocio).stream()
                .filter(c -> c.getFechaHora().isAfter(inicioDia) && c.getFechaHora().isBefore(finDia))
                .filter(c -> c.getEstado() != Cita.EstadoCita.CANCELADA)
                .collect(Collectors.toList());

        boolean hayTraslape = citasDelDia.stream().anyMatch(c ->
                request.getFechaHora().isBefore(c.getFechaFin()) && fechaFin.isAfter(c.getFechaHora()));
        if (hayTraslape) {
            throw new BadRequestException("El horario seleccionado no está disponible. Por favor elige otro horario.");
        }

        // Buscar o crear cliente
        Cliente cliente = clienteRepository.findByNegocioAndEmail(negocio, request.getClienteEmail())
                .orElseGet(() -> {
                    Cliente nuevo = Cliente.builder()
                            .negocio(negocio)
                            .nombre(request.getClienteNombre())
                            .email(request.getClienteEmail())
                            .telefono(request.getClienteTelefono())
                            .build();
                    return clienteRepository.save(nuevo);
                });

        // Servicio principal = primero de la lista
        Servicio servicioPrincipal = servicios.get(0);

        // Precio total = suma de todos los servicios
        java.math.BigDecimal precioTotal = servicios.stream()
                .map(s -> s.getPrecio() != null ? s.getPrecio() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // Crear la cita
        Cita cita = Cita.builder()
                .negocio(negocio)
                .cliente(cliente)
                .servicio(servicioPrincipal)
                .fechaHora(request.getFechaHora())
                .fechaFin(fechaFin)
                .estado(Cita.EstadoCita.CONFIRMADA)
                .notas(request.getNotas())
                .precio(precioTotal)
                .build();

        Cita citaGuardada = citaRepository.save(cita);

        // Agregar servicios adicionales (si hay más de uno)
        if (servicios.size() > 1) {
            List<CitaServicio> citaServicios = servicios.stream()
                    .map(s -> CitaServicio.builder()
                            .cita(citaGuardada)
                            .servicio(s)
                            .precio(s.getPrecio())
                            .duracionMinutos(s.getDuracionMinutos())
                            .build())
                    .collect(Collectors.toList());
            citaServicioRepository.saveAll(citaServicios);
        }

        log.info("Cita pública creada: {} para cliente {} en negocio {}",
                citaGuardada.getId(), cliente.getEmail(), negocio.getNombre());

        return citaGuardada;
    }

    // ============================================================
    // MÉTODOS PRIVADOS
    // ============================================================

    private PublicBookingToken validarToken(String token) {
        PublicBookingToken bookingToken = publicBookingTokenRepository
                .findByTokenActivoWithNegocio(token)
                .orElseThrow(() -> new NotFoundException("El enlace de reservas no es válido o ha expirado"));

        if (!bookingToken.isValido()) {
            throw new BadRequestException("El enlace de reservas ha expirado");
        }

        return bookingToken;
    }

    private int calcularDuracionTotal(Negocio negocio, List<UUID> servicioIds) {
        if (servicioIds == null || servicioIds.isEmpty()) {
            return 30; // duración por defecto si no se especifican servicios
        }
        int duracion = 0;
        for (UUID id : servicioIds) {
            Servicio servicio = servicioRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Servicio no encontrado: " + id));
            if (!servicio.getNegocio().getId().equals(negocio.getId())) {
                throw new BadRequestException("El servicio no pertenece a este negocio");
            }
            duracion += servicio.getDuracionMinutos();
        }
        return duracion;
    }

    private List<DisponibilidadResponse.HorarioDisponible> generarHorariosDisponibles(
            HorarioTrabajo horario, List<Cita> citasExistentes, int duracionMinutos, LocalDate fecha) {

        List<DisponibilidadResponse.HorarioDisponible> disponibles = new ArrayList<>();
        LocalTime horaActual = horario.getHoraApertura();
        LocalTime horaFin = horario.getHoraCierre();

        while (horaActual.plusMinutes(duracionMinutos).isBefore(horaFin) ||
                horaActual.plusMinutes(duracionMinutos).equals(horaFin)) {

            LocalTime horaFinSlot = horaActual.plusMinutes(duracionMinutos);
            LocalDateTime inicioDateTime = LocalDateTime.of(fecha, horaActual);
            LocalDateTime finDateTime = LocalDateTime.of(fecha, horaFinSlot);

            boolean estaDisponible = citasExistentes.stream().noneMatch(c ->
                    inicioDateTime.isBefore(c.getFechaFin()) && finDateTime.isAfter(c.getFechaHora()));

            if (estaDisponible) {
                boolean esRecomendado = horaActual.isAfter(LocalTime.of(9, 59)) &&
                        horaActual.isBefore(LocalTime.of(16, 1));

                disponibles.add(DisponibilidadResponse.HorarioDisponible.builder()
                        .horaInicio(horaActual)
                        .horaFin(horaFinSlot)
                        .etiqueta(String.format("%s - %s", horaActual, horaFinSlot))
                        .recomendado(esRecomendado)
                        .build());
            }

            horaActual = horaActual.plusMinutes(INTERVALO_MINUTOS);
        }

        return disponibles;
    }
}
