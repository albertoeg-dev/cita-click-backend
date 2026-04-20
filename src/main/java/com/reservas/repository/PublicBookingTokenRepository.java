package com.reservas.repository;

import com.reservas.entity.PublicBookingToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PublicBookingTokenRepository extends JpaRepository<PublicBookingToken, UUID> {

    @Query("SELECT t FROM PublicBookingToken t JOIN FETCH t.negocio WHERE t.token = :token AND t.activo = true")
    Optional<PublicBookingToken> findByTokenActivoWithNegocio(@Param("token") String token);

    @Query("SELECT t FROM PublicBookingToken t WHERE t.negocio.id = :negocioId AND t.activo = true ORDER BY t.createdAt DESC")
    Optional<PublicBookingToken> findTokenActivoByNegocioId(@Param("negocioId") UUID negocioId);

    boolean existsByTokenAndActivoTrue(String token);
}
