package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.model.UpiLimitConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UpiLimitConfigurationRepository extends JpaRepository<UpiLimitConfiguration, Long> {
    Optional<UpiLimitConfiguration> findTopByOrderByIdAsc();
}
