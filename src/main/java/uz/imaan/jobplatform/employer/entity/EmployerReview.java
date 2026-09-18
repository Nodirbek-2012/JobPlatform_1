package uz.imaan.jobplatform.employer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "employer_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employer_chat_id", nullable = false)
    private Long employerChatId;

    @Column(name = "job_seeker_id", nullable = false)
    private Long jobSeekerId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "rating", nullable = false)
    private Integer rating; // 1 dan 5 gacha

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}