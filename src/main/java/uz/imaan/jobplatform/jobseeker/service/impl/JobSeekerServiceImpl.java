package uz.imaan.jobplatform.jobseeker.service.impl;


import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.imaan.jobplatform.feign.TestFeignClient;
import uz.imaan.jobplatform.jobseeker.dto.*;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.entity.Resume;
import uz.imaan.jobplatform.jobseeker.mapper.JobSeekerMapper;
import uz.imaan.jobplatform.jobseeker.repository.JobApplicationRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.jobseeker.repository.ResumeRepository;
import uz.imaan.jobplatform.jobseeker.service.interfaces.JobSeekerService;

import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class JobSeekerServiceImpl implements JobSeekerService {


    private final JobSeekerProfileRepository profileRepository;
    private final JobApplicationRepository applicationRepository;
    private final ResumeRepository resumeRepository;
    private final JobSeekerMapper mapper;
    private final TestFeignClient testFeignClient;

    // ============================================
    // PROFIL SERVICES
    // ============================================


    @PostConstruct
    void test() {
        System.err.println(testFeignClient.getData());
    }



    @Override
    @Transactional(readOnly = true)
    @PostAuthorize("returnObject.userId == authentication.principal.userId or hasRole('ADMIN')")
    public JobSeekerProfileDto getProfileByUserId(Long userId) {
        log.info("📋 Profil olish: userId={}", userId);
        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));
        return mapper.toDto(profile);
    }

    @Override
    @Transactional
    public JobSeekerProfileDto updateProfile(Long userId, @Valid UpdateProfileRequest dto) {
        log.info("✏️ Profil yangilash: userId={}", userId);
        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        mapper.updateEntityFromDto(dto, profile);
        JobSeekerProfile updatedProfile = profileRepository.save(profile);
        return mapper.toDto(updatedProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public JobSeekerProfile getProfileEntity(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));
    }

    // ============================================
    // JOB APPLICATION SERVICES
    // ============================================

    @Override
    @Transactional
    public JobApplication applyForJob(Long userId, ApplyJobRequest request) {
        log.info("📝 Ariza topshirish: userId={}, jobId={}", userId, request.jobId());

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        if (applicationRepository.existsByJobIdAndJobSeekerId(request.jobId(), profile.getId())) {
            throw new IllegalStateException("Siz ushbu ishga allaqachon ariza yuborgansiz!");
        }

        // Aktiv rezyume mavjudligini tekshirish
        List<Resume> activeResumes = resumeRepository.findByJobSeekerIdAndIsActiveTrue(profile.getId());
        if (activeResumes.isEmpty()) {
            throw new IllegalStateException("Ariza yuborish uchun avval rezyume yarating!");
        }

        JobApplication application = JobApplication.builder()
                .jobId(request.jobId())
                .jobSeekerId(profile.getId())
                .coverLetter(request.comment())
                .status(JobApplication.ApplicationStatus.PENDING)
                .build();

        return applicationRepository.save(application);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobApplication> getMyApplicationsWithPagination(Long userId, Pageable pageable) {
        log.info("📋 Arizalar ro'yxati (pagination): userId={}", userId);
        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));
        return applicationRepository.findByJobSeekerId(profile.getId(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationDto> getMyApplications(Long userId) {
        log.info("Arizalar ro'yxati (list): userId={}", userId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        return applicationRepository.findByJobSeekerId(profile.getId())
                .stream()
                .map(application -> new JobApplicationDto(
                        application.getId(),                      // DTO id (ariza ID)
                        application.getJobId(),             // jobId (ish e'lonining ID si)
                        profile.getId(),                          // jobSeekerId (yoki application.getJobSeeker().getId())
                        application.getStatus().name(),           // status
                        application.getCreatedAt(),               // appliedAt
                        application.getCoverLetter()              // coverLetter
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplication> getMyActiveJobs(Long userId) {
        log.info("📋 Faol ishlar: userId={}", userId);
        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        return applicationRepository.findByJobSeekerIdAndStatus(
                profile.getId(),
                JobApplication.ApplicationStatus.ACCEPTED
        );
    }

    @Override
    @Transactional
    public void cancelApplication(Long userId, Long applicationId) {
        log.info("🚫 Arizani bekor qilish: userId={}, applicationId={}", userId, applicationId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Ariza topilmadi: " + applicationId));

        if (!application.getJobSeekerId().equals(profile.getId())) {
            throw new SecurityException("Bu arizani bekor qilish huquqi yo'q!");
        }

        if (application.getStatus() == JobApplication.ApplicationStatus.ACCEPTED) {
            throw new IllegalStateException("Qabul qilingan arizani bekor qilib bo'lmaydi!");
        }

        if (application.getStatus() == JobApplication.ApplicationStatus.CANCELLED) {
            throw new IllegalStateException("Bu ariza allaqachon bekor qilingan!");
        }

        application.setStatus(JobApplication.ApplicationStatus.CANCELLED);
        applicationRepository.save(application);

        log.info("✅ Ariza bekor qilindi: applicationId={}", applicationId);
    }

    // ============================================
    // RESUME SERVICES
    // ============================================

    @Override
    @Transactional
    public ResumeDto createResume(Long userId, @Valid CreateResumeRequest request) {
        log.info("📄 Rezyume yaratish: userId={}", userId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        // Eski aktiv rezyumeni o'chirish
        List<Resume> activeResumes = resumeRepository.findByJobSeekerIdAndIsActiveTrue(profile.getId());
        for (Resume oldResume : activeResumes) {
            oldResume.setIsActive(false);
            resumeRepository.save(oldResume);
        }


        // Yangi rezyume yaratish
        Resume resume = Resume.builder()
                .jobSeekerId(profile.getId())
                .title(request.title())
                .fullName(request.fullName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .profession(request.profession())
                .experience(request.experience())
                .education(request.education())
                .skills(request.skills())
                .about(request.about())
                .isActive(true)
                .build();

        Resume savedResume = resumeRepository.save(resume);
        log.info("✅ Rezyume yaratildi: resumeId={}", savedResume.getId());
        return toResumeDto(savedResume);
    }

    @Override
    @Transactional
    public ResumeDto updateResume(Long userId, Long resumeId, @Valid UpdateResumeRequest request) {
        log.info("✏️ Rezyume yangilash: userId={}, resumeId={}", userId, resumeId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        Resume resume = resumeRepository.findByIdAndJobSeekerId(resumeId, profile.getId())
                .orElseThrow(() -> new RuntimeException("Rezyume topilmadi!"));

        if (request.title() != null) resume.setTitle(request.title());
        if (request.fullName() != null) resume.setFullName(request.fullName());
        if (request.email() != null) resume.setEmail(request.email());
        if (request.phoneNumber() != null) resume.setPhoneNumber(request.phoneNumber());
        if (request.profession() != null) resume.setProfession(request.profession());
        if (request.experience() != null) resume.setExperience(request.experience());
        if (request.education() != null) resume.setEducation(request.education());
        if (request.skills() != null) resume.setSkills(request.skills());
        if (request.about() != null) resume.setAbout(request.about());

        Resume updatedResume = resumeRepository.save(resume);
        log.info("✅ Rezyume yangilandi: resumeId={}", updatedResume.getId());
        return toResumeDto(updatedResume);
    }

    @Override
    @Transactional
    public void deleteResume(Long userId, Long resumeId) {
        log.info("🗑️ Rezyume o'chirish: userId={}, resumeId={}", userId, resumeId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        Resume resume = resumeRepository.findByIdAndJobSeekerId(resumeId, profile.getId())
                .orElseThrow(() -> new RuntimeException("Rezyume topilmadi!"));

        resumeRepository.delete(resume);
        log.info("✅ Rezyume o'chirildi: resumeId={}", resumeId);
    }

    @Override
    @Transactional
    public void setActiveResume(Long userId, Long resumeId) {
        log.info("🔄 Rezyumeni aktiv qilish: userId={}, resumeId={}", userId, resumeId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        // Barcha rezyumelarni aktiv emas qilish
        List<Resume> allResumes = resumeRepository.findByJobSeekerId(profile.getId());
        for (Resume resume : allResumes) {
            resume.setIsActive(false);
            resumeRepository.save(resume);
        }

        // Tanlangan rezyumeni aktiv qilish
        Resume resume = resumeRepository.findByIdAndJobSeekerId(resumeId, profile.getId())
                .orElseThrow(() -> new RuntimeException("Rezyume topilmadi!"));
        resume.setIsActive(true);
        resumeRepository.save(resume);

        log.info("✅ Rezyume aktiv holatga o'tkazildi: resumeId={}", resumeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumeDto> getMyResumes(Long userId) {
        log.info("📋 Rezyumeler ro'yxati: userId={}", userId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        return resumeRepository.findByJobSeekerId(profile.getId())
                .stream()
                .map(this::toResumeDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeDto getActiveResume(Long userId) {
        log.info("📋 Aktiv rezyume: userId={}", userId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        return resumeRepository
                .findFirstByJobSeekerIdAndIsActiveTrueOrderByCreatedAtDesc(profile.getId())
                .map(this::toResumeDto)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeDto getResumeById(Long userId, Long resumeId) {
        log.info("📋 Rezyume ID bo'yicha: userId={}, resumeId={}", userId, resumeId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        Resume resume = resumeRepository.findByIdAndJobSeekerId(resumeId, profile.getId())
                .orElseThrow(() -> new RuntimeException("Rezyume topilmadi!"));

        return toResumeDto(resume);
    }

    // ============================================
    // SETTINGS SERVICES
    // ============================================

    @Override
    @Transactional
    public void updateSettings(Long userId, SettingsUpdateRequest request) {
        log.info("⚙️ Sozlamalar yangilash: userId={}", userId);
        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        if (request.language() != null) {
            profile.setLanguage(request.language());
        }

        profileRepository.save(profile);
        log.info("✅ Sozlamalar yangilandi: userId={}", userId);
    }

    // ============================================
    // YORDAMCHI METODLAR
    // ============================================

    private ResumeDto toResumeDto(Resume resume) {
        return new ResumeDto(
                resume.getId(),
                resume.getTitle(),
                resume.getFullName(),
                resume.getEmail(),
                resume.getPhoneNumber(),
                resume.getProfession(),
                resume.getExperience(),
                resume.getEducation(),
                resume.getSkills(),
                resume.getAbout(),
                resume.getIsActive(),
                resume.getCreatedAt(),
                resume.getUpdatedAt()
        );
    }
}
