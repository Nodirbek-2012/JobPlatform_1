package uz.imaan.jobplatform.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import uz.imaan.jobplatform.admin.category.Role;

@Entity
@Table(name = "admins",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_admin_telegram_id", columnNames = "telegram_id"),
                @UniqueConstraint(name = "uk_admin_username",   columnNames = "username")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", unique = true)
    private Long telegramId;

    @Column(length = 100)
    private String username;

    @Column(length = 100)
    private String name;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}