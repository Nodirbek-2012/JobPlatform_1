package uz.imaan.jobplatform.employer.job;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "job_vacancies", indexes = {
        @Index(name = "idx_vac_active_cat", columnList = "isActive, category"),
        @Index(name = "idx_vac_employer", columnList = "employerChatId"),
        @Index(name = "idx_vac_notified", columnList = "notified, isActive"),
        @Index(name = "idx_vac_created_at", columnList = "createdAt")
})
public class JobVacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employer_chat_id")
    private Long employerChatId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    private String type;
    private String salary;
    private String workHours;
    private Integer workerCount;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    private String phoneNumber;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    private String companyName;

    // null → Faol, "FILLED" → To'ldirildi
    private String vacancyStatus;

    @Column(name = "external_id", unique = true)
    private String externalId;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Builder.Default
    @Column(name = "notified")
    private Boolean notified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}