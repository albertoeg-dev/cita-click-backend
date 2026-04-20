package com.reservas.entity;

import com.reservas.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Token público único por negocio que permite a los clientes
 * agendar citas sin necesidad de autenticación.
 *
 * URL resultante: /book/{token}
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "tbl_public_booking_tokens",
    indexes = {
        @Index(name = "idx_pbt_token", columnList = "token"),
        @Index(name = "idx_pbt_negocio_id", columnList = "negocio_id"),
        @Index(name = "idx_pbt_activo", columnList = "activo")
    }
)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicBookingToken extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false)
    private Negocio negocio;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    private void prePersist() {
        if (token == null) {
            token = UUID.randomUUID().toString().replace("-", "");
        }
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusYears(1);
        }
    }

    public boolean isValido() {
        return activo && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }
}
