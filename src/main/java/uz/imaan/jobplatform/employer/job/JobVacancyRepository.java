package uz.imaan.jobplatform.employer.job;

import org.springframework.data.jpa.repository.JpaRepository;
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
}