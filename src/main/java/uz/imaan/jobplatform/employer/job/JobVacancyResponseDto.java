package uz.imaan.jobplatform.employer.job;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class JobVacancyResponseDto {
    private Long id;
    private Long employerChatId;
    private String title;
    private String category;
    private String type;
    private String salary;
    private String workHours;
    private Integer workerCount;
    private String requirements;
    private String phoneNumber;
    private Boolean isActive;
    private String companyName;
    private String vacancyStatus;
    private String sourceUrl;
    private LocalDateTime createdAt;
}