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

    @Query("SELECT j FROM JobVacancy j WHERE j.isActive = true AND (" +
            "LOWER(j.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(j.requirements) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(j.category) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(j.companyName) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<JobVacancy> searchByKeyword(@Param("query") String query);

    long countByVacancyStatus(String filled);

    long countByEmployerChatId(Long employerChatId);

    long countByIsActiveTrue();

    // ⭐ НОВЫЕ МЕТОДЫ ДЛЯ ФИЛЬТРА ПО КАТЕГОРИИ

    /**
     * Категория бўйича аниқ мос келиш (катта-кичик ҳарфни ажратмайди).
     */
    @Query("SELECT j FROM JobVacancy j WHERE j.isActive = true " +
            "AND LOWER(TRIM(j.category)) = LOWER(TRIM(:category)) " +
            "ORDER BY j.id DESC")
    List<JobVacancy> findByCategoryIgnoreCase(@Param("category") String category);

    /**
     * Категория қисм бўйича қидириш (LIKE).
     */
    @Query("SELECT j FROM JobVacancy j WHERE j.isActive = true " +
            "AND LOWER(j.category) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "ORDER BY j.id DESC")
    List<JobVacancy> searchByCategoryLike(@Param("query") String query);
    @Query("SELECT v FROM JobVacancy v WHERE v.notified = false AND v.isActive = true")
    List<JobVacancy> findPendingNotificationVacancies();
}