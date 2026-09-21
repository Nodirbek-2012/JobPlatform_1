package uz.imaan.jobplatform.jobseeker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_applications", indexes = {
        @Index(name = "idx_app_seeker_job", columnList = "job_seeker_id, job_id"),
        @Index(name = "idx_app_status", columnList = "status"),
        @Index(name = "idx_app_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Vakansiya ID si kiritilishi shart")
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @NotNull(message = "Ish izlovchi ID si kiritilishi shart")
    @Column(name = "job_seeker_id", nullable = false)
    private Long jobSeekerId;

    @Size(max = 1000, message = "Cover letter 1000 belgidan oshmasligi kerak")
    @Column(name = "cover_letter", length = 1000)
    private String coverLetter;

    @NotNull(message = "Ariza holati kiritilishi shart")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ApplicationStatus {
        PENDING,      // ⏳ Kutilmoqda
        VIEWED,       // 👁 Ish beruvchi ko'rib chiqdi
        INTERVIEW,    // 📅 Suhbatga chaqirildi
        ACCEPTED,     // ✅ Qabul qilindi
        REJECTED,     // ❌ Rad etildi
        CANCELLED     // 🚫 Nomzod bekor qildi
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}