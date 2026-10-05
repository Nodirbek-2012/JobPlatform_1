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

    // ─────────────────────── READ ───────────────────────

    public List<AdminDtoTwo> getAllAdmins() {
        return adminRepository.findAll().stream().map(adminMapper::toDto).toList();
    }

    public AdminDtoTwo getAdminById(Long id) {
        return adminRepository.findById(id).map(adminMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Admin not found: " + id));
    }

    // ─────────────────────── CREATE ───────────────────────

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
        if (!adminRepository.existsById(id)) throw new RuntimeException("Admin not found: " + id);
        adminRepository.deleteById(id);
    }

    // ─────────────────────── AUTH ───────────────────────

    public boolean isAdmin(Long chatId) {
        log.info("═══════════ ПРОВЕРКА АДМИНА ═══════════");
        log.info("📱 Мой chatId из Telegram: {}", chatId);
        if (chatId == null) { log.warn("❌ chatId = null"); return false; }

        boolean inDb = false;
        try {
            inDb = adminRepository.existsByTelegramId(chatId);
            log.info("🗄 Проверка БД: {}", inDb);
        } catch (Exception e) { log.error("❌ Ошибка БД: {}", e.getMessage()); }

        boolean inConfig = adminChatIds != null && adminChatIds.contains(chatId);
        log.info("⚙️ Список из yml: {}", adminChatIds);
        log.info("⚙️ contains: {}", inConfig);

        boolean result = inDb || inConfig;
        log.info("✅ РЕЗУЛЬТАТ isAdmin = {}", result);
        log.info("═══════════════════════════════════════");
        return result;
    }

    // ─────────────────────── STATS ───────────────────────

    @Transactional(readOnly = true)
    public AdminStatsDto getDetailedStats() {
        long totalAdmins = safeCount(adminRepository::count);
        long totalWorkers = safeCount(jobSeekerProfileRepository::count);
        long totalEmployers = safeCount(employerRepository::count);
        long totalUsers = totalAdmins + totalWorkers + totalEmployers;

        long totalVacancies = safeCount(jobVacancyRepository::count);
        long activeJobs = safeCount(jobVacancyRepository::countByIsActiveTrue);
        long filledVacancies = safeCount(() -> jobVacancyRepository.countByVacancyStatus("FILLED"));

        long totalApplications = 0, pendingApplications = 0, acceptedApplications = 0, rejectedApplications = 0;
        try {
            totalApplications = jobApplicationRepository.count();
            pendingApplications = jobApplicationRepository.countByStatus(JobApplication.ApplicationStatus.PENDING);
            acceptedApplications = jobApplicationRepository.countByStatus(JobApplication.ApplicationStatus.ACCEPTED);
            rejectedApplications = jobApplicationRepository.countByStatus(JobApplication.ApplicationStatus.REJECTED);
        } catch (Exception e) { log.warn("⚠️ Заявки: {}", e.getMessage()); }

        long totalBankCards = safeCount(bankCardRepository::count);
        long activeBankCards = safeCount(bankCardRepository::countByIsActiveTrue);

        BigDecimal totalWalletBalance = BigDecimal.ZERO;
        List<JobSeekerProfile> allSeekers = new ArrayList<>();
        try {
            allSeekers = jobSeekerProfileRepository.findAll();
            for (JobSeekerProfile s : allSeekers) {
                if (s.getWalletBalance() != null) totalWalletBalance = totalWalletBalance.add(s.getWalletBalance());
            }
        } catch (Exception e) { log.warn("⚠️ Баланс: {}", e.getMessage()); }

        long totalReviews = 0;
        double averageRating = 0.0;
        try {
            double sum = 0; long cnt = 0;
            for (JobSeekerProfile s : allSeekers) {
                if (s.getReviewCount() != null) totalReviews += s.getReviewCount();
                if (s.getRating() != null && s.getRating() > 0) { sum += s.getRating(); cnt++; }
            }
            if (cnt > 0) averageRating = sum / cnt;
        } catch (Exception e) { log.warn("⚠️ Рейтинг: {}", e.getMessage()); }

        long totalSubscriptions = safeCount(subscriptionRepository::count);

        Map<String, Long> vacanciesByCategory = new HashMap<>();
        Map<String, Long> seekersByCategory = new HashMap<>();
        Map<String, Long> subscriptionsByCategory = new HashMap<>();

        try {
            for (JobVacancy v : jobVacancyRepository.findAll()) {
                String cat = (v.getCategory() != null && !v.getCategory().isBlank()) ? v.getCategory().trim() : "Без категории";
                vacanciesByCategory.merge(cat, 1L, Long::sum);
            }
            for (JobSeekerProfile s : allSeekers) {
                String cat = (s.getCategory() != null && !s.getCategory().isBlank()) ? s.getCategory().trim()
                        : (s.getProfession() != null && !s.getProfession().isBlank() ? s.getProfession().trim() : "Без категории");
                seekersByCategory.merge(cat, 1L, Long::sum);
            }
            for (Subscription sub : subscriptionRepository.findAll()) {
                String cat = (sub.getCategory() != null && !sub.getCategory().isBlank()) ? sub.getCategory().trim() : "Без категории";
                subscriptionsByCategory.merge(cat, 1L, Long::sum);
            }
        } catch (Exception e) { log.warn("⚠️ Категории: {}", e.getMessage()); }

        Set<String> allCats = new HashSet<>();
        allCats.addAll(vacanciesByCategory.keySet());
        allCats.addAll(seekersByCategory.keySet());
        allCats.addAll(subscriptionsByCategory.keySet());

        return AdminStatsDto.builder()
                .totalAdmins(totalAdmins).totalWorkers(totalWorkers).totalEmployers(totalEmployers).totalUsers(totalUsers)
                .totalVacancies(totalVacancies).activeJobs(activeJobs).filledVacancies(filledVacancies)
                .totalApplications(totalApplications).pendingApplications(pendingApplications)
                .acceptedApplications(acceptedApplications).rejectedApplications(rejectedApplications)
                .totalBankCards(totalBankCards).activeBankCards(activeBankCards)
                .totalWalletBalance(totalWalletBalance).totalTransactions(0L)
                .totalReviews(totalReviews).averageRating(averageRating)
                .totalSubscriptions(totalSubscriptions).totalCategories(allCats.size())
                .vacanciesByCategory(vacanciesByCategory).seekersByCategory(seekersByCategory)
                .subscriptionsByCategory(subscriptionsByCategory)
                .completedShifts(0L).activeShifts(0L)
                .build();
    }

    // ─────────────────────── BLOCK / UNBLOCK ───────────────────────

    @Transactional
    public void blockUser(BlockUserDto dto) {
        JobSeekerProfile w = jobSeekerProfileRepository.findByUserId(dto.userId())
                .orElseThrow(() -> new RuntimeException("User not found: " + dto.userId()));
        w.setIsActive(false);
        w.setBlockReason(dto.reason());
        jobSeekerProfileRepository.save(w);
    }

    @Transactional
    public void unblockUser(Long userId) {
        JobSeekerProfile w = jobSeekerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        w.setIsActive(true);
        w.setBlockReason(null);
        jobSeekerProfileRepository.save(w);
    }

    // ─────────────────────── LISTS ───────────────────────

    public List<JobSeekerProfile> getAllJobSeekers() { return jobSeekerProfileRepository.findAll(); }
    public List<EmployerEntity> getAllEmployers() { return employerRepository.findAll(); }

    // ─────────────────────── DETAILS ───────────────────────

    public String getWorkerDetails(Long userId) {
        JobSeekerProfile w = jobSeekerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Worker not found: " + userId));

        StringBuilder sb = new StringBuilder();
        sb.append("👤 <b>ПРОФИЛЬ ИСПОЛНИТЕЛЯ</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🆔 <b>ID (System):</b> ").append(w.getUserId() != null ? w.getUserId() : "—").append("\n");
        sb.append("📱 <b>Telegram ID:</b> ").append(w.getTelegramId() != null ? w.getTelegramId() : "—").append("\n");
        sb.append("📛 <b>ФИО:</b> ").append(safe(w.getFullName(), "Не указано")).append("\n");
        sb.append("📞 <b>Телефон:</b> ").append(safe(w.getPhoneNumber(), "Не указан")).append("\n");
        sb.append("🪪 <b>Паспорт:</b> ").append(safe(w.getPassportNumber(), "Не указан")).append("\n");
        sb.append("💼 <b>Профессия:</b> ").append(safe(w.getProfession(), "Не указана")).append("\n");
        sb.append("📂 <b>Категория:</b> ").append(safe(w.getCategory(), "Не указана")).append("\n");
        sb.append("🎯 <b>Желаемая работа:</b> ").append(safe(w.getPreferredJobType(), "Не указана")).append("\n");
        sb.append("📊 <b>Опыт:</b> ").append(safe(w.getExperience(), "Не указан")).append("\n");
        sb.append("🌐 <b>Язык:</b> ").append(safe(w.getLanguage(), "Не указан")).append("\n");
        sb.append("💰 <b>Баланс:</b> ").append(w.getWalletBalance() != null ? w.getWalletBalance().toPlainString() + " сум" : "0 сум").append("\n");
        sb.append("⭐ <b>Рейтинг:</b> ").append(w.getRating() != null ? String.format("%.1f", w.getRating()) : "0.0")
                .append(" (Отзывов: ").append(w.getReviewCount() != null ? w.getReviewCount() : 0).append(")\n");

        boolean isActive = Boolean.TRUE.equals(w.getIsActive());
        sb.append("📌 <b>Статус:</b> ").append(isActive ? "Активен ✅" : "Заблокирован ❌").append("\n");
        sb.append("🔒 <b>Заблокирован:</b> ").append(isActive ? "Нет" : "Да").append("\n");
        if (!isActive) sb.append("💬 <b>Причина:</b> ").append(safe(w.getBlockReason(), "Не указана")).append("\n");

        if (w.getAvatarUrl() != null && !w.getAvatarUrl().isBlank())
            sb.append("🖼 <b>Аватар:</b> <a href='").append(w.getAvatarUrl()).append("'>Ссылка</a>\n");
        if (w.getPortfolio() != null && !w.getPortfolio().isBlank())
            sb.append("🔗 <b>Портфолио:</b> <a href='").append(w.getPortfolio()).append("'>Ссылка</a>\n");

        sb.append("━━━━━━━━━━━━━━━━━━━━");
        return sb.toString();
    }

    public String getEmployerDetails(Long employerChatId) {
        EmployerEntity e = employerRepository.findByEmployerChatId(employerChatId).orElse(null);
        if (e == null) {
            try { e = employerRepository.findByTelegramId(employerChatId).orElse(null); } catch (Exception ignored) {}
        }
        if (e == null) e = employerRepository.findById(employerChatId).orElse(null);
        if (e == null) return "❌ Работодатель с ID " + employerChatId + " не найден.";

        StringBuilder sb = new StringBuilder();
        sb.append("🏢 <b>ПРОФИЛЬ РАБОТОДАТЕЛЯ</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🆔 <b>ID (System):</b> ").append(e.getId() != null ? e.getId() : "—").append("\n");
        sb.append("📱 <b>Telegram ID:</b> ").append(e.getTelegramId() != null ? e.getTelegramId() : "—").append("\n");
        sb.append("💬 <b>Chat ID:</b> ").append(e.getEmployerChatId() != null ? e.getEmployerChatId() : "—").append("\n");
        sb.append("🏢 <b>Компания:</b> ").append(safe(e.getCompanyName(), "Не указана")).append("\n");
        sb.append("👤 <b>ФИО:</b> ").append(safe(e.getFullName(), "Не указано")).append("\n");
        sb.append("🎂 <b>Возраст:</b> ").append(e.getAge() != null ? e.getAge() : "—").append("\n");
        sb.append("📞 <b>Телефон:</b> ").append(safe(e.getPhoneNumber(), "Не указан")).append("\n");
        sb.append("📄 <b>ИНН:</b> ").append(safe(e.getInn(), "Не указан")).append("\n");
        sb.append("🪪 <b>Паспорт:</b> ").append(safe(e.getPassportSeriesNumber(), "Не указан")).append("\n");

        if (e.getCompanyAddress() != null && !e.getCompanyAddress().isBlank())
            sb.append("📍 <b>Адрес:</b> ").append(e.getCompanyAddress()).append("\n");
        if (e.getCompanyWebsite() != null && !e.getCompanyWebsite().isBlank())
            sb.append("🔗 <b>Сайт:</b> <a href='").append(e.getCompanyWebsite()).append("'>").append(e.getCompanyWebsite()).append("</a>\n");
        if (e.getCompanySize() != null && !e.getCompanySize().isBlank())
            sb.append("👥 <b>Размер:</b> ").append(e.getCompanySize()).append("\n");
        if (e.getCompanyDescription() != null && !e.getCompanyDescription().isBlank())
            sb.append("📝 <b>Описание:</b> ").append(e.getCompanyDescription()).append("\n");

        sb.append("💼 <b>Категория:</b> ").append(safe(e.getCategory(), "Не указана")).append("\n");
        sb.append("🧰 <b>Тип работы:</b> ").append(safe(e.getJobType(), "Не указан")).append("\n");
        sb.append("🌐 <b>Язык:</b> ").append(safe(e.getLanguage(), "Не указан")).append("\n");
        sb.append("⭐ <b>Рейтинг:</b> ").append(e.getRating() != null ? String.format("%.1f", e.getRating()) : "0.0")
                .append(" (Отзывов: ").append(e.getReviewCount() != null ? e.getReviewCount() : 0).append(")\n");
        sb.append("📌 <b>Статус:</b> ").append(safe(e.getStatus(), "—")).append("\n");

        try {
            long cnt = jobVacancyRepository.countByEmployerChatId(e.getEmployerChatId());
            sb.append("💼 <b>Вакансий:</b> ").append(cnt).append("\n");
        } catch (Exception ignored) {}

        sb.append("━━━━━━━━━━━━━━━━━━━━");
        return sb.toString();
    }

    public String getUserInfoByTgId(Long tgId) {
        if (tgId == null) return "⚠️ Неверный ID.";

        Optional<JobSeekerProfile> workerOpt = jobSeekerProfileRepository.findByTelegramId(tgId);
        if (workerOpt.isEmpty()) workerOpt = jobSeekerProfileRepository.findByUserId(tgId);

        Optional<EmployerEntity> employerOpt = employerRepository.findById(tgId);
        if (employerOpt.isEmpty()) {
            try { employerOpt = employerRepository.findByTelegramId(tgId); } catch (Exception ignored) {}
        }
        if (employerOpt.isEmpty()) {
            try { employerOpt = employerRepository.findByEmployerChatId(tgId); } catch (Exception ignored) {}
        }

        if (workerOpt.isEmpty() && employerOpt.isEmpty()) return "❌ Пользователь с ID " + tgId + " не найден.";

        StringBuilder sb = new StringBuilder();
        sb.append("👤 <b>Информация о пользователе</b>\n");
        sb.append("🆔 ID: ").append(tgId).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        workerOpt.ifPresent(w -> {
            sb.append("👷 <b>СОИСКАТЕЛЬ</b>\n\n");
            sb.append("📛 ФИО: ").append(safe(w.getFullName(), "—")).append("\n");
            sb.append("📞 Телефон: ").append(safe(w.getPhoneNumber(), "—")).append("\n");
            sb.append("🪪 Паспорт: ").append(safe(w.getPassportNumber(), "—")).append("\n");
            sb.append("💼 Профессия: ").append(safe(w.getProfession(), "—")).append("\n");
            sb.append("📂 Категория: ").append(safe(w.getCategory(), "—")).append("\n");
            sb.append("⭐ Рейтинг: ").append(w.getRating() != null ? String.format("%.1f", w.getRating()) : "0.0")
                    .append(" (отзывов: ").append(w.getReviewCount() != null ? w.getReviewCount() : 0).append(")\n");
            sb.append("📊 Опыт: ").append(safe(w.getExperience(), "—")).append("\n");
            sb.append("🌐 Язык: ").append(safe(w.getLanguage(), "—")).append("\n");
            sb.append("💰 Баланс: ").append(w.getWalletBalance() != null ? w.getWalletBalance().toPlainString() + " сум" : "0 сум").append("\n");
            sb.append("📌 Статус: ").append(Boolean.TRUE.equals(w.getIsActive()) ? "Активен ✅" : "Заблокирован ❌").append("\n");
            sb.append("\n");
        });

        employerOpt.ifPresent(e -> {
            sb.append("🏢 <b>РАБОТОДАТЕЛЬ</b>\n\n");
            sb.append("🏢 Компания: ").append(safe(e.getCompanyName(), "—")).append("\n");
            sb.append("👤 ФИО: ").append(safe(e.getFullName(), "—")).append("\n");
            sb.append("📞 Телефон: ").append(safe(e.getPhoneNumber(), "—")).append("\n");
            sb.append("📄 ИНН: ").append(safe(e.getInn(), "—")).append("\n");
            sb.append("📌 Статус: ").append(safe(e.getStatus(), "—")).append("\n");
            sb.append("\n");
        });

        return sb.toString();
    }

    // ─────────────────────── PAGINATION ───────────────────────

    @Transactional(readOnly = true)
    public Object[] getWorkersListPageData(int page) {
        List<JobSeekerProfile> workers = jobSeekerProfileRepository.findAll();
        if (workers.isEmpty()) return new Object[]{"👷 Список рабочих пуст.", new ArrayList<Long>(), 1};

        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) workers.size() / pageSize);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int start = page * pageSize;
        int end = Math.min(start + pageSize, workers.size());

        StringBuilder sb = new StringBuilder("👷 Список всех рабочих:\n\n");
        List<Long> idsOnPage = new ArrayList<>();
        for (int i = start; i < end; i++) {
            JobSeekerProfile w = workers.get(i);
            Long displayId = w.getTelegramId() != null ? w.getTelegramId() : w.getUserId();
            sb.append(i + 1).append(". ").append(safe(w.getFullName(), "Без имени"))
                    .append(" | ID: ").append(displayId != null ? displayId : "—")
                    .append(" | 📞 ").append(safe(w.getPhoneNumber(), "—")).append("\n");
            if (displayId != null) idsOnPage.add(displayId);
        }
        return new Object[]{sb.toString(), idsOnPage, totalPages};
    }

    @Transactional(readOnly = true)
    public Object[] getEmployersListPageData(int page) {
        List<EmployerEntity> employers = employerRepository.findAll();
        if (employers.isEmpty()) return new Object[]{"🏢 Список работодателей пуст.", new ArrayList<Long>(), 1};

        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) employers.size() / pageSize);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int start = page * pageSize;
        int end = Math.min(start + pageSize, employers.size());

        StringBuilder sb = new StringBuilder("🏢 Список всех работодателей:\n\n");
        List<Long> idsOnPage = new ArrayList<>();
        for (int i = start; i < end; i++) {
            EmployerEntity e = employers.get(i);
            Long displayId = e.getTelegramId() != null ? e.getTelegramId()
                    : (e.getEmployerChatId() != null ? e.getEmployerChatId() : e.getId());
            sb.append(i + 1).append(". ").append(safe(e.getCompanyName(), "Без названия"))
                    .append(" | Паспорт: ").append(safe(e.getPassportSeriesNumber(), "—"))
                    .append(" | ID: ").append(displayId != null ? displayId : "—")
                    .append(" | 📞 ").append(safe(e.getPhoneNumber(), "—")).append("\n");
            if (displayId != null) idsOnPage.add(displayId);
        }
        return new Object[]{sb.toString(), idsOnPage, totalPages};
    }

    // ─────────────────────── APPLICATIONS / VACANCIES ───────────────────────

    @Transactional(readOnly = true)
    public List<String> getAllApplicationsFormatted() {
        List<String> result = new ArrayList<>();
        try {
            List<JobApplication> apps = jobApplicationRepository.findAll();

            for (JobApplication app : apps) {
                String statusName = app.getStatus() != null ? app.getStatus().name() : "PENDING";
                String statusEmoji;
                switch (statusName) {
                    case "ACCEPTED"  -> statusEmoji = "✅";
                    case "REJECTED"  -> statusEmoji = "❌";
                    case "VIEWED"    -> statusEmoji = "👁";
                    case "INTERVIEW" -> statusEmoji = "📅";
                    case "CANCELLED" -> statusEmoji = "🚫";
                    default          -> statusEmoji = "⏳";
                }

                String seekerName = "—";
                String seekerPhone = "—";
                try {
                    JobSeekerProfile seeker = jobSeekerProfileRepository.findById(app.getJobSeekerId()).orElse(null);
                    if (seeker != null) {
                        seekerName = safe(seeker.getFullName(), "—");
                        seekerPhone = safe(seeker.getPhoneNumber(), "—");
                    }
                } catch (Exception ignored) {}

                String vacancyTitle = "—";
                try {
                    JobVacancy v = jobVacancyRepository.findById(app.getJobId()).orElse(null);
                    if (v != null) vacancyTitle = safe(v.getTitle(), "—");
                } catch (Exception ignored) {}

                String line = String.format(
                        "%s <b>Заявка #%d</b>\n   📌 Вакансия: %s\n   👤 Кандидат: %s (ID: %d)\n   📞 Тел: %s\n   📊 Статус: %s\n   💬 Cover: %s",
                        statusEmoji,
                        app.getId(),
                        vacancyTitle,
                        seekerName,
                        app.getJobSeekerId(),
                        seekerPhone,
                        statusName,
                        safe(app.getCoverLetter(), "—")
                );
                result.add(line);
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка получения заявок: {}", e.getMessage());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<String> getAllVacanciesFormatted() {
        List<String> result = new ArrayList<>();
        try {
            for (JobVacancy v : jobVacancyRepository.findAll()) {
                String statusEmoji = Boolean.TRUE.equals(v.getIsActive()) ? "🟢" : "🔴";
                String line = String.format(
                        "%s <b>%s</b>\n   💰 ЗП: %s\n   🏢 Компания: %s\n   📂 Категория: %s\n   📞 Тел: %s",
                        statusEmoji,
                        safe(v.getTitle(), "Без названия"),
                        safe(v.getSalary(), "—"),
                        safe(v.getCompanyName(), "—"),
                        safe(v.getCategory(), "—"),
                        safe(v.getPhoneNumber(), "—")
                );
                result.add(line);
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка получения вакансий: {}", e.getMessage());
        }
        return result;
    }

    // ─────────────────────── HELPERS ───────────────────────

    private static String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static long safeCount(CountSupplier supplier) {
        try { return supplier.get(); } catch (Exception e) { return 0L; }
    }

    @FunctionalInterface
    private interface CountSupplier { long get() throws Exception; }
}