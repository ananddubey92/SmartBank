package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.model.UpiProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UpiProfileRepository extends JpaRepository<UpiProfile, Long> {
    Optional<UpiProfile> findByUpiId(String upiId);
    Optional<UpiProfile> findByUser(User user);
    List<UpiProfile> findAllByUser(User user);
    Optional<UpiProfile> findByUserAndIsPrimaryTrue(User user);
    boolean existsByUpiId(String upiId);
    Optional<UpiProfile> findByUserPhone(String phone);
    List<UpiProfile> findByIsLockedTrue();
    long countByIsActiveTrue();
}
