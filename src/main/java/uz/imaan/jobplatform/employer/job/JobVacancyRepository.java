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

    // ===== YANGI: Faqat aktiv vakansiyalar (JobSeeker uchun) =====
    List<JobVacancy> findByIsActiveTrue();

    // ===== YANGI: Employer uchun aktiv vakansiyalar =====
    List<JobVacancy> findAllByEmployerChatIdAndIsActiveTrue(Long employerChatId);

    List<JobVacancy> findByNotifiedFalseAndIsActiveTrue();

    @Query("SELECT v FROM JobVacancy v WHERE (v.notified IS NULL OR v.notified = false) AND v.isActive = true")
    List<JobVacancy> findPendingNotificationVacancies();

    // ===== ⭐️ KALIT SO'Z BO'YICHA QIDIRUV (Katta-kichik harfni ajratmaydi) =====
    @Query("SELECT j FROM JobVacancy j WHERE j.isActive = true AND (" +
            "LOWER(j.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(j.requirements) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(j.category) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(j.companyName) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<JobVacancy> searchByKeyword(@Param("query") String query);

    long countByVacancyStatus(String filled);

    long countByEmployerChatId(Long employerChatId);

    long countByIsActiveTrue();
}