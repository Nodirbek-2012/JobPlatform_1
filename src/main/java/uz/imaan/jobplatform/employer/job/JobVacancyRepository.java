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

    // ===== Faqat aktiv vakansiyalar (JobSeeker uchun) =====
    List<JobVacancy> findByIsActiveTrue();

    // ===== Employer uchun aktiv vakansiyalar =====
    List<JobVacancy> findAllByEmployerChatIdAndIsActiveTrue(Long employerChatId);

    // ===== KALIT SO'Z BO'YICHA QIDIRUV =====
    @Query("SELECT j FROM JobVacancy j WHERE j.isActive = true AND (" +
            "LOWER(j.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(j.requirements) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(j.category) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(j.companyName) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<JobVacancy> searchByKeyword(@Param("query") String query);

    // ===== ⭐️ YANGI: Bildirishnomasi yuborilmagan faol vakansiyalarni olish =====
    @Query("SELECT j FROM JobVacancy j WHERE (j.isActive = true OR j.isActive IS NULL) " +
            "AND (j.notified = false OR j.notified IS NULL)")
    List<JobVacancy> findUnnotifiedVacancies();

    List<JobVacancy> findByNotifiedFalseOrNotifiedIsNull();
}