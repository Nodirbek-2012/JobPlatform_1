package uz.imaan.jobplatform.jobseeker.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;
import uz.imaan.jobplatform.jobseeker.dto.ApplyJobRequest;
import uz.imaan.jobplatform.jobseeker.dto.JobApplicationDto;
import uz.imaan.jobplatform.jobseeker.dto.UpdateApplicationStatusRequest;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.repository.JobApplicationRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final JobVacancyRepository vacancyRepository;
    private final JobSeekerProfileRepository profileRepository;


    @Transactional
    public JobApplicationDto apply(ApplyJobRequest request) {
        if (!vacancyRepository.existsById(request.jobId())) {
            throw new RuntimeException("Vakansiya topilmadi: ID = " + request.jobId());
        }

        if (!profileRepository.existsById(request.jobSeekerId())) {
            throw new RuntimeException("Foydalanuvchi profili topilmadi: ID = " + request.jobSeekerId());
        }

        // Takroriy ariza tekshiruvi
        if (applicationRepository.existsByJobIdAndJobSeekerId(request.jobId(), request.jobSeekerId())) {
            throw new IllegalStateException("Siz ushbu vakansiyaga allaqachon ariza topshirgansiz!");
        }

        JobApplication application = JobApplication.builder()
                .jobId(request.jobId())
                .jobSeekerId(request.jobSeekerId())
                .coverLetter(request.comment()) // 👈 Sizdagi comment maydoni ulandi
                .status(JobApplication.ApplicationStatus.PENDING)
                .build();

        return toDto(applicationRepository.save(application));
    }

    public List<JobApplicationDto> getApplicationsBySeekerId(Long seekerId) {
        return applicationRepository.findByJobSeekerId(seekerId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public JobApplicationDto updateStatus(Long id, UpdateApplicationStatusRequest request) {
        JobApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ariza topilmadi: ID = " + id));

        application.setStatus(request.status());
        return toDto(applicationRepository.save(application));
    }


    private JobApplicationDto toDto(JobApplication app) {
        return new JobApplicationDto(
                app.getId(),
                app.getJobId(),
                app.getJobSeekerId(),
                app.getStatus().name(),
                app.getCreatedAt(),      // appliedAt o'rniga created_at beriladi
                app.getCoverLetter()
        );
    }
}