package com.reservas.entity;

import com.reservas.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Módulos activos por negocio.
 *
 * Representa la suscripción de un negocio a un módulo específico del marketplace.
 * Cada registro indica que un negocio tiene acceso activo a una funcionalidad.
 * La restricción única (negocio_id, modulo_id) garantiza que un negocio no
 * pueda tener el mismo módulo dos veces.
 *
 * @author Cita Click
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "tbl_modulo_negocio",
    indexes = {
        @Index(name = "idx_modulo_negocio_negocio_id", columnList = "negocio_id"),
        @Index(name = "idx_modulo_negocio_modulo_id", columnList = "modulo_id"),
        @Index(name = "idx_modulo_negocio_activo", columnList = "activo")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_modulo_negocio", columnNames = {"negocio_id", "modulo_id"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuloNegocio extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false)
    private Negocio negocio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modulo_id", nullable = false)
    private Modulo modulo;

    /**
     * ID de la suscripción Stripe que factura este módulo.
     * Se establece cuando el pago es procesado exitosamente.
     */
    @Column(name = "stripe_subscription_id", length = 100)
    private String stripeSubscriptionId;

    @Column(name = "fecha_activacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaActivacion = LocalDateTime.now();

    @Column(name = "fecha_cancelacion")
    private LocalDateTime fechaCancelacion;

    @Column(name = "activo", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean activo = true;
}
