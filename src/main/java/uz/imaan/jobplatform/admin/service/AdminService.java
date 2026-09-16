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
import uz.imaan.jobplatform.employer.repository.EmployerRepository;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final AdminMapper adminMapper;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final EmployerRepository employerRepository;

    @Value("${telegram.bot.admin-ids:}")
    private List<Long> adminChatIds;

    public AdminService(AdminRepository adminRepository,
                        AdminMapper adminMapper,
                        JobSeekerProfileRepository jobSeekerProfileRepository,
                        EmployerRepository employerRepository) {
        this.adminRepository = adminRepository;
        this.adminMapper = adminMapper;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.employerRepository = employerRepository;
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

    // ─────────────────────────── DELETE ───────────────────────────

    @Transactional
    public void deleteAdmin(Long id) {
        if (!adminRepository.existsById(id)) {
            throw new RuntimeException("Admin not found: " + id);
        }
        adminRepository.deleteById(id);
    }

    // ─────────────────────────── AUTH ───────────────────────────

    public boolean isAdmin(Long chatId) {
        if (chatId == null) return false;
        boolean inDb = adminRepository.existsByTelegramId(chatId);
        boolean inConfig = adminChatIds != null && adminChatIds.contains(chatId);
        return inDb || inConfig;
    }

    // ─────────────────────────── STATS ───────────────────────────

    @Transactional(readOnly = true)
    public AdminStatsDto getDetailedStats() {
        return AdminStatsDto.builder()
                .totalAdmins(adminRepository.count())
                .totalWorkers(jobSeekerProfileRepository.count())
                .totalEmployers(employerRepository.count())
                .activeJobs(0L)
                .completedShifts(0L)
                .totalVacancies(0L)
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

    // ─────────────────────────── LISTS ───────────────────────────

    public List<JobSeekerProfile> getAllJobSeekers() {
        return jobSeekerProfileRepository.findAll();
    }

    public List<EmployerEntity> getAllEmployers() {
        return employerRepository.findAll();
    }

    public String getAllWorkersList() {
        List<JobSeekerProfile> workers = jobSeekerProfileRepository.findAll();
        if (workers.isEmpty()) return "👷 Список рабочих пуст.";
        StringBuilder sb = new StringBuilder("👷 *Список всех рабочих:*\n\n");
        int i = 1;
        for (JobSeekerProfile w : workers) {
            Long tgId = w.getTelegramId() != null ? w.getTelegramId() : w.getUserId();
            sb.append(i++).append(". ")
                    .append(safe(w.getFullName(), "Без имени"))
                    .append(" | ID: `").append(tgId != null ? tgId : "—").append("`")
                    .append(" | 📞 ").append(safe(w.getPhoneNumber(), "—"))
                    .append("\n");
        }
        return sb.toString();
    }

    public String getFormattedEmployersList() {
        List<EmployerEntity> employers = employerRepository.findAll();
        if (employers.isEmpty()) return "🏢 Работодатели не найдены.";
        StringBuilder sb = new StringBuilder("🏢 *Список всех работодателей:*\n\n");
        int i = 1;
        for (EmployerEntity e : employers) {
            sb.append(i++).append(". ")
                    .append(safe(e.getCompanyName(), "Без названия"))
                    .append(" | Паспорт: ").append(safe(e.getPassportSeriesNumber(), "—"))
                    .append(" | ID: `").append(e.getTelegramId() != null ? e.getTelegramId() : "—").append("`")
                    .append(" | 📞 ").append(safe(e.getPhoneNumber(), "—"))
                    .append("\n");
        }
        return sb.toString();
    }

    public String getWorkerDetails(Long userId) {
        JobSeekerProfile w = jobSeekerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Worker not found: " + userId));
        return String.format("""
                👤 *Профиль исполнителя* (ID: %d)
                📛 *ФИО:* %s
                📞 *Телефон:* %s
                📌 *Статус:* %s
                🔒 *Заблокирован:* %s
                💬 *Причина:* %s
                📊 Профессия: %s | Опыт: %s | Рейтинг: %.1f ⭐""",
                userId,
                safe(w.getFullName(), "Не указано"),
                safe(w.getPhoneNumber(), "Не указан"),
                Boolean.TRUE.equals(w.getIsActive()) ? "Активен ✅" : "Заблокирован ❌",
                Boolean.TRUE.equals(w.getIsActive()) ? "Нет" : "Да",
                safe(w.getBlockReason(), "Нет"),
                safe(w.getProfession(), "Не указана"),
                safe(w.getExperience(), "Не указан"),
                w.getRating() != null ? w.getRating() : 0.0);
    }

    public String getUserInfoByTgId(Long tgId) {
        Optional<JobSeekerProfile> worker = jobSeekerProfileRepository.findByTelegramId(tgId);
        Optional<EmployerEntity> employer = employerRepository.findById(tgId);

        if (worker.isEmpty() && employer.isEmpty()) {
            return "❌ Пользователь с ID `" + tgId + "` не найден.";
        }

        StringBuilder sb = new StringBuilder("👤 *Информация о пользователе*\n🆔 `" + tgId + "`\n\n");
        worker.ifPresent(w -> sb.append("👷 *Соискатель*\n")
                .append("📛 ").append(safe(w.getFullName(), "—")).append("\n")
                .append("📞 ").append(safe(w.getPhoneNumber(), "—")).append("\n\n"));
        employer.ifPresent(e -> sb.append("🏢 *Работодатель*\n")
                .append("📄 ").append(safe(e.getPassportSeriesNumber(), "—")).append("\n")
                .append("📞 ").append(safe(e.getPhoneNumber(), "—")).append("\n"));
        return sb.toString();
    }

    private static String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}