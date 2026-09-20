package uz.imaan.jobplatform.jobseeker.dto;

import jakarta.validation.constraints.NotNull;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;

public record UpdateApplicationStatusRequest(
        @NotNull(message = "Status kiritilishi shart")
        JobApplication.ApplicationStatus status
) {
}