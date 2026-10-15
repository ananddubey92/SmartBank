package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.model.LoginActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginActivityRepository extends JpaRepository<LoginActivity, Long> {
    List<LoginActivity> findTop10ByUsernameOrderByTimestampDesc(String username);
    List<LoginActivity> findAllByUsernameOrderByTimestampDesc(String username);
    long countByUsernameAndStatus(String username, String status);
}
