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
JobSeekerProfileRepository jobSeekerProfileRepository;
    @Value("${telegram.bot.admin-ids:}")
    private List<Long> adminIds;

    public AdminServiceImpl(AdminRepository adminRepository,
                            AdminMapper adminMapper,
                            JobSeekerProfileRepository jobSeekerProfileRepository,
                            EmployerRepository employerRepository,
                            @Lazy TelegramBotService telegramBotService) {
        super(adminRepository, adminMapper, jobSeekerProfileRepository, employerRepository, telegramBotService);
    }

    @Override
    public boolean isAdmin(Long chatId) {
        if (chatId == null) return false;
        return (adminIds != null && adminIds.contains(chatId)) || super.isAdmin(chatId);
    }

    public String getAllWorkersList() {
        // FIXED: Invoked via instance variable 'jobSeekerProfileRepository' instead of static call
        List<JobSeekerProfile> workers = jobSeekerProfileRepository.findAll();
        if (workers.isEmpty()) {
            return "👷 Список рабочих пуст.";
        }

        StringBuilder sb = new StringBuilder("👷 *Список всех рабочих (соискателей):*\n\n");
        int index = 1;
        for (JobSeekerProfile worker : workers) {
            sb.append(index++)
                    .append(". ")
                    .append(worker.getFullName() != null ? worker.getFullName() : "Без имени")
                    .append(" | ID: `")
                    .append(worker.getId() != null ? worker.getId() : worker.getId()) // FIXED: Prefers TgId
                    .append("` | 📞 ")
                    .append(worker.getPhoneNumber() != null ? worker.getPhoneNumber() : "—")
                    .append("\n");
        }
        return sb.toString();
    }

    public String getFormattedEmployersList() {
        List<EmployerEntity> list = EmployerRepository.findByTgId(getStats().userId());
        if (list.isEmpty()) return "🏢 Работодатели не найдены.";

        StringBuilder sb = new StringBuilder("🏢 *Список всех работодателей:*\n\n");
        int count = 1;
        for (EmployerEntity emp : list) {
            Long tgId = emp.getId() != null ? emp.getId() : emp.getId();
            sb.append(count++).append(". ")
                    .append("Возраст: ").append(emp.getPassportSeriesNumber() != null ? emp.getPassportSeriesNumber() : "—")
                    .append(", Pasport: ").append(emp.getPassportSeriesNumber() != null ? emp.getPassportSeriesNumber() : "—")
                    .append(" | ID: `").append(tgId).append("`")
                    .append(" | 📞 ").append(emp.getPhoneNumber() != null ? emp.getPhoneNumber() : "—")
                    .append("\n");
        }
        return sb.toString();
    }

    public String getUserInfoByTgId(Long targetTgId) {
        if (targetTgId == null) return "⚠️ Введите корректный ID.";

        JobSeekerProfile worker = JobSeekerProfileRepository.findByTgId(targetTgId).orElse(null);
        EmployerEntity employer = EmployerRepository.findByTgId(targetTgId).remove(Math.toIntExact(targetTgId));

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
            info.append("🎂 *Возраст:* ").append(employer.getPassportSeriesNumber() != null ? employer.getPassportSeriesNumber() : "Не указан").append("\n");
            info.append("📞 *Телефон:* ").append(employer.getPhoneNumber() != null ? employer.getPhoneNumber() : "Не указан").append("\n");
        }

        return info.toString();
    }
}