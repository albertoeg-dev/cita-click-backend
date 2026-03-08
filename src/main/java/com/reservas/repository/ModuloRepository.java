package com.reservas.repository;

import com.reservas.entity.Modulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModuloRepository extends JpaRepository<Modulo, UUID> {

    Optional<Modulo> findByClave(String clave);

    List<Modulo> findByActivoTrueOrderByPrecioMensualAsc();

    boolean existsByClave(String clave);
}
