package uz.imaan.jobplatform.employer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployerRepository extends JpaRepository<EmployerEntity, Long> {

    @Query("SELECT e FROM EmployerEntity e WHERE e.employerChatId = :tgId OR e.id = :tgId")
    List<EmployerEntity> findByTgId(@Param("tgId") Long tgId);

    List<EmployerEntity> findByStatus(String status);

    Optional<EmployerEntity> findByEmployerChatId(Long employerChatId);

    Optional<EmployerEntity> findByPhoneNumber(String phoneNumber);

    Optional<EmployerEntity> findByTelegramId(Long telegramId);
}