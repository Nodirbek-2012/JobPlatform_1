package uz.imaan.jobplatform.jobseeker.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.imaan.jobplatform.jobseeker.entity.FavoriteVacancy;

import java.util.Optional;

@Repository
public interface FavoriteVacancyRepository extends JpaRepository<FavoriteVacancy, Long> {

    boolean existsByProfile_UserIdAndVacancy_Id(Long userId, Long vacancyId);

    Optional<FavoriteVacancy> findByProfile_UserIdAndVacancy_Id(Long userId, Long vacancyId);

    Page<FavoriteVacancy> findAllByProfile_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}