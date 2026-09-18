package uz.imaan.jobplatform.jobseeker.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByJobSeekerId(Long jobSeekerId);

    Page<JobApplication> findByJobSeekerId(Long jobSeekerId, Pageable pageable);

    boolean existsByJobIdAndJobSeekerId(Long jobId, Long jobSeekerId);

    List<JobApplication> findByJobSeekerIdAndStatus(Long jobSeekerId, JobApplication.ApplicationStatus status);

    Optional<JobApplication> findByIdAndJobSeekerId(Long id, Long jobSeekerId);

    // ===== YANGI: Employer uchun - barcha arizalarni olish =====
    @Query("SELECT a FROM JobApplication a WHERE a.jobId IN " +
            "(SELECT v.id FROM JobVacancy v WHERE v.employerChatId = :employerChatId) " +
            "ORDER BY a.createdAt DESC")
    List<JobApplication> findAllByEmployerChatId(@Param("employerChatId") Long employerChatId);

    // ===== YANGI: Employer uchun - status bo'yicha =====
    @Query("SELECT a FROM JobApplication a WHERE a.jobId IN " +
            "(SELECT v.id FROM JobVacancy v WHERE v.employerChatId = :employerChatId) " +
            "AND a.status = :status ORDER BY a.createdAt DESC")
    List<JobApplication> findAllByEmployerChatIdAndStatus(
            @Param("employerChatId") Long employerChatId,
            @Param("status") JobApplication.ApplicationStatus status
    );
    long countByStatus(JobApplication.ApplicationStatus status);
}