package uz.imaan.jobplatform.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import uz.imaan.jobplatform.admin.TelegramBotService;
import uz.imaan.jobplatform.admin.mapper.AdminMapper;
import uz.imaan.jobplatform.admin.repository.AdminRepository;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;
import uz.imaan.jobplatform.employer.repository.EmployerRepository;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;

import java.util.List;

@Service
public class AdminServiceImpl extends AdminService {

    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final EmployerRepository employerRepository;

    @Value("${telegram.bot.admin-ids:}")
    private List<Long> adminIds;

    public AdminServiceImpl(AdminRepository adminRepository,
                            AdminMapper adminMapper,
                            JobSeekerProfileRepository jobSeekerProfileRepository,
                            EmployerRepository employerRepository,
                            @Lazy TelegramBotService telegramBotService) {
        super(adminRepository, adminMapper, jobSeekerProfileRepository, employerRepository, telegramBotService);
        // ИНИЦИАЛИЗАЦИЯ ПОЛЕЙ КЛАССА
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.employerRepository = employerRepository;
    }

    @Override
    public boolean isAdmin(Long chatId) {
        if (chatId == null) return false;
        return (adminIds != null && adminIds.contains(chatId)) || super.isAdmin(chatId);
    }

    @Override
    public String getAllWorkersList() {
        List<JobSeekerProfile> workers = jobSeekerProfileRepository.findAll();
        if (workers.isEmpty()) {
            return "👷 Список рабочих пуст.";
        }

        StringBuilder sb = new StringBuilder("👷 *Список всех рабочих (соискателей):*\n\n");
        int index = 1;
        for (JobSeekerProfile worker : workers) {
            // Если в сущности поле Telegram ID называется getTgId() или getTelegramId() — подставьте его
            Long tgId = worker.getUserId() != null ? worker.getUserId() : worker.getId();

            sb.append(index++)
                    .append(". ")
                    .append(worker.getFullName() != null ? worker.getFullName() : "Без имени")
                    .append(" | ID: `")
                    .append(tgId != null ? tgId : "—")
                    .append("` | 📞 ")
                    .append(worker.getPhoneNumber() != null ? worker.getPhoneNumber() : "—")
                    .append("\n");
        }
        return sb.toString();
    }

    @Override
    public String getFormattedEmployersList() {
        List<EmployerEntity> employers = employerRepository.findAll();
        if (employers.isEmpty()) {
            return "🏢 Работодатели не найдены.";
        }

        StringBuilder sb = new StringBuilder("🏢 *Список всех работодателей:*\n\n");
        int count = 1;
        for (EmployerEntity emp : employers) {
            Long tgId = emp.getId() != null ? emp.getId() : emp.getId();

            sb.append(count++).append(". ")
                    .append("Паспорт: ").append(emp.getPassportSeriesNumber() != null ? emp.getPassportSeriesNumber() : "—")
                    .append(" | ID: `").append(tgId != null ? tgId : "—").append("`")
                    .append(" | 📞 ").append(emp.getPhoneNumber() != null ? emp.getPhoneNumber() : "—")
                    .append("\n");
        }
        return sb.toString();
    }

    @Override
    public String getUserInfoByTgId(Long targetTgId) {
        if (targetTgId == null) return "⚠️ Введите корректный ID.";

        JobSeekerProfile worker = jobSeekerProfileRepository.findById(targetTgId).orElse(null);
        EmployerEntity employer = employerRepository.findById(targetTgId).orElse(null);

        if (worker == null && employer == null) {
            return "❌ Пользователь с ID `" + targetTgId + "` не найден в базе данных.";
        }

        StringBuilder info = new StringBuilder("👤 *Информация о пользователе*\n");
        info.append("───────────────────────────\n");
        info.append("🆔 *ID:* `").append(targetTgId).append("`\n\n");

        if (worker != null) {
            info.append("👷 *Роль:* Соискатель (Рабочий)\n");
            info.append("📛 *Имя:* ").append(worker.getFullName() != null ? worker.getFullName() : "Не указано").append("\n");
            info.append("📞 *Телефон:* ").append(worker.getPhoneNumber() != null ? worker.getPhoneNumber() : "Не указан").append("\n\n");
        }

        if (employer != null) {
            info.append("🏢 *Роль:* Работодатель\n");
            info.append("📄 *Паспорт:* ").append(employer.getPassportSeriesNumber() != null ? employer.getPassportSeriesNumber() : "Не указан").append("\n");
            info.append("📞 *Телефон:* ").append(employer.getPhoneNumber() != null ? employer.getPhoneNumber() : "Не указан").append("\n");
        }

        return info.toString();
    }
}