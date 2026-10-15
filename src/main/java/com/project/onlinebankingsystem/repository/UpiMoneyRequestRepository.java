package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.enums.UpiRequestStatus;
import com.project.onlinebankingsystem.model.UpiMoneyRequest;
import com.project.onlinebankingsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UpiMoneyRequestRepository extends JpaRepository<UpiMoneyRequest, Long> {
    Optional<UpiMoneyRequest> findByRequestReference(String requestReference);
    List<UpiMoneyRequest> findByTargetUserOrderByCreatedAtDesc(User targetUser);
    List<UpiMoneyRequest> findByRequesterUserOrderByCreatedAtDesc(User requesterUser);
    List<UpiMoneyRequest> findByTargetUserAndStatusOrderByCreatedAtDesc(User targetUser, UpiRequestStatus status);
    List<UpiMoneyRequest> findByRequesterUserAndStatusOrderByCreatedAtDesc(User requesterUser, UpiRequestStatus status);
    long countByTargetUserAndStatus(User targetUser, UpiRequestStatus status);
}
