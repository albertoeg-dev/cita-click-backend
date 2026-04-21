package com.reservas.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.reservas.entity.TipoRecurrencia;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaRequest {

    @NotNull(message = "Cliente es requerido")
    private String clienteId;

    @NotNull(message = "Servicio es requerido")
    private String servicioId;

    @NotNull(message = "La fecha es requerida")
    @FutureOrPresent(message = "La fecha no puede ser pasada")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;

    @NotNull(message = "La hora es requerida")
    // Sin @JsonFormat para permitir el deserializador por defecto
    // que acepta tanto "HH:mm" como "HH:mm:ss"
    private LocalTime hora;

    private String notas;

    private BigDecimal precio;

    private String estado;

    // Campos para citas recurrentes
    private Boolean esRecurrente;

    private TipoRecurrencia tipoRecurrencia;

    private Integer intervaloRecurrencia; // Para PERSONALIZADO

    private LocalDate fechaFinRecurrencia; // Fecha límite de recurrencia

    private Integer numeroOcurrencias; // Número máximo de ocurrencias

    private String diasSemana; // Para SEMANAL: "LUN,MIE,VIE"

    @com.fasterxml.jackson.annotation.JsonIgnore
    public LocalDateTime getFechaHora() {
        if (fecha == null || hora == null) {
            return null;
        }
        return LocalDateTime.of(fecha, hora);
    }
}
