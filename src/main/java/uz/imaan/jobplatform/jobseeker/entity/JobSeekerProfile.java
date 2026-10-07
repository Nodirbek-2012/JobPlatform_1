package uz.imaan.jobplatform.jobseeker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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

    @NotNull(message = "User ID kiritilishi shart")
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @NotBlank(message = "Ism familya kiritilishi shart")
    @Size(min = 3, max = 100, message = "Ism familya 3-100 belgidan iborat bolishi kerak")
    @Column(name = "full_name")
    private String fullName;

    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Telefon raqam noto'g'ri formatda")
    @Column(name = "phone_number")
    private String phoneNumber;

    @Size(max = 100, message = "Kasb 100 belgidan oshmasligi kerak")
    private String profession;

    @Min(value = 0, message = "Reyting 0 dan kam bo'lishi mumkin emas")
    @Max(value = 5, message = "Reyting 5 dan oshmasligi kerak")
    private Double rating;

    @Size(max = 10, message = "Til 10 belgidan oshmasligi kerak")
    private String language;

    // ============================================
    // PASPORT IXTIYORIY QILINDI (@NotBlank olib tashlandi)
    // ============================================
    @Column(name = "passport_number")
    private String passportNumber;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Size(max = 1000, message = "Portfolio 1000 belgidan oshmasligi kerak")
    @Column(length = 1000)
    private String portfolio;

    @PositiveOrZero(message = "Hamyon balansi manfiy bo'lishi mumkin emas")
    @Column(name = "wallet_balance", precision = 19, scale = 2)
    private BigDecimal walletBalance;

    // ============================================
    // YANGI FIELD'LAR
    // ============================================
    @Size(max = 1000, message = "Tajriba 1000 belgidan oshmasligi kerak")
    @Column(name = "experience", length = 1000)
    private String experience;

    @Size(max = 100, message = "Ish turi 100 belgidan oshmasligi kerak")
    @Column(name = "preferred_job_type")
    private String preferredJobType;

    @Size(max = 100, message = "Kategoriya 100 belgidan oshmasligi kerak")
    @Column(name = "category")
    private String category;

    // ============================================
    // ADMIN UCHUN
    // ============================================
    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "block_reason")
    private String blockReason;

    @OneToMany(mappedBy = "jobSeeker", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<BankCard> bankCards = new ArrayList<>();

    @Column(name = "telegram_id")
    private Long telegramId;

    @Column(name = "photo_file_id")
    private String photoFileId;

    @Column(name = "review_count")
    private Integer reviewCount = 0;
}