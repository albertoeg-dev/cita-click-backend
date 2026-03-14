package com.reservas.entity;

import com.reservas.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad que representa un negocio registrado en el sistema.
 *
 * Cada negocio tiene su propio plan de suscripción, usuarios, clientes y servicios.
 * Maneja el ciclo de vida de suscripción (trial, activo, vencido, suspendido).
 *
 * @author Cita Click
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "tbl_negocios",
    indexes = {
        @Index(name = "idx_negocio_email", columnList = "email"),
        @Index(name = "idx_negocio_stripe_customer_id", columnList = "stripe_customer_id"),
        @Index(name = "idx_negocio_estado_pago", columnList = "estado_pago"),
        @Index(name = "idx_negocio_plan", columnList = "plan"),
        @Index(name = "idx_negocio_cuenta_activa", columnList = "cuenta_activa")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_negocio_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_negocio_stripe_customer_id", columnNames = "stripe_customer_id")
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Negocio extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "tipo", length = 50)
    private String tipo; // 'salon', 'clinica', 'masajes', 'consultorio', etc.

    // Legacy flat address fields (kept for backward compatibility)
    @Column(name = "domicilio", length = 300)
    private String domicilio;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "pais", length = 100)
    private String pais;

    // Individual address fields for structured storage
    @Column(name = "direccion_calle", length = 200)
    private String direccionCalle;

    @Column(name = "direccion_colonia", length = 100)
    private String direccionColonia;

    @Column(name = "direccion_codigo_postal", length = 10)
    private String direccionCodigoPostal;

    @Column(name = "direccion_estado", length = 100)
    private String direccionEstado;

    @Column(name = "estado_pago", nullable = false, length = 50, columnDefinition = "varchar(50) default 'trial'")
    @Builder.Default
    private String estadoPago = "trial"; // 'trial', 'activo', 'vencido', 'suspendido'

    @Column(name = "plan", nullable = false, length = 50, columnDefinition = "varchar(50) default 'basico'")
    @Builder.Default
    private String plan = "basico"; // 'basico', 'profesional', 'premium'

    @Column(name = "fecha_inicio_plan")
    private LocalDateTime fechaInicioPlan;

    @Column(name = "fecha_proximo_cobro")
    private LocalDateTime fechaProximoCobro;

    // Stripe Customer ID
    @Column(name = "stripe_customer_id", unique = true, length = 100)
    private String stripeCustomerId;

    // Stripe Subscription ID
    @Column(name = "stripe_subscription_id", length = 100)
    private String stripeSubscriptionId;

    // Campos para control de suscripción
    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "fecha_fin_prueba")
    private LocalDateTime fechaFinPrueba;

    @Column(name = "en_periodo_prueba", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean enPeriodoPrueba = true;

    @Column(name = "cuenta_activa", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean cuentaActiva = true;

    @Column(name = "notificacion_prueba_enviada", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean notificacionPruebaEnviada = false;

    @Column(name = "notificacion_vencimiento_enviada", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean notificacionVencimientoEnviada = false;

    @Column(name = "onboarding_completo", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean onboardingCompleto = false;

    @PrePersist
    protected void onPrePersist() {
        fechaRegistro = LocalDateTime.now();
        fechaInicioPlan = LocalDateTime.now();

        // Solo planes Básico y Profesional tienen prueba
        if ("basico".equalsIgnoreCase(plan) || "profesional".equalsIgnoreCase(plan)) {
            fechaFinPrueba = LocalDateTime.now().plusDays(7);
            enPeriodoPrueba = true;
            estadoPago = "trial";
        } else {
            // Premium no tiene prueba, requiere pago inmediato
            enPeriodoPrueba = false;
            estadoPago = "pendiente_pago";
            cuentaActiva = false;
        }
    }

    // Métodos de utilidad
    public boolean haVencidoPrueba() {
        if (!enPeriodoPrueba || fechaFinPrueba == null) return false;
        return LocalDateTime.now().isAfter(fechaFinPrueba);
    }

    public boolean puedeUsarSistema() {
        // Si está en periodo de prueba y no ha vencido
        if (enPeriodoPrueba && fechaFinPrueba != null) {
            return !haVencidoPrueba() && cuentaActiva;
        }
        // Si tiene suscripción activa
        return "activo".equals(estadoPago) && cuentaActiva;
    }

    public long diasRestantesPrueba() {
        if (!enPeriodoPrueba || fechaFinPrueba == null) return 0;

        LocalDateTime ahora = LocalDateTime.now();
        if (ahora.isAfter(fechaFinPrueba)) return 0;

        return java.time.Duration.between(ahora, fechaFinPrueba).toDays();
    }

    public long diasRestantesVencimiento() {
        if (fechaProximoCobro == null) return 0;

        LocalDateTime ahora = LocalDateTime.now();
        if (ahora.isAfter(fechaProximoCobro)) return 0;

        return java.time.Duration.between(ahora, fechaProximoCobro).toDays();
    }

    public boolean necesitaNotificacionPrueba() {
        // Notificar 1 día antes de que termine la prueba
        return enPeriodoPrueba && !notificacionPruebaEnviada &&
               diasRestantesPrueba() <= 1 && diasRestantesPrueba() > 0;
    }

    public boolean necesitaNotificacionVencimiento() {
        // Notificar 5 días antes del vencimiento
        return !enPeriodoPrueba && "activo".equals(estadoPago) &&
               !notificacionVencimientoEnviada &&
               diasRestantesVencimiento() <= 5 && diasRestantesVencimiento() > 0;
    }
}
