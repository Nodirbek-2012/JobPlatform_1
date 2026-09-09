package uz.imaan.jobplatform.employer.mapper;

import uz.imaan.jobplatform.employer.entity.EmployerEntity;
import org.springframework.stereotype.Component;
import uz.imaan.jobplatform.employer.dto.EmployerCreateDTO;
import uz.imaan.jobplatform.employer.dto.EmployerResponseDTO;

@Component
public class EmployerMapper {

    public EmployerEntity toEntity(EmployerCreateDTO dto) {
        if (dto == null) return null;

        return EmployerEntity.builder()
                .employerChatId(dto.employerChatId())
                .inn(dto.inn())
                .passportSeriesNumber(dto.passportSeriesNumber())
                .phoneNumber(dto.phoneNumber())
                .title(dto.title())
                .category(dto.category())
                .jobType(dto.jobType())
                .salary(dto.salary())
                .workHours(dto.workHours())
                .jobDate(dto.jobDate())
                .workerCount(dto.workerCount())
                .requirements(dto.requirements())
                .foodProvided(dto.foodProvided())
                .latitude(dto.latitude())
                .longitude(dto.longitude())
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
}