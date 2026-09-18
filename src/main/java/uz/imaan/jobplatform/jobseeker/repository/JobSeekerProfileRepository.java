package uz.imaan.jobplatform.jobseeker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;

import java.util.Optional;

public interface JobSeekerProfileRepository extends JpaRepository<JobSeekerProfile, Long> {

    Optional<JobSeekerProfile> findByTelegramId(Long telegramId);

    Optional<JobSeekerProfile> findByUserId(Long userId);

    Optional<JobSeekerProfile> findByPhoneNumber(String phoneNumber);
}