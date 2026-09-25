package uz.imaan.jobplatform.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.imaan.jobplatform.admin.dto.AdminDtoTwo;
import uz.imaan.jobplatform.admin.dto.AdminStatsDto;
import uz.imaan.jobplatform.admin.dto.BlockUserDto;
import uz.imaan.jobplatform.admin.entity.Admin;
import uz.imaan.jobplatform.admin.mapper.AdminMapper;
import uz.imaan.jobplatform.admin.repository.AdminRepository;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;
import uz.imaan.jobplatform.employer.repository.EmployerRepository;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.entity.Subscription;
import uz.imaan.jobplatform.jobseeker.repository.BankCardRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobApplicationRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.jobseeker.repository.SubscriptionRepository;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final AdminMapper adminMapper;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final EmployerRepository employerRepository;
    private final JobVacancyRepository jobVacancyRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final BankCardRepository bankCardRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Value("${telegram.bot.admin-ids:}")
    private List<Long> adminChatIds;

    public AdminService(AdminRepository adminRepository,
                        AdminMapper adminMapper,
                        JobSeekerProfileRepository jobSeekerProfileRepository,
                        EmployerRepository employerRepository,
                        JobVacancyRepository jobVacancyRepository,
                        JobApplicationRepository jobApplicationRepository,
                        BankCardRepository bankCardRepository,
                        SubscriptionRepository subscriptionRepository) {
        this.adminRepository = adminRepository;
        this.adminMapper = adminMapper;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.employerRepository = employerRepository;
        this.jobVacancyRepository = jobVacancyRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.bankCardRepository = bankCardRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    // ─────────────────────────── READ ───────────────────────────

    public List<AdminDtoTwo> getAllAdmins() {
        return adminRepository.findAll().stream()
                .map(adminMapper::toDto)
                .toList();
    }

    public AdminDtoTwo getAdminById(Long id) {
        return adminRepository.findById(id)
                .map(adminMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Admin not found: " + id));
    }

    // ─────────────────────────── CREATE ───────────────────────────

    @Transactional
    public AdminDtoTwo createAdmin(AdminDtoTwo dto) {
        if (dto.telegramId() != null && adminRepository.existsByTelegramId(dto.telegramId())) {
            throw new IllegalStateException("Admin already exists: " + dto.telegramId());
        }
        Admin saved = adminRepository.save(adminMapper.toEntity(dto));
        log.info("Created admin id={}", saved.getId());
        return adminMapper.toDto(saved);
    }

    @Transactional
    public void deleteAdmin(Long id) {
        if (!adminRepository.existsById(id)) {
            throw new RuntimeException("Admin not found: " + id);
        }
        adminRepository.deleteById(id);
    }

    // ─────────────────────────── AUTH ───────────────────────────

    public boolean isAdmin(Long chatId) {
        log.info("═══════════ ПРОВЕРКА АДМИНА ═══════════");
        log.info("📱 Мой chatId из Telegram: {}", chatId);

        if (chatId == null) {
            log.warn("❌ chatId = null → НЕ админ");
            return false;
        }

        // 1. Проверка в БД
        boolean inDb = false;
        try {
            inDb = adminRepository.existsByTelegramId(chatId);
            log.info("🗄 Проверка БД (existsByTelegramId): {}", inDb);
        } catch (Exception e) {
            log.error("❌ Ошибка БД: {}", e.getMessage(), e);
        }

        // 2. Проверка в конфиге
        boolean inConfig = adminChatIds != null && adminChatIds.contains(chatId);
        log.info("⚙️  Список из application.yml (admin-ids): {}", adminChatIds);
        log.info("⚙️  Проверка конфига (contains): {}", inConfig);

        // 3. Итог
        boolean result = inDb || inConfig;
        log.info("✅ РЕЗУЛЬТАТ isAdmin = {}", result);
        log.info("═══════════════════════════════════════");

        return result;
    }

    // ─────────────────────────── STATS ───────────────────────────
    /**
     * Красиво отформатированный профиль РАБОТОДАТЕЛЯ (HTML).
     */
    public String getEmployerDetails(Long employerChatId) {
        EmployerEntity e = employerRepository.findByEmployerChatId(employerChatId)
                .orElse(null);

        // fallback — поиск по telegramId или по id
        if (e == null) {
            try { e = employerRepository.findByTelegramId(employerChatId).orElse(null); } catch (Exception ignored) {}
        }
        if (e == null) {
            e = employerRepository.findById(employerChatId).orElse(null);
        }
        if (e == null) {
            return "❌ Работодатель с ID " + employerChatId + " не найден.";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("🏢 <b>ПРОФИЛЬ РАБОТОДАТЕЛЯ</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");

        // --- Основное ---
        sb.append("🆔 <b>ID (System):</b> ").append(e.getId() != null ? e.getId() : "—").append("\n");
        sb.append("📱 <b>Telegram ID:</b> ").append(e.getTelegramId() != null ? e.getTelegramId() : "—").append("\n");
        sb.append("💬 <b>Chat ID:</b> ").append(e.getEmployerChatId() != null ? e.getEmployerChatId() : "—").append("\n");
        sb.append("🏢 <b>Компания:</b> ").append(safe(e.getCompanyName(), "Не указана")).append("\n");
        sb.append("👤 <b>ФИО:</b> ").append(safe(e.getFullName(), "Не указано")).append("\n");
        sb.append("🎂 <b>Возраст:</b> ").append(e.getAge() != null ? e.getAge() : "—").append("\n");

        // --- Контакты и документы ---
        sb.append("📞 <b>Телефон:</b> ").append(safe(e.getPhoneNumber(), "Не указан")).append("\n");
        sb.append("📄 <b>ИНН:</b> ").append(safe(e.getInn(), "Не указан")).append("\n");
        sb.append("🪪 <b>Паспорт:</b> ").append(safe(e.getPassportSeriesNumber(), "Не указан")).append("\n");

        // --- Компания ---
        if (e.getCompanyAddress() != null && !e.getCompanyAddress().isBlank())
            sb.append("📍 <b>Адрес:</b> ").append(e.getCompanyAddress()).append("\n");
        if (e.getCompanyWebsite() != null && !e.getCompanyWebsite().isBlank())
            sb.append("🔗 <b>Сайт:</b> <a href='").append(e.getCompanyWebsite()).append("'>")
                    .append(e.getCompanyWebsite()).append("</a>\n");
        if (e.getCompanySize() != null && !e.getCompanySize().isBlank())
            sb.append("👥 <b>Размер:</b> ").append(e.getCompanySize()).append("\n");
        if (e.getCompanyDescription() != null && !e.getCompanyDescription().isBlank())
            sb.append("📝 <b>Описание:</b> ").append(e.getCompanyDescription()).append("\n");

        // --- Профессиональное ---
        sb.append("💼 <b>Категория:</b> ").append(safe(e.getCategory(), "Не указана")).append("\n");
        sb.append("🧰 <b>Тип работы:</b> ").append(safe(e.getJobType(), "Не указан")).append("\n");
        sb.append("🌐 <b>Язык:</b> ").append(safe(e.getLanguage(), "Не указан")).append("\n");

        // --- Финансы и рейтинг ---
        sb.append("⭐ <b>Рейтинг:</b> ").append(
                e.getRating() != null ? String.format("%.1f", e.getRating()) : "0.0"
        ).append(" (Отзывов: ").append(e.getReviewCount() != null ? e.getReviewCount() : 0).append(")\n");

        // --- Статус ---
        sb.append("📌 <b>Статус:</b> ").append(safe(e.getStatus(), "—")).append("\n");

        // --- Кол-во вакансий ---
        try {
            long vacancyCount = jobVacancyRepository.countByEmployerChatId(e.getEmployerChatId());
            sb.append("💼 <b>Вакансий:</b> ").append(vacancyCount).append("\n");
        } catch (Exception ignored) { }

        sb.append("━━━━━━━━━━━━━━━━━━━━");
        return sb.toString();

    }
    @Transactional(readOnly = true)
    public AdminStatsDto getDetailedStats() {

        long totalAdmins = safeCount(adminRepository::count);
        long totalWorkers = safeCount(jobSeekerProfileRepository::count);
        long totalEmployers = safeCount(employerRepository::count);
        long totalUsers = totalAdmins + totalWorkers + totalEmployers;

        long totalVacancies = safeCount(jobVacancyRepository::count);
        long activeJobs = safeCount(jobVacancyRepository::countByIsActiveTrue);
        long filledVacancies = safeCount(() -> jobVacancyRepository.countByVacancyStatus("FILLED"));

        long totalApplications = 0;
        long pendingApplications = 0;
        long acceptedApplications = 0;
        long rejectedApplications = 0;
        try {
            totalApplications = jobApplicationRepository.count();
            pendingApplications = jobApplicationRepository.countByStatus(JobApplication.ApplicationStatus.PENDING);
            acceptedApplications = jobApplicationRepository.countByStatus(JobApplication.ApplicationStatus.ACCEPTED);
            rejectedApplications = jobApplicationRepository.countByStatus(JobApplication.ApplicationStatus.REJECTED);
        } catch (Exception e) {
            log.warn("⚠️ Ошибка подсчёта заявок: {}", e.getMessage());
        }

        long totalBankCards = safeCount(bankCardRepository::count);
        long activeBankCards = safeCount(bankCardRepository::countByIsActiveTrue);

        BigDecimal totalWalletBalance = BigDecimal.ZERO;
        List<JobSeekerProfile> allSeekers = new ArrayList<>();
        try {
            allSeekers = jobSeekerProfileRepository.findAll();
            for (JobSeekerProfile seeker : allSeekers) {
                if (seeker.getWalletBalance() != null) {
                    totalWalletBalance = totalWalletBalance.add(seeker.getWalletBalance());
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка подсчёта баланса: {}", e.getMessage());
        }

        long totalReviews = 0;
        double averageRating = 0.0;
        try {
            double sumRating = 0;
            long countRating = 0;
            for (JobSeekerProfile seeker : allSeekers) {
                if (seeker.getReviewCount() != null) totalReviews += seeker.getReviewCount();
                if (seeker.getRating() != null && seeker.getRating() > 0) {
                    sumRating += seeker.getRating();
                    countRating++;
                }
            }
            if (countRating > 0) averageRating = sumRating / countRating;
        } catch (Exception e) {
            log.warn("⚠️ Ошибка подсчёта отзывов: {}", e.getMessage());
        }

        long totalSubscriptions = safeCount(subscriptionRepository::count);

        Map<String, Long> vacanciesByCategory = new HashMap<>();
        Map<String, Long> seekersByCategory = new HashMap<>();
        Map<String, Long> subscriptionsByCategory = new HashMap<>();

        try {
            List<JobVacancy> allVacancies = jobVacancyRepository.findAll();
            for (JobVacancy v : allVacancies) {
                String cat = (v.getCategory() != null && !v.getCategory().isBlank())
                        ? v.getCategory().trim() : "Без категории";
                vacanciesByCategory.merge(cat, 1L, Long::sum);
            }
            for (JobSeekerProfile seeker : allSeekers) {
                String cat = (seeker.getCategory() != null && !seeker.getCategory().isBlank())
                        ? seeker.getCategory().trim()
                        : (seeker.getProfession() != null && !seeker.getProfession().isBlank()
                        ? seeker.getProfession().trim() : "Без категории");
                seekersByCategory.merge(cat, 1L, Long::sum);
            }
            List<Subscription> allSubs = subscriptionRepository.findAll();
            for (Subscription sub : allSubs) {
                String cat = (sub.getCategory() != null && !sub.getCategory().isBlank())
                        ? sub.getCategory().trim() : "Без категории";
                subscriptionsByCategory.merge(cat, 1L, Long::sum);
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка подсчёта категорий: {}", e.getMessage());
        }

        Set<String> allCats = new HashSet<>();
        allCats.addAll(vacanciesByCategory.keySet());
        allCats.addAll(seekersByCategory.keySet());
        allCats.addAll(subscriptionsByCategory.keySet());
        long totalCategories = allCats.size();

        return AdminStatsDto.builder()
                .totalAdmins(totalAdmins)
                .totalWorkers(totalWorkers)
                .totalEmployers(totalEmployers)
                .totalUsers(totalUsers)
                .totalVacancies(totalVacancies)
                .activeJobs(activeJobs)
                .filledVacancies(filledVacancies)
                .totalApplications(totalApplications)
                .pendingApplications(pendingApplications)
                .acceptedApplications(acceptedApplications)
                .rejectedApplications(rejectedApplications)
                .totalBankCards(totalBankCards)
                .activeBankCards(activeBankCards)
                .totalWalletBalance(totalWalletBalance)
                .totalTransactions(0L)
                .totalReviews(totalReviews)
                .averageRating(averageRating)
                .totalSubscriptions(totalSubscriptions)
                .totalCategories(totalCategories)
                .vacanciesByCategory(vacanciesByCategory)
                .seekersByCategory(seekersByCategory)
                .subscriptionsByCategory(subscriptionsByCategory)
                .completedShifts(0L)
                .activeShifts(0L)
                .build();
    }

    // ─────────────────────────── BLOCK / UNBLOCK ───────────────────────────

    @Transactional
    public void blockUser(BlockUserDto dto) {
        JobSeekerProfile worker = jobSeekerProfileRepository.findByUserId(dto.userId())
                .orElseThrow(() -> new RuntimeException("User not found: " + dto.userId()));
        worker.setIsActive(false);
        worker.setBlockReason(dto.reason());
        jobSeekerProfileRepository.save(worker);
    }

    @Transactional
    public void unblockUser(Long userId) {
        JobSeekerProfile worker = jobSeekerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        worker.setIsActive(true);
        worker.setBlockReason(null);
        jobSeekerProfileRepository.save(worker);
    }

    // ─────────────────────────── LISTS (с пагинацией) ───────────────────────────

    public List<JobSeekerProfile> getAllJobSeekers() {
        return jobSeekerProfileRepository.findAll();
    }

    public List<EmployerEntity> getAllEmployers() {
        return employerRepository.findAll();
    }

    /**
     * Страницы списка рабочих (по 5 на страницу).
     */
    @Transactional(readOnly = true)
    public List<String> getWorkersListPages() {
        List<String> pages = new ArrayList<>();
        List<JobSeekerProfile> workers = jobSeekerProfileRepository.findAll();

        if (workers.isEmpty()) {
            pages.add("👷 Список рабочих пуст.");
            return pages;
        }

        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) workers.size() / pageSize);

        for (int p = 0; p < totalPages; p++) {
            int start = p * pageSize;
            int end = Math.min(start + pageSize, workers.size());

            StringBuilder sb = new StringBuilder();
            sb.append("👷 Список всех рабочих:\n\n");

            for (int i = start; i < end; i++) {
                JobSeekerProfile w = workers.get(i);
                Long displayId = w.getTelegramId() != null ? w.getTelegramId() : w.getUserId();
                sb.append(i + 1).append(". ")
                        .append(safe(w.getFullName(), "Без имени"))
                        .append(" | ID: ").append(displayId != null ? displayId : "—")
                        .append(" | 📞 ").append(safe(w.getPhoneNumber(), "—"))
                        .append("\n");
            }
            pages.add(sb.toString());
        }
        return pages;
    }

    /**
     * Страницы списка работодателей (по 5 на страницу).
     */
    @Transactional(readOnly = true)
    public List<String> getEmployersListPages() {
        List<String> pages = new ArrayList<>();
        List<EmployerEntity> employers = employerRepository.findAll();

        if (employers.isEmpty()) {
            pages.add("🏢 Список работодателей пуст.");
            return pages;
        }

        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) employers.size() / pageSize);

        for (int p = 0; p < totalPages; p++) {
            int start = p * pageSize;
            int end = Math.min(start + pageSize, employers.size());

            StringBuilder sb = new StringBuilder();
            sb.append("🏢 Список всех работодателей:\n\n");

            for (int i = start; i < end; i++) {
                EmployerEntity e = employers.get(i);
                Long displayId = e.getTelegramId() != null
                        ? e.getTelegramId()
                        : (e.getEmployerChatId() != null ? e.getEmployerChatId() : e.getId());

                sb.append(i + 1).append(". ")
                        .append(safe(e.getCompanyName(), "Без названия"))
                        .append(" | Паспорт: ").append(safe(e.getPassportSeriesNumber(), "—"))
                        .append(" | ID: ").append(displayId != null ? displayId : "—")
                        .append(" | 📞 ").append(safe(e.getPhoneNumber(), "—"))
                        .append("\n");
            }
            pages.add(sb.toString());
        }
        return pages;
    }

    // ─────────────────────────── /info (одним сообщением) ───────────────────────────

    public String getUserInfoByTgId(Long tgId) {
        if (tgId == null) return "⚠️ Неверный ID.";

        Optional<JobSeekerProfile> workerOpt = jobSeekerProfileRepository.findByTelegramId(tgId);
        if (workerOpt.isEmpty()) {
            workerOpt = jobSeekerProfileRepository.findByUserId(tgId);
        }

        Optional<EmployerEntity> employerOpt = employerRepository.findById(tgId);
        if (employerOpt.isEmpty()) {
            try { employerOpt = employerRepository.findByTelegramId(tgId); } catch (Exception ignored) {}
        }
        if (employerOpt.isEmpty()) {
            try { employerOpt = employerRepository.findByEmployerChatId(tgId); } catch (Exception ignored) {}
        }

        if (workerOpt.isEmpty() && employerOpt.isEmpty()) {
            return "❌ Пользователь с ID " + tgId + " не найден.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("👤 Информация о пользователе\n");
        sb.append("🆔 ID: ").append(tgId).append("\n");
        sb.append("──────────────────────\n\n");

        workerOpt.ifPresent(w -> {
            sb.append("👷 СОИСКАТЕЛЬ\n\n");
            sb.append("📛 ФИО: ").append(safe(w.getFullName(), "—")).append("\n");
            sb.append("📞 Телефон: ").append(safe(w.getPhoneNumber(), "—")).append("\n");
            sb.append("🪪 Паспорт: ").append(safe(w.getPassportNumber(), "—")).append("\n");
            sb.append("💼 Профессия: ").append(safe(w.getProfession(), "—")).append("\n");
            sb.append("📂 Категория: ").append(safe(w.getCategory(), "—")).append("\n");
            sb.append("⭐ Рейтинг: ").append(
                    w.getRating() != null ? String.format("%.1f", w.getRating()) : "0.0"
            ).append(" (отзывов: ").append(
                    w.getReviewCount() != null ? w.getReviewCount() : 0
            ).append(")\n");
            sb.append("📊 Опыт: ").append(safe(w.getExperience(), "—")).append("\n");
            sb.append("🎯 Желаемая работа: ").append(safe(w.getPreferredJobType(), "—")).append("\n");
            sb.append("🌐 Язык: ").append(safe(w.getLanguage(), "—")).append("\n");
            sb.append("💰 Баланс: ").append(
                    w.getWalletBalance() != null ? w.getWalletBalance().toPlainString() + " сум" : "0 сум"
            ).append("\n");
            sb.append("📌 Статус: ").append(
                    Boolean.TRUE.equals(w.getIsActive()) ? "Активен ✅" : "Заблокирован ❌"
            ).append("\n");

            if (Boolean.FALSE.equals(w.getIsActive()) && w.getBlockReason() != null) {
                sb.append("💬 Причина: ").append(w.getBlockReason()).append("\n");
            }
            if (w.getUserId() != null) sb.append("🆔 User ID: ").append(w.getUserId()).append("\n");
            if (w.getTelegramId() != null) sb.append("📱 Telegram ID: ").append(w.getTelegramId()).append("\n");
            if (w.getAvatarUrl() != null && !w.getAvatarUrl().isBlank())
                sb.append("🖼 Аватар: ").append(w.getAvatarUrl()).append("\n");
            if (w.getPortfolio() != null && !w.getPortfolio().isBlank())
                sb.append("🔗 Портфолио: ").append(w.getPortfolio()).append("\n");
            sb.append("\n");
        });

        employerOpt.ifPresent(e -> {
            sb.append("🏢 РАБОТОДАТЕЛЬ\n\n");
            sb.append("🏢 Компания: ").append(safe(e.getCompanyName(), "—")).append("\n");
            sb.append("👤 ФИО: ").append(safe(e.getFullName(), "—")).append("\n");
            sb.append("📞 Телефон: ").append(safe(e.getPhoneNumber(), "—")).append("\n");
            sb.append("📄 ИНН: ").append(safe(e.getInn(), "—")).append("\n");
            sb.append("🪪 Паспорт: ").append(safe(e.getPassportSeriesNumber(), "—")).append("\n");
            sb.append("🌐 Язык: ").append(safe(e.getLanguage(), "—")).append("\n");
            sb.append("📌 Статус: ").append(safe(e.getStatus(), "—")).append("\n");

            if (e.getCompanyAddress() != null && !e.getCompanyAddress().isBlank())
                sb.append("📍 Адрес: ").append(e.getCompanyAddress()).append("\n");
            if (e.getCompanyWebsite() != null && !e.getCompanyWebsite().isBlank())
                sb.append("🔗 Сайт: ").append(e.getCompanyWebsite()).append("\n");
            if (e.getCompanySize() != null && !e.getCompanySize().isBlank())
                sb.append("👥 Размер: ").append(e.getCompanySize()).append("\n");
            if (e.getCompanyDescription() != null && !e.getCompanyDescription().isBlank())
                sb.append("📝 Описание: ").append(e.getCompanyDescription()).append("\n");
            if (e.getTelegramId() != null) sb.append("📱 Telegram ID: ").append(e.getTelegramId()).append("\n");
            if (e.getEmployerChatId() != null) sb.append("💬 Chat ID: ").append(e.getEmployerChatId()).append("\n");

            try {
                long vacancyCount = jobVacancyRepository.countByEmployerChatId(e.getEmployerChatId());
                sb.append("💼 Вакансий: ").append(vacancyCount).append("\n");
            } catch (Exception ignored) { }

            sb.append("\n");
        });

        return sb.toString();
    }

    // ─────────────────────────── HELPERS ───────────────────────────

    private static String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static long safeCount(CountSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return 0L;
        }
    }

    @FunctionalInterface
    private interface CountSupplier {
        long get() throws Exception;
    }
    public String getWorkerDetails(Long userId) {
        JobSeekerProfile w = jobSeekerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Worker not found: " + userId));

        // Формируем строку со всей доступной информацией
        StringBuilder sb = new StringBuilder();

        sb.append("👤 <b>ПРОФИЛЬ ИСПОЛНИТЕЛЯ</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");

        // --- Основная информация ---
        sb.append("🆔 <b>ID (System):</b> ").append(w.getUserId() != null ? w.getUserId() : "—").append("\n");
        sb.append("📱 <b>Telegram ID:</b> ").append(w.getTelegramId() != null ? w.getTelegramId() : "—").append("\n");
        sb.append("📛 <b>ФИО:</b> ").append(safe(w.getFullName(), "Не указано")).append("\n");

        // --- Контакты и Документы ---
        sb.append("📞 <b>Телефон:</b> ").append(safe(w.getPhoneNumber(), "Не указан")).append("\n");
        sb.append("🪪 <b>Паспорт:</b> ").append(safe(w.getPassportNumber(), "Не указан")).append("\n");

        // --- Профессиональная информация ---
        sb.append("💼 <b>Профессия:</b> ").append(safe(w.getProfession(), "Не указана")).append("\n");
        sb.append("📂 <b>Категория:</b> ").append(safe(w.getCategory(), "Не указана")).append("\n");
        sb.append("🎯 <b>Желаемая работа:</b> ").append(safe(w.getPreferredJobType(), "Не указана")).append("\n");
        sb.append("📊 <b>Опыт:</b> ").append(safe(w.getExperience(), "Не указан")).append("\n");
        sb.append("🌐 <b>Язык:</b> ").append(safe(w.getLanguage(), "Не указан")).append("\n");

        // --- Финансы и Рейтинг ---
        sb.append("💰 <b>Баланс:</b> ").append(
                w.getWalletBalance() != null ? w.getWalletBalance().toPlainString() + " сум" : "0 сум"
        ).append("\n");
        sb.append("⭐ <b>Рейтинг:</b> ").append(
                w.getRating() != null ? String.format("%.1f", w.getRating()) : "0.0"
        ).append(" (Отзывов: ").append(w.getReviewCount() != null ? w.getReviewCount() : 0).append(")\n");

        // --- Статус и Блокировка ---
        boolean isActive = Boolean.TRUE.equals(w.getIsActive());
        sb.append("📌 <b>Статус:</b> ").append(isActive ? "Активен ✅" : "Заблокирован ❌").append("\n");
        sb.append("🔒 <b>Заблокирован:</b> ").append(isActive ? "Нет" : "Да").append("\n");
        if (!isActive) {
            sb.append("💬 <b>Причина:</b> ").append(safe(w.getBlockReason(), "Не указана")).append("\n");
        }

        // --- Дополнительно (Ссылки) ---
        if (w.getAvatarUrl() != null && !w.getAvatarUrl().isBlank()) {
            sb.append("🖼 <b>Аватар:</b> <a href='").append(w.getAvatarUrl()).append("'>Ссылка</a>\n");
        }
        if (w.getPortfolio() != null && !w.getPortfolio().isBlank()) {
            sb.append("🔗 <b>Портфолио:</b> <a href='").append(w.getPortfolio()).append("'>Ссылка</a>\n");
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━");

        return sb.toString();
    }
    /**
     * Возвращает данные одной страницы рабочих:
     * [0] = текст сообщения
     * [1] = список ID пользователей (для callback-кнопок)
     */
    @Transactional(readOnly = true)
    public Object[] getWorkersListPageData(int page) {
        List<JobSeekerProfile> workers = jobSeekerProfileRepository.findAll();

        if (workers.isEmpty()) {
            return new Object[]{"👷 Список рабочих пуст.", new ArrayList<Long>()};
        }

        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) workers.size() / pageSize);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int start = page * pageSize;
        int end = Math.min(start + pageSize, workers.size());

        StringBuilder sb = new StringBuilder();
        sb.append("👷 Список всех рабочих:\n\n");

        List<Long> idsOnPage = new ArrayList<>();
        for (int i = start; i < end; i++) {
            JobSeekerProfile w = workers.get(i);
            Long displayId = w.getTelegramId() != null ? w.getTelegramId() : w.getUserId();
            sb.append(i + 1).append(". ")
                    .append(safe(w.getFullName(), "Без имени"))
                    .append(" | ID: ").append(displayId != null ? displayId : "—")
                    .append(" | 📞 ").append(safe(w.getPhoneNumber(), "—"))
                    .append("\n");
            if (displayId != null) idsOnPage.add(displayId);
        }

        return new Object[]{sb.toString(), idsOnPage, totalPages};
    }
    @Transactional(readOnly = true)
    public Object[] getEmployersListPageData(int page) {
        List<EmployerEntity> employers = employerRepository.findAll();

        if (employers.isEmpty()) {
            return new Object[]{"🏢 Список работодателей пуст.", new ArrayList<Long>(), 1};
        }

        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) employers.size() / pageSize);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int start = page * pageSize;
        int end = Math.min(start + pageSize, employers.size());

        StringBuilder sb = new StringBuilder();
        sb.append("🏢 Список всех работодателей:\n\n");

        List<Long> idsOnPage = new ArrayList<>();
        for (int i = start; i < end; i++) {
            EmployerEntity e = employers.get(i);
            Long displayId = e.getTelegramId() != null
                    ? e.getTelegramId()
                    : (e.getEmployerChatId() != null ? e.getEmployerChatId() : e.getId());

            sb.append(i + 1).append(". ")
                    .append(safe(e.getCompanyName(), "Без названия"))
                    .append(" | Паспорт: ").append(safe(e.getPassportSeriesNumber(), "—"))
                    .append(" | ID: ").append(displayId != null ? displayId : "—")
                    .append(" | 📞 ").append(safe(e.getPhoneNumber(), "—"))
                    .append("\n");
            if (displayId != null) idsOnPage.add(displayId);
        }

        return new Object[]{sb.toString(), idsOnPage, totalPages};
    }}
    /**
     * Красиво отформатированный профиль РАБОТОДАТЕЛЯ (HTML).
     */
