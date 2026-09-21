package uz.imaan.jobplatform.jobseeker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplyJobRequest(

        @NotNull(message = "Job ID kiritilishi shart")
        Long jobId,

        @NotNull(message = "Nomzod ID si kiritilishi shart")
        Long jobSeekerId,

        @Size(max = 1000, message = "Izoh 1000 belgidan oshmasligi kerak")
        String comment

) {}