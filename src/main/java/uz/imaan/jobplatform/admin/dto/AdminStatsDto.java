package uz.imaan.jobplatform.admin.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsDto {

    // ===== ПОЛЬЗОВАТЕЛИ =====
    private long totalAdmins;
    private long totalWorkers;
    private long totalEmployers;
    private long totalUsers;

    // ===== ВАКАНСИИ =====
    private long totalVacancies;
    private long activeJobs;
    private long filledVacancies;

    // ===== ЗАЯВКИ =====
    private long totalApplications;
    private long pendingApplications;
    private long acceptedApplications;
    private long rejectedApplications;

    // ===== КАРТЫ =====
    private long totalBankCards;
    private long activeBankCards;

    // ===== ФИНАНСЫ =====
    private BigDecimal totalWalletBalance;
    private long totalTransactions;

    // ===== ОТЗЫВЫ =====
    private long totalReviews;
    private double averageRating;

    // ===== ПОДПИСКИ =====
    private long totalSubscriptions;

    // ===== КАТЕГОРИИ =====
    private long totalCategories;
    private Map<String, Long> vacanciesByCategory;
    private Map<String, Long> seekersByCategory;
    private Map<String, Long> subscriptionsByCategory;

    // ===== СМЕНЫ =====
    private long completedShifts;
    private long activeShifts;
}