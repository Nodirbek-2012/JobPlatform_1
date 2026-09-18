package uz.imaan.jobplatform.employer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.imaan.jobplatform.employer.entity.EmployerReview;

import java.util.List;

@Repository
public interface EmployerReviewRepository extends JpaRepository<EmployerReview, Long> {

    List<EmployerReview> findByEmployerChatIdOrderByCreatedAtDesc(Long employerChatId);

    boolean existsByApplicationId(Long applicationId);

    @Query("SELECT AVG(r.rating) FROM EmployerReview r WHERE r.employerChatId = :employerChatId")
    Double getAverageRating(@Param("employerChatId") Long employerChatId);

    long countByEmployerChatId(Long employerChatId);
}