package uz.imaan.jobplatform.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admins")
@Getter
@Setter
public class Admin {


    private String name;

    // Геттеры и сеттеры (обязательно должны быть!)
        public Long getTelegramId() { return telegramId; }
        public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }
    // ... остальные геттеры/сеттеры

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;
    private String username;
    private String role; // e.g. "ROLE_ADMIN", "SUPER_ADMIN"
    private Boolean isActive;
    private String phoneNumber;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}