package com.reservas.controller;

import com.reservas.dto.request.HorarioTrabajoRequest;
import com.reservas.dto.request.NegocioRequest;
import com.reservas.dto.response.ApiResponse;
import com.reservas.dto.response.HorarioTrabajoResponse;
import com.reservas.dto.response.NegocioResponse;
import com.reservas.entity.HorarioTrabajo;
import com.reservas.mapper.HorarioTrabajoMapper;
import com.reservas.service.NegocioService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/negocios")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@Slf4j
public class NegocioController {

    private final NegocioService negocioService;
    private final HorarioTrabajoMapper horarioMapper;

    public NegocioController(NegocioService negocioService, HorarioTrabajoMapper horarioMapper) {
        this.negocioService = negocioService;
        this.horarioMapper = horarioMapper;
    }

    @GetMapping("/perfil")
    public ResponseEntity<ApiResponse> obtenerPerfil(Authentication authentication) {
        try {
            String email = authentication.getName();
            NegocioResponse negocio = negocioService.obtenerNegocioDelUsuario(email);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Perfil obtenido correctamente")
                    .data(negocio)
                    .build());
        } catch (Exception e) {
            log.error(" Error al obtener perfil: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PutMapping("/perfil")
    public ResponseEntity<ApiResponse> actualizarPerfil(
            @Valid @RequestBody NegocioRequest request,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            NegocioResponse negocio = negocioService.actualizarNegocio(email, request);
            log.info(" Negocio actualizado: {}", email);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Negocio actualizado correctamente")
                    .data(negocio)
                    .build());
        } catch (Exception e) {
            log.error(" Error al actualizar negocio: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/onboarding/completar")
    public ResponseEntity<ApiResponse> completarOnboarding(Authentication authentication) {
        try {
            String email = authentication.getName();
            negocioService.completarOnboarding(email);
            log.info("Onboarding completado para: {}", email);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Onboarding completado")
                    .build());
        } catch (Exception e) {
            log.error("Error al completar onboarding: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/horarios")
    public ResponseEntity<ApiResponse> obtenerHorarios(Authentication authentication) {
        try {
            String email = authentication.getName();
            List<HorarioTrabajo> horarios = negocioService.obtenerHorarios(email);
            List<HorarioTrabajoResponse> horariosResponse = horarioMapper.toResponseList(horarios);
            log.info(" Horarios obtenidos: {}", email);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Horarios obtenidos correctamente")
                    .data(horariosResponse)
                    .build());
        } catch (Exception e) {
            log.error(" Error al obtener horarios: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/horarios/{diaSemana}")
    public ResponseEntity<ApiResponse> guardarHorario(
            @PathVariable Integer diaSemana,
            @Valid @RequestBody HorarioTrabajoRequest request,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            HorarioTrabajo horario = negocioService.guardarHorario(email, diaSemana, request);
            HorarioTrabajoResponse horarioResponse = horarioMapper.toResponse(horario);
            log.info(" Horario guardado para día: {}", diaSemana);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.builder()
                            .success(true)
                            .message("Horario guardado correctamente")
                            .data(horarioResponse)
                            .build());
        } catch (Exception e) {
            log.error(" Error al guardar horario: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @DeleteMapping("/horarios/{horarioId}")
    public ResponseEntity<ApiResponse> eliminarHorario(
            @PathVariable UUID horarioId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            negocioService.eliminarHorario(horarioId, email);
            log.info(" Horario eliminado: {}", horarioId);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Horario eliminado correctamente")
                    .build());
        } catch (Exception e) {
            log.error(" Error al eliminar horario: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }


    @PutMapping("/horarios/{horarioId}")
    public ResponseEntity<ApiResponse> actualizarHorario(
            @PathVariable UUID horarioId,
            @Valid @RequestBody HorarioTrabajoRequest request,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            HorarioTrabajo horario = negocioService.actualizarHorario(horarioId, email, request);
            HorarioTrabajoResponse horarioResponse = horarioMapper.toResponse(horario);
            log.info(" Horario actualizado: {}", horarioId);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Horario actualizado correctamente")
                    .data(horarioResponse)
                    .build());
        } catch (Exception e) {
            log.error(" Error al actualizar horario: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }
}
