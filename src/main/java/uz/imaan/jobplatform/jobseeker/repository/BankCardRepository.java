package uz.imaan.jobplatform.jobseeker.repository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.imaan.jobplatform.jobseeker.entity.BankCard;

import java.util.List;
import java.util.Optional;

public interface BankCardRepository extends JpaRepository<BankCard, Long> {

    List<BankCard> findByJobSeekerId(Long jobSeekerId);

    Optional<BankCard> findFirstByJobSeekerId(Long jobSeekerId);

    long countByIsActiveTrue();

    boolean existsByCardNumber(@NotBlank(message = "Karta raqami kiritilishi shart") @Pattern(regexp = "^[0-9]{16}$", message = "Karta raqami 16 ta raqamdan iborat bo'lishi kerak") String s);
}