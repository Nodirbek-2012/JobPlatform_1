package uz.imaan.jobplatform.admin.dto;

import lombok.Builder;

@Builder
public record AdminStatsDto(
        long totalAdmins,
        long totalWorkers,
        long totalEmployers,
        long activeJobs,
        long completedShifts,
        long totalVacancies,
        long activeShifts
) {}