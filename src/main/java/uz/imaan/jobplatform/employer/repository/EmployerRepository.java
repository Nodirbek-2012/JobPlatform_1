package uz.imaan.jobplatform.employer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;

import java.util.Optional;

@Repository
public interface EmployerRepository extends JpaRepository<EmployerEntity, Long> {

    // Исправленный метод
    Optional<EmployerEntity> findByTelegramId(Long telegramId);

    // Или используем @Query
    @Query("SELECT e FROM EmployerEntity e WHERE e.telegramId = :telegramId")
    Optional<EmployerEntity> findByTgId(@Param("telegramId") Long telegramId);

    // Проверка существования
    boolean existsByTelegramId(Long telegramId);

    // Поиск по номеру телефона
    Optional<EmployerEntity> findByPhoneNumber(String phoneNumber);
}