package uz.imaan.jobplatform.employer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployerUpdateDTO {

        private String category;
        private String jobType;

        @NotNull(message = "Maosh bo'sh bo'lmasligi kerak")
        private Double salary;

        @Positive(message = "Ish soati musbat bo'lishi kerak")
        private Integer workHours;

        private LocalDate jobDate;
        private Integer workerCount;
        private String requirements;
        private Boolean foodProvided;
        private Double latitude;
        private Double longitude;
        private String status;
}