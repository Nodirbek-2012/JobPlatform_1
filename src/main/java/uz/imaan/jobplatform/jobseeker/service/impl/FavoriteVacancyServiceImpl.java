package uz.imaan.jobplatform.jobseeker.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;
import uz.imaan.jobplatform.jobseeker.dto.FavoriteVacancyDto;
import uz.imaan.jobplatform.jobseeker.entity.FavoriteVacancy;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.repository.FavoriteVacancyRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.jobseeker.service.interfaces.FavoriteVacancyService;


import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteVacancyServiceImpl implements FavoriteVacancyService {

    private final FavoriteVacancyRepository favoriteVacancyRepository;
    private final JobSeekerProfileRepository profileRepository;
    private final JobVacancyRepository vacancyRepository;

    @Override
    @Transactional
    public boolean toggleFavorite(Long userId, Long vacancyId) {
        Optional<FavoriteVacancy> existingFavorite = favoriteVacancyRepository
                .findByProfile_UserIdAndVacancy_Id(userId, vacancyId);

        if (existingFavorite.isPresent()) {
            favoriteVacancyRepository.delete(existingFavorite.get());
            log.info("Vakansiya saralanganlardan olib tashlandi: userId={}, vacancyId={}", userId, vacancyId);
            return false;
        }

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profil topilmadi: " + userId));

        JobVacancy vacancy = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new IllegalArgumentException("Vakansiya topilmadi: " + vacancyId));

        favoriteVacancyRepository.save(new FavoriteVacancy(profile, vacancy));
        log.info("Vakansiya saralanganlarga saqlandi: userId={}, vacancyId={}", userId, vacancyId);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long vacancyId) {
        return favoriteVacancyRepository.existsByProfile_UserIdAndVacancy_Id(userId, vacancyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FavoriteVacancyDto> getFavorites(Long userId, Pageable pageable) {
        return favoriteVacancyRepository.findAllByProfile_UserIdOrderByCreatedAtDesc(userId, pageable)
                .map(fav -> {
                    JobVacancy v = fav.getVacancy();
                    return new FavoriteVacancyDto(
                            fav.getId(),
                            v.getId(),
                            v.getTitle(),
                            v.getCategory(),
                            v.getType() != null ? v.getType() : "Ko'rsatilmagan",
                            v.getSalary() != null ? v.getSalary() : "Kelishuv asosida",
                            v.getWorkHours() != null ? v.getWorkHours() : "Ko'rsatilmagan",
                            fav.getCreatedAt()
                    );
                });
    }
}