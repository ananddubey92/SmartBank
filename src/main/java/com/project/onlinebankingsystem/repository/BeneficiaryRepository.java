package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.model.Beneficiary;
import com.project.onlinebankingsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByUser(User user);
    List<Beneficiary> findByUserUsername(String username);
}
