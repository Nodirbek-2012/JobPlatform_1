package uz.imaan.jobplatform.employer.job;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "job_vacancies")
public class JobVacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
}