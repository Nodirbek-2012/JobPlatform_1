package uz.imaan.jobplatform.jobseeker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Karta raqami kiritilishi shart")
    @Size(min = 16, max = 16, message = "Karta raqami 16ta raqamdan iborat bolishi kerak")
    @Pattern(regexp = "^[0-9]{16}$", message = "Karta raqami faqat raqamlardan iborat bolishi kerak")
    @Column(name = "card_number", nullable = false, unique = true)
    private String cardNumber;

    @NotBlank(message = "Karta egasi ismi kiritilishi shart")
    @Size(min = 3, max = 100, message = "Karta egasi ismi 3-100 belgidan iborat bolishi kerak")
    @Column(name = "card_holder_name")
    private String cardHolderName;

    // To'g'ri bog'lanish:
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_seeker_id")
    private JobSeekerProfile jobSeeker;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
    }
}