package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.enums.CardStatus;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.DebitCard;
import com.project.onlinebankingsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DebitCardRepository extends JpaRepository<DebitCard, Long> {

    Optional<DebitCard> findByAccount(Account account);

    Optional<DebitCard> findByAccount_AccountNumber(String accountNumber);

    Optional<DebitCard> findByCardNumber(String cardNumber);

    boolean existsByAccount(Account account);

    boolean existsByAccount_AccountNumber(String accountNumber);

    boolean existsByCardNumber(String cardNumber);

    List<DebitCard> findByUser(User user);

    List<DebitCard> findByUser_Username(String username);

    Optional<DebitCard> findFirstByUser_UsernameOrderByIdDesc(String username);

    long countByStatus(CardStatus status);

    List<DebitCard> findAllByOrderByCreatedAtDesc();
}
