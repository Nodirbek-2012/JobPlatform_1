package uz.imaan.jobplatform.employer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long employerChatId;

    private String fullName;
    private String title;
    private String category;
    private String jobType;
    private String salary;         // String formatida
    private String workHours;      // String formatida
    private String requirements;
    private String status;
    private LocalDateTime createdAt;

    private String inn;
    private String passportSeriesNumber;
    private String phoneNumber;
    private LocalDate jobDate;
    private Integer workerCount;
    private Boolean foodProvided;
    private Double latitude;
    private Double longitude;
    private String language;
    private String companyName;
    private Long telegramId;// To'g'ri camelCase formatda
}