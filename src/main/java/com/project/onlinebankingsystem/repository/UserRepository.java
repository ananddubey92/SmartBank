package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByPan(String pan);
    boolean existsByAadhar(String aadhar);
    Optional<User> findByPan(String pan);
    Optional<User> findByAadhar(String aadhar);
    java.util.List<User> findByRole(com.project.onlinebankingsystem.enums.Role role);
    long countByRole(com.project.onlinebankingsystem.enums.Role role);
    long countByKycStatus(com.project.onlinebankingsystem.enums.KycStatus kycStatus);
}
