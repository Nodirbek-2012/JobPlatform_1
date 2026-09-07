package uz.imaan.jobplatform.employer.repository;

import org.springframework.data.repository.query.Param; // <-- To'g'ri import
import org.springframework.data.jpa.repository.Query;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployerRepository extends JpaRepository<EmployerEntity, Long> {

    @Query("SELECT e FROM EmployerEntity e WHERE e.employerChatId = :tgId OR e.id = :tgId")
    static List<EmployerEntity> findByTgId(@Param("tgId") Long tgId) {
        return null;
    }

    List<EmployerEntity> findByStatus(String status);

    Optional<EmployerEntity> findByEmployerChatId(Long employerChatId);

    boolean existsByInn(String inn);
}