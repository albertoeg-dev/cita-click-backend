package com.reservas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request para agendar una cita desde la página pública (sin autenticación).
 */
@Data
public class PublicAgendarCitaRequest {

    @NotEmpty(message = "Debes seleccionar al menos un servicio")
    private List<UUID> servicioIds;

    @NotNull(message = "La fecha y hora son requeridas")
    private LocalDateTime fechaHora;

    @NotBlank(message = "El nombre es requerido")
    private String clienteNombre;

    @NotBlank(message = "El email es requerido")
    @Email(message = "El email no es válido")
    private String clienteEmail;

    private String clienteTelefono;

    private String notas;
}
