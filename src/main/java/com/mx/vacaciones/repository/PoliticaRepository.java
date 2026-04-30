package com.mx.vacaciones.repository;

import com.mx.vacaciones.model.Politica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PoliticaRepository extends JpaRepository<Politica, Integer> {
}