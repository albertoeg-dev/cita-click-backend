package com.reservas.repository;

import com.reservas.entity.ModuloNegocio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModuloNegocioRepository extends JpaRepository<ModuloNegocio, UUID> {

    List<ModuloNegocio> findByNegocioIdAndActivoTrue(UUID negocioId);

    boolean existsByNegocioIdAndModuloClaveAndActivoTrue(UUID negocioId, String clave);

    Optional<ModuloNegocio> findByNegocioIdAndModuloClaveAndActivoTrue(UUID negocioId, String clave);

    @Query("SELECT mn FROM ModuloNegocio mn JOIN FETCH mn.modulo WHERE mn.negocio.id = :negocioId AND mn.activo = true")
    List<ModuloNegocio> findActivosConModulo(@Param("negocioId") UUID negocioId);

    /**
     * Historial completo (activos + cancelados) ordenado por fecha de activación descendente.
     * Usado en la vista de historial de compras del negocio.
     */
    @Query("SELECT mn FROM ModuloNegocio mn JOIN FETCH mn.modulo WHERE mn.negocio.id = :negocioId ORDER BY mn.fechaActivacion DESC")
    List<ModuloNegocio> findByNegocioIdOrderByFechaActivacionDesc(@Param("negocioId") UUID negocioId);
}
