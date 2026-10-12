package uz.imaan.jobplatform.jobseeker.service.interfaces;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.jobseeker.dto.*;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;

import java.util.List;

public interface JobSeekerService {

    // PROFIL SERVICES
    JobSeekerProfileDto getProfileByUserId(Long userId);
    JobSeekerProfileDto updateProfile(Long userId, @Valid UpdateProfileRequest dto);
    JobSeekerProfile getProfileEntity(Long userId);

    // JOB APPLICATION SERVICES
    JobApplication applyForJob(Long userId, ApplyJobRequest request);
    Page<JobApplication> getMyApplicationsWithPagination(Long userId, Pageable pageable);
    List<JobApplicationDto> getMyApplications(Long userId);
    List<JobApplication> getMyActiveJobs(Long userId);
    void cancelApplication(Long userId, Long applicationId);

    // RESUME SERVICES
    ResumeDto createResume(Long userId, @Valid CreateResumeRequest request);
    ResumeDto updateResume(Long userId, Long resumeId, @Valid UpdateResumeRequest request);
    void deleteResume(Long userId, Long resumeId);
    void setActiveResume(Long userId, Long resumeId);
    List<ResumeDto> getMyResumes(Long userId);
    ResumeDto getActiveResume(Long userId);
    ResumeDto getResumeById(Long userId, Long resumeId);

    // SETTINGS SERVICES
    void updateSettings(Long userId, SettingsUpdateRequest request);

    // ⭐ VACANCIES BY CATEGORY
    List<JobVacancy> getVacanciesByCategory(String category);
}