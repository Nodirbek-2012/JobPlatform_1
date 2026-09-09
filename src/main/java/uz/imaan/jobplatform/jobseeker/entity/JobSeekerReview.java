package uz.imaan.jobplatform.jobseeker.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_seeker_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSeekerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_seeker_id", nullable = false)
    private Long jobSeekerId;

    @Column(name = "employer_chat_id", nullable = false)
    private Long employerChatId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "rating", nullable = false)
    private Integer rating; // 1 dan 5 gacha

    @Column(name = "comment")
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

}
