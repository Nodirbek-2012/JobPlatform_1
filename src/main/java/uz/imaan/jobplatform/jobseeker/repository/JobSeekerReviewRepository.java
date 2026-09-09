package uz.imaan.jobplatform.jobseeker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerReview;

import java.util.List;

public interface JobSeekerReviewRepository extends JpaRepository<JobSeekerReview, Long> {

    List<JobSeekerReview> findByJobSeekerId(Long jobSeekerId);

    boolean existsByApplicationId(Long applicationId);

}
