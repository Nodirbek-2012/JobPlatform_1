package uz.imaan.jobplatform.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import uz.imaan.jobplatform.admin.TelegramBotService;
import uz.imaan.jobplatform.admin.entity.Admin;
import uz.imaan.jobplatform.admin.mapper.AdminMapper;
import uz.imaan.jobplatform.admin.repository.AdminRepository;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;
import uz.imaan.jobplatform.employer.repository.EmployerRepository;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AdminServiceImpl extends AdminService {

    private static final String WORKERS_LIST_EMPTY = "👷 Список рабочих пуст.";
    private static final String EMPLOYERS_LIST_EMPTY = "🏢 Работодатели не найдены.";
    private static final String USER_NOT_FOUND = "❌ Пользователь с ID `%d` не найден в базе данных.";
    private static final String INVALID_ID = "⚠️ Введите корректный ID.";

    private static final String BOLD = "*";
    private static final String CODE = "`";
    private static final String SEPARATOR = "───────────────────────────\n";
    private static final String DEFAULT_NAME = "Без имени";
    private static final String DEFAULT_PHONE = "—";
    private static final String DEFAULT_PASSPORT = "—";
    private static final String NOT_SPECIFIED = "Не указано";
    private static final String NOT_SPECIFIED_PHONE = "Не указан";

    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final EmployerRepository employerRepository;
    private final AdminRepository adminRepository;

    @Value("${telegram.bot.admin-ids:}")
    private List<Long> adminIds;

    public AdminServiceImpl(AdminRepository adminRepository,
                            AdminMapper adminMapper,
                            JobSeekerProfileRepository jobSeekerProfileRepository,
                            EmployerRepository employerRepository,
                            @Lazy TelegramBotService telegramBotService, AdminRepository adminRepository1) {
        super(adminRepository, adminMapper, jobSeekerProfileRepository, employerRepository, telegramBotService);
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.employerRepository = employerRepository;
        this.adminRepository = adminRepository1;
    }

    @Override
    public boolean isAdmin(Long chatId) {
        if (chatId == null) {
            log.debug("ChatId is null, returning false");
            return false;
        }

        boolean isAdminInProperties = Optional.ofNullable(adminIds)
                .orElse(Collections.emptyList())
                .contains(chatId);

        boolean isAdminInDb = super.isAdmin(chatId);

        log.debug("Checking admin status for chatId: {}, in properties: {}, in DB: {}",
                chatId, isAdminInProperties, isAdminInDb);

        return isAdminInProperties || isAdminInDb;
    }

    @Override
    public String getAllWorkersList() {
        log.debug("Fetching all workers list");

        List<JobSeekerProfile> workers = jobSeekerProfileRepository.findAll();

        if (workers.isEmpty()) {
            log.debug("No workers found in database");
            return WORKERS_LIST_EMPTY;
        }

        log.info("Found {} workers in database", workers.size());

        StringBuilder sb = new StringBuilder("👷 ").append(BOLD).append("Список всех рабочих (соискателей):")
                .append(BOLD).append("\n\n");

        int index = 1;
        for (JobSeekerProfile worker : workers) {
            Long tgId = getTelegramId(worker);
            String fullName = getWorkerFullName(worker);
            String phoneNumber = getWorkerPhoneNumber(worker);

            sb.append(index++)
                    .append(". ")
                    .append(fullName)
                    .append(" | ID: ").append(CODE).append(tgId != null ? tgId : "—").append(CODE)
                    .append(" | 📞 ")
                    .append(phoneNumber)
                    .append("\n");
        }

        return sb.toString();
    }

    @Override
    public String getFormattedEmployersList() {
        log.debug("Fetching all employers list");

        List<EmployerEntity> employers = employerRepository.findAll();

        if (employers.isEmpty()) {
            log.debug("No employers found in database");
            return EMPLOYERS_LIST_EMPTY;
        }

        log.info("Found {} employers in database", employers.size());

        StringBuilder sb = new StringBuilder("🏢 ").append(BOLD).append("Список всех работодателей:")
                .append(BOLD).append("\n\n");

        int count = 1;
        for (EmployerEntity employer : employers) {
            // Получаем ID разными способами
            Long displayId = getEmployerDisplayId(employer);
            String passport = getEmployerPassport(employer);
            String phoneNumber = getEmployerPhoneNumber(employer);
            String companyName = getEmployerCompanyName(employer);

            sb.append(count++).append(". ")
                    .append("Компания: ").append(companyName)
                    .append(" | Паспорт: ").append(passport)
                    .append(" | ID: ").append(CODE).append(displayId != null ? displayId : "—").append(CODE)
                    .append(" | 📞 ").append(phoneNumber)
                    .append("\n");
        }

        return sb.toString();
    }

    private Long getEmployerDisplayId(EmployerEntity employer) {
        // Сначала пробуем получить telegramId
        Long tgId = employer.getTelegramId();

        // Если telegramId есть и это похоже на реальный ID (> 1000000000), возвращаем его
        if (tgId != null && tgId > 1000000000L) {
            return tgId;
        }

        // Если telegramId нет или это ID записи (маленькое число), пробуем найти через телефон
        if (employer.getPhoneNumber() != null && !employer.getPhoneNumber().isEmpty()) {
            // Ищем в таблице JobSeekerProfile по телефону
            Optional<JobSeekerProfile> worker = jobSeekerProfileRepository
                    .findByPhoneNumber(employer.getPhoneNumber());
            if (worker.isPresent()) {
                Long workerTgId = worker.get().getTelegramId();
                if (workerTgId != null && workerTgId > 1000000000L) {
                    log.debug("Found employer telegramId through phone number: {}", workerTgId);
                    return workerTgId;
                }
            }

            // Ищем в таблице Admin по телефону
            Optional<Admin> admin = adminRepository.findByTelegramId(employer.getTelegramId());
            if (admin.isPresent()) {
                Long adminTgId = admin.get().getTelegramId();
                if (adminTgId != null && adminTgId > 1000000000L) {
                    log.debug("Found employer telegramId through admin phone: {}", adminTgId);
                    return adminTgId;
                }
            }
        }

        // Если ничего не нашли, возвращаем null (будет отображаться "—")
        log.warn("Could not find valid telegramId for employer: {}", employer.getCompanyName());
        return null;
    }

    private Long getEmployerTelegramId(EmployerEntity employer) {
        // Пытаемся получить Telegram ID
        Long tgId = employer.getTelegramId();

        // Если telegramId не установлен, пробуем использовать userId
        if (tgId == null || tgId == 0L) {
            Long userId = employer.getTelegramId();
            if (userId != null && userId > 0) {
                log.debug("Employer {} has no telegramId, using userId: {}",
                        employer.getCompanyName(), userId);
                return userId;
            }
        }

        // Если все равно нет, проверяем, может быть ID хранится в другом поле
        // Например, в chatId или другом поле
        if (tgId == null || tgId == 0L) {
            // Попытка получить из другого источника
            Long chatId = employer.getTelegramId(); // Если есть такое поле
            if (chatId != null && chatId > 0) {
                return chatId;
            }
        }

        return tgId;
    }

    private String getEmployerCompanyName(EmployerEntity employer) {
        return Optional.ofNullable(employer.getCompanyName())
                .filter(name -> !name.trim().isEmpty())
                .orElse("Без названия");
    }



    @Override
    public String getUserInfoByTgId(Long targetTgId) {
        log.debug("Fetching user info for telegram ID: {}", targetTgId);

        if (targetTgId == null) {
            log.warn("Attempted to get user info with null ID");
            return INVALID_ID;
        }

        Optional<JobSeekerProfile> workerOpt = jobSeekerProfileRepository.findByTelegramId(targetTgId);
        Optional<EmployerEntity> employerOpt = employerRepository.findById(targetTgId);

        if (workerOpt.isEmpty() && employerOpt.isEmpty()) {
            log.info("User with ID {} not found in database", targetTgId);
            return String.format(USER_NOT_FOUND, targetTgId);
        }

        log.debug("Found user info for ID: {}, worker: {}, employer: {}",
                targetTgId, workerOpt.isPresent(), employerOpt.isPresent());

        StringBuilder info = new StringBuilder("👤 ").append(BOLD).append("Информация о пользователе")
                .append(BOLD).append("\n");
        info.append(SEPARATOR);
        info.append("🆔 ").append(BOLD).append("ID:").append(BOLD).append(" ").append(CODE)
                .append(targetTgId).append(CODE).append("\n\n");

        workerOpt.ifPresent(worker -> {
            info.append("👷 ").append(BOLD).append("Роль:").append(BOLD)
                    .append(" Соискатель (Рабочий)\n");
            info.append("📛 ").append(BOLD).append("Имя:").append(BOLD).append(" ")
                    .append(Optional.ofNullable(worker.getFullName()).orElse(NOT_SPECIFIED))
                    .append("\n");
            info.append("📞 ").append(BOLD).append("Телефон:").append(BOLD).append(" ")
                    .append(Optional.ofNullable(worker.getPhoneNumber()).orElse(NOT_SPECIFIED_PHONE))
                    .append("\n\n");
        });

        employerOpt.ifPresent(employer -> {
            info.append("🏢 ").append(BOLD).append("Роль:").append(BOLD)
                    .append(" Работодатель\n");
            info.append("📄 ").append(BOLD).append("Паспорт:").append(BOLD).append(" ")
                    .append(Optional.ofNullable(employer.getPassportSeriesNumber()).orElse(NOT_SPECIFIED))
                    .append("\n");
            info.append("📞 ").append(BOLD).append("Телефон:").append(BOLD).append(" ")
                    .append(Optional.ofNullable(employer.getPhoneNumber()).orElse(NOT_SPECIFIED_PHONE))
                    .append("\n");
        });

        return info.toString();
    }

    // Private helper methods for better readability and maintainability

    private Long getTelegramId(JobSeekerProfile worker) {
        Long tgId = worker.getTelegramId();
        if (tgId == null) {
            Long userId = worker.getUserId();
            if (userId != null) {
                log.debug("Worker {} has no telegramId, using userId: {}",
                        worker.getFullName(), userId);
            }
            return userId;
        }
        return tgId;
    }

    private String getWorkerFullName(JobSeekerProfile worker) {
        return Optional.ofNullable(worker.getFullName())
                .filter(name -> !name.trim().isEmpty())
                .orElse(DEFAULT_NAME);
    }

    private String getWorkerPhoneNumber(JobSeekerProfile worker) {
        return Optional.ofNullable(worker.getPhoneNumber())
                .filter(phone -> !phone.trim().isEmpty())
                .orElse(DEFAULT_PHONE);
    }

    private String getEmployerPassport(EmployerEntity employer) {
        return Optional.ofNullable(employer.getPassportSeriesNumber())
                .filter(passport -> !passport.trim().isEmpty())
                .orElse(DEFAULT_PASSPORT);
    }

    private String getEmployerPhoneNumber(EmployerEntity employer) {
        return Optional.ofNullable(employer.getPhoneNumber())
                .filter(phone -> !phone.trim().isEmpty())
                .orElse(DEFAULT_PHONE);
    }

   }