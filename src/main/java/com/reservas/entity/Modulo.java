package com.reservas.entity;

import com.reservas.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Catálogo de módulos disponibles en el marketplace de Cita Click.
 *
 * Cada módulo representa una funcionalidad opcional que un negocio puede
 * activar mediante suscripción independiente. Reemplaza el sistema de
 * booleanos en PlanLimites por un modelo flexible de marketplace.
 *
 * Claves de módulo disponibles:
 *   - email_recordatorios  → Recordatorios automáticos por email ($199/mes)
 *   - sms_whatsapp         → Recordatorios SMS/WhatsApp ($349/mes)
 *   - cobros_online        → Pagos en línea con Stripe Connect ($349/mes)
 *   - reportes_avanzados   → Reportes PDF/Excel ($249/mes)
 *   - usuarios_extra       → Usuarios adicionales ($149/usuario/mes)
 *   - multi_sucursal       → Multi-sucursal ($399/sucursal/mes)
 *   - branding_email       → Personalización de emails ($99/mes)
 *
 * @author Cita Click
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "tbl_modulos",
    indexes = {
        @Index(name = "idx_modulos_clave", columnList = "clave"),
        @Index(name = "idx_modulos_activo", columnList = "activo")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_modulos_clave", columnNames = "clave")
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Modulo extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "clave", unique = true, nullable = false, length = 100)
    private String clave;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "precio_mensual", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioMensual;

    @Column(name = "stripe_price_id", length = 100)
    private String stripePriceId;

    @Column(name = "activo", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean activo = true;
}
