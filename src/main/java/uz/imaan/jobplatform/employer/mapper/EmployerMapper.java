package uz.imaan.jobplatform.employer.mapper;

import org.springframework.stereotype.Component;
import uz.imaan.jobplatform.employer.dto.EmployerCreateDTO;
import uz.imaan.jobplatform.employer.dto.EmployerResponseDTO;
import uz.imaan.jobplatform.employer.dto.EmployerUpdateDTO;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;

@Component
public class EmployerMapper {

    public EmployerEntity toEntity(EmployerCreateDTO dto) {
        if (dto == null) return null;

        return EmployerEntity.builder()
                .employerChatId(dto.getEmployerChatId())
                .inn(dto.getInn())
                .passportSeriesNumber(dto.getPassportSeriesNumber())
                .phoneNumber(dto.getPhoneNumber())
                .title(dto.getTitle())
                .category(dto.getCategory())
                .jobType(dto.getJobType())
                .salary(dto.getSalary())
                .workHours(dto.getWorkHours())
                .jobDate(dto.getJobDate())
                .workerCount(dto.getWorkerCount())
                .requirements(dto.getRequirements())
                .foodProvided(dto.getFoodProvided())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .build();
    }

    public EmployerResponseDTO toResponseDTO(EmployerEntity entity) {
        if (entity == null) return null;

        return EmployerResponseDTO.builder()
                .id(entity.getId())
                .employerChatId(entity.getEmployerChatId())
                .inn(entity.getInn())
                .passportSeriesNumber(entity.getPassportSeriesNumber())
                .fullName(entity.getFullName())
                .phoneNumber(entity.getPhoneNumber())
                .title(entity.getTitle())
                .category(entity.getCategory())
                .jobType(entity.getJobType())
                .salary(entity.getSalary())
                .workHours(entity.getWorkHours())
                .jobDate(entity.getJobDate())
                .workerCount(entity.getWorkerCount())
                .requirements(entity.getRequirements())
                .foodProvided(entity.getFoodProvided())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public void updateEntityFromDto(EmployerUpdateDTO dto, EmployerEntity entity) {
        if (dto == null || entity == null) return;

        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getJobType() != null) entity.setJobType(dto.getJobType());
        if (dto.getSalary() != null) entity.setSalary(String.valueOf(dto.getSalary()));
        if (dto.getWorkHours() != null) entity.setWorkHours(String.valueOf(dto.getWorkHours()));
        if (dto.getJobDate() != null) entity.setJobDate(dto.getJobDate());
        if (dto.getWorkerCount() != null) entity.setWorkerCount(dto.getWorkerCount());
        if (dto.getRequirements() != null) entity.setRequirements(dto.getRequirements());
        if (dto.getFoodProvided() != null) entity.setFoodProvided(dto.getFoodProvided());
        if (dto.getLatitude() != null) entity.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null) entity.setLongitude(dto.getLongitude());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
    }
}