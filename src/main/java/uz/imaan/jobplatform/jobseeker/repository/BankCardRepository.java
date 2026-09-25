package uz.imaan.jobplatform.jobseeker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.imaan.jobplatform.jobseeker.entity.BankCard;

import java.util.List;
import java.util.Optional;

public interface BankCardRepository extends JpaRepository<BankCard, Long> {

    List<BankCard> findByJobSeekerId(Long jobSeekerId);

    Optional<BankCard> findFirstByJobSeekerId(Long jobSeekerId);

    long countByIsActiveTrue();

    boolean existsByCardNumber(String cardNumber);
}