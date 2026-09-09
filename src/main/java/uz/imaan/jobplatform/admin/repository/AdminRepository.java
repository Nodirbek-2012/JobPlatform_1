package uz.imaan.jobplatform.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.imaan.jobplatform.admin.entity.Admin;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByTelegramId(Long telegramId);
    boolean existsByTelegramId(Long telegramId);

    // Добавьте этот метод
    Optional<Admin> findByPhoneNumber(String phoneNumber);

    // Или если хотите избежать проблем с null, добавьте этот метод
    @Query("SELECT a FROM Admin a WHERE a.telegramId = :telegramId AND a.telegramId IS NOT NULL")
    Optional<Admin> findByTelegramIdNotNull(@Param("telegramId") Long telegramId);
}

