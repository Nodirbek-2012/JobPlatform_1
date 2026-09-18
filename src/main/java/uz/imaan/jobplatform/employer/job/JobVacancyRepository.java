package uz.imaan.jobplatform.employer.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobVacancyRepository extends JpaRepository<JobVacancy, Long> {

    List<JobVacancy> findByEmployerChatId(Long chatId);

    List<JobVacancy> findAllByEmployerChatId(Long chatId);

    boolean existsByExternalId(String externalId);

    Optional<JobVacancy> findByIdAndEmployerChatId(Long id, Long employerChatId);

    List<JobVacancy> findByIsActiveTrue();

    List<JobVacancy> findAllByEmployerChatIdAndIsActiveTrue(Long employerChatId);

    long countByIsActiveTrue();

    long countByVacancyStatus(String vacancyStatus);

    long countByIsActiveTrueAndVacancyStatusIsNull();

    // ===== Для /info — количество вакансий работодателя =====
    long countByEmployerChatId(Long employerChatId);

    @Query("""
            SELECT v FROM JobVacancy v
            WHERE LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(v.category) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(v.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(v.requirements) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<JobVacancy> searchByKeyword(@Param("keyword") String keyword);
}