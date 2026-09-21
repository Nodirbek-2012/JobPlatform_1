package uz.imaan.jobplatform.employer.job;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@RequiredArgsConstructor
public class JobVacancyService {

    private final JobVacancyRepository repository;

    @Transactional
    public JobVacancyResponseDto create(JobVacancyRequestDto dto) {
        JobVacancy vacancy = JobVacancy.builder()
                .employerChatId(dto.getEmployerChatId())
                .title(dto.getTitle())
                .category(dto.getCategory())
                .type(dto.getType())
                .salary(dto.getSalary())
                .workHours(dto.getWorkHours())
                .workerCount(dto.getWorkerCount())
                .requirements(dto.getRequirements())
                .phoneNumber(dto.getPhoneNumber())
                .companyName(dto.getCompanyName())
                .isActive(true)
                .notified(false)
                .build();

        return toDto(repository.save(vacancy));
    }

    public JobVacancyResponseDto getById(Long id) {
        JobVacancy vacancy = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vakansiya topilmadi: ID = " + id));
        return toDto(vacancy);
    }

    public Page<JobVacancyResponseDto> getAllActive(Pageable pageable) {
        return repository.findAll(pageable).map(this::toDto);
    }

    @Transactional
    public JobVacancyResponseDto update(Long id, JobVacancyRequestDto dto) {
        JobVacancy vacancy = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vakansiya topilmadi: ID = " + id));

        vacancy.setTitle(dto.getTitle());
        vacancy.setCategory(dto.getCategory());
        vacancy.setType(dto.getType());
        vacancy.setSalary(dto.getSalary());
        vacancy.setWorkHours(dto.getWorkHours());
        vacancy.setWorkerCount(dto.getWorkerCount());
        vacancy.setRequirements(dto.getRequirements());
        vacancy.setPhoneNumber(dto.getPhoneNumber());
        vacancy.setCompanyName(dto.getCompanyName());

        return toDto(repository.save(vacancy));
    }

    @Transactional
    public void delete(Long id) {
        JobVacancy vacancy = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vakansiya topilmadi: ID = " + id));
        vacancy.setIsActive(false); // Soft delete
        repository.save(vacancy);
    }

    private JobVacancyResponseDto toDto(JobVacancy entity) {
        return JobVacancyResponseDto.builder()
                .id(entity.getId())
                .employerChatId(entity.getEmployerChatId())
                .title(entity.getTitle())
                .category(entity.getCategory())
                .type(entity.getType())
                .salary(entity.getSalary())
                .workHours(entity.getWorkHours())
                .workerCount(entity.getWorkerCount())
                .requirements(entity.getRequirements())
                .phoneNumber(entity.getPhoneNumber())
                .isActive(entity.getIsActive())
                .companyName(entity.getCompanyName())
                .vacancyStatus(entity.getVacancyStatus())
                .sourceUrl(entity.getSourceUrl())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}