package uz.imaan.jobplatform.jobseeker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_seekers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSeekerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "passport_number", length = 20)
    private String passportNumber;

    @Column(name = "profession", length = 100)
    private String profession;

    @Column(name = "category", length = 100)
    private String category;

    private Double rating;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "experience", length = 1000)
    private String experience;

    @Column(name = "preferred_job_type", length = 100)
    private String preferredJobType;

    @Column(name = "language", length = 10)
    private String language;

    @Column(name = "wallet_balance", precision = 19, scale = 2)
    private BigDecimal walletBalance;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "block_reason")
    private String blockReason;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "portfolio", length = 1000)
    private String portfolio;

    @Column(name = "photo_file_id")
    private String photoFileId;

    // ===== BANK CARDS =====
    @OneToMany(mappedBy = "jobSeeker", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BankCard> bankCards = new ArrayList<>();
}