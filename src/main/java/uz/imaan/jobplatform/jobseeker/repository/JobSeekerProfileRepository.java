package uz.imaan.jobplatform.jobseeker.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;

import java.util.List;
import java.util.Optional;

public interface JobSeekerProfileRepository extends JpaRepository<JobSeekerProfile, Long> {

    Optional<JobSeekerProfile> findByUserId(Long userId);

    Optional<JobSeekerProfile> findByTelegramId(Long telegramId);

    Optional<JobSeekerProfile> findByPhoneNumber(String phoneNumber);

    List<JobSeekerProfile> findByFullNameContainingIgnoreCase(String fullName);

    List<JobSeekerProfile> findAllByCategoryIgnoreCase(String category);

    boolean existsByUserId(Long userId);
    boolean existsByPhoneNumber(String phoneNumber);

    Page<JobSeekerProfile> findAll(Pageable pageable);

    @Query("SELECT j FROM JobSeekerProfile j WHERE " +
            "LOWER(j.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "j.phoneNumber LIKE CONCAT('%', :keyword, '%')")
    List<JobSeekerProfile> searchForAdmin(@Param("keyword") String keyword);
}