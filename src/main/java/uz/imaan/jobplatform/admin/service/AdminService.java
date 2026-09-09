package uz.imaan.jobplatform.admin.service;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import uz.imaan.jobplatform.admin.TelegramBotService;
import uz.imaan.jobplatform.admin.dto.AdminDtoTwo;
import uz.imaan.jobplatform.admin.entity.Admin;
import uz.imaan.jobplatform.admin.mapper.AdminMapper;
import uz.imaan.jobplatform.admin.repository.AdminRepository;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;
import uz.imaan.jobplatform.employer.repository.EmployerRepository;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public abstract class AdminService {

    private final AdminRepository adminRepository;
    private final AdminMapper adminMapper;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final EmployerRepository employerRepository;
    private final TelegramBotService telegramBotService;

    private final List<Long> adminChatIds = List.of(
            6326035618L,
            7584728450L,
            6500351879L
    );

    @Autowired
    public AdminService(AdminRepository adminRepository,
                        AdminMapper adminMapper,
                        JobSeekerProfileRepository jobSeekerProfileRepository,
                        EmployerRepository employerRepository,
                        @Lazy TelegramBotService telegramBotService) {
        this.adminRepository = adminRepository;
        this.adminMapper = adminMapper;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.employerRepository = employerRepository;
        this.telegramBotService = telegramBotService;
    }

    public List<AdminDtoTwo> getAllAdmins() {
        return adminRepository.findAll()
                .stream()
                .map(adminMapper::toDTO)
                .collect(Collectors.toList());
    }

    public AdminDtoTwo getAdminById(Long id) {
        Admin entity = adminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Admin not found with id: " + id));
        return adminMapper.toDTO(entity);
    }

    public AdminDtoTwo createAdmin(AdminDtoTwo adminDTO) {
        Admin entity = adminMapper.toEntity(adminDTO);
        Admin savedEntity = adminRepository.save(entity);
        return adminMapper.toDTO(savedEntity);
    }

    public boolean isAdmin(Long chatId) {
        if (chatId == null) return false;

        boolean existsInDb = adminRepository.existsByTelegramId(chatId);
        boolean existsInList = (adminChatIds != null && adminChatIds.contains(chatId));

        System.out.println("DEBUG: Проверка прав для chatId = " + chatId);
        System.out.println("DEBUG: Найдено в adminIds: " + existsInList);
        System.out.println("DEBUG: Найдено в БД (existsByTelegramId): " + existsInDb);

        return existsInList || existsInDb;
    }

    public void deleteAdmin(Long id) {
        adminRepository.deleteById(id);
    }


    public AdminDtoTwo getStats() {
        long admins = adminRepository.count();
        long workers = jobSeekerProfileRepository.findAll().size();
        long employers = employerRepository.findAll().size();

        // 🔍 ПРОВЕРКА В КОНСОЛИ IDE
        System.out.println(">>> DEBUG ADM: " + admins);
        System.out.println(">>> DEBUG WRK: " + workers);
        System.out.println(">>> DEBUG EMP: " + employers);

        return AdminDtoTwo.builder()
                .totalAdmins(admins)
                .totalWorkers(workers)
                .totalEmployers(employers)
                .build();
    }

    @Transactional
    public void blockUser(@NotNull AdminDtoTwo blockDTO) {
        JobSeekerProfile worker = jobSeekerProfileRepository.findByUserId(blockDTO.userId())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден с ID: " + blockDTO.userId()));

        worker.setIsActive(false);
        worker.setBlockReason(blockDTO.reason());

        jobSeekerProfileRepository.save(worker);

        System.out.println("Пользователь с ID " + blockDTO.userId() + " заблокирован по причине: " + blockDTO.reason());
    }

    @Transactional
    public void unblockUser(Long userId) {
        JobSeekerProfile worker = jobSeekerProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден с ID: " + userId));

        worker.setIsActive(true);
        worker.setBlockReason(null);

        jobSeekerProfileRepository.save(worker);

        System.out.println("Пользователь с ID " + userId + " успешно разблокирован.");
    }

    @Transactional
    public String getWorkerDetails(Long userId) {
        JobSeekerProfile worker = jobSeekerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Исполнитель не найден с ID: " + userId));

        return String.format(
                "👤 **Профиль исполнителя** (ID: %d)\n" +
                        "📛 **ФИО:** %s\n" +
                        "📞 **Телефон:** %s\n\n" +
                        "📌 **Статус:** %s\n" +
                        "🔒 **Заблокирован:** %s\n" +
                        "💬 **Причина блокировки:** %s\n\n" +
                        "📊 **Характеристики:**\n" +
                        "• Профессия: %s\n" +
                        "• Опыт: %s\n" +
                        "• Рейтинг: %.1f ⭐",
                userId,
                worker.getFullName() != null ? worker.getFullName() : "Не указано",
                worker.getPhoneNumber() != null ? worker.getPhoneNumber() : "Не указан",
                Boolean.TRUE.equals(worker.getIsActive()) ? "Активен ✅" : "Заблокирован ❌",
                Boolean.TRUE.equals(worker.getIsActive()) ? "Нет" : "Да",
                worker.getBlockReason() != null ? worker.getBlockReason() : "Нет",
                worker.getProfession() != null ? worker.getProfession() : "Не указана",
                worker.getExperience() != null ? worker.getExperience() : "Не указан",
                worker.getRating() != null ? worker.getRating() : 0.0
        );
    }

    @PostConstruct
    public void init() {
        long myRealId = 6326035618L;

        if (!adminRepository.existsByTelegramId(myRealId)) {
            Admin newAdmin = new Admin();
            newAdmin.setTelegramId(myRealId);
            newAdmin.setName("Главный Админ");
            newAdmin.setRole("ADMIN");

            adminRepository.save(newAdmin);
            System.out.println("✅ АДМИН УСПЕШНО ДОБАВЛЕН В БАЗУ ДАННЫХ!");
        } else {
            System.out.println("ℹ️ Админ уже есть в базе.");
        }
    }

    public void notifyAllAdmins(String message) {
        for (Long chatId : adminChatIds) {
            telegramBotService.sendMessage(chatId, message);
        }
    }

    public List<JobSeekerProfile> getAllJobSeekers() {
        return jobSeekerProfileRepository.findAll();
    }

    public List<EmployerEntity> getAllEmployers() {
        return employerRepository.findAll();
    }

    public String getFormattedJobSeekersList() {
        List<JobSeekerProfile> list = getAllJobSeekers();
        if (list.isEmpty()) {
            return "👷‍♂️ **Список рабочих пуст.**";
        }

        StringBuilder sb = new StringBuilder("👷‍♂️ **Список всех рабочих (соискателей):**\n\n");
        for (int i = 0; i < list.size(); i++) {
            JobSeekerProfile seeker = list.get(i);
            sb.append(i + 1).append(". ")
                    .append(seeker.getFullName() != null ? seeker.getFullName() : "Без имени")
                    .append(" | 📞 ").append(seeker.getPhoneNumber() != null ? seeker.getPhoneNumber() : "Нет номера")
                    .append("\n");
        }
        return sb.toString();
    }

    public abstract String getAllWorkersList();

    public String getFormattedEmployersList() {
        List<EmployerEntity> list = getAllEmployers();
        if (list.isEmpty()) {
            return "💼 **Список работодателей пуст.**";
        }

        StringBuilder sb = new StringBuilder("💼 **Список всех работодателей:**\n\n");
        for (int i = 0; i < list.size(); i++) {
            EmployerEntity emp = list.get(i);
            sb.append(i + 1).append(". ")
                    .append(emp.getCompanyName() != null ? emp.getCompanyName() : "Без названия")
                    .append(" | 📞 ").append(emp.getPhoneNumber() != null ? emp.getPhoneNumber() : "Нет номера")
                    .append("\n");
        }
        return sb.toString();
    }

    public AdminDtoTwo getDetailedStats() {
        long totalAdmins = (adminChatIds != null ? adminChatIds.size() : 0) + adminRepository.count();

        // Считаем рабочих и работодателей
        long totalWorkers = jobSeekerProfileRepository.findAll().size();
        long totalEmployers = employerRepository.findAll().size();

        // Считаем дополнительные параметры (если есть нужные репозитории)
        long activeJobsCount = 0L; // Например: jobRepository.countByStatus("ACTIVE");
        long completedShiftsCount = 0L; // Например: shiftRepository.countByStatus("COMPLETED");

        return AdminDtoTwo.builder()
                .totalAdmins(totalAdmins)
                .totalEmployers(totalEmployers)
                .totalWorkers(totalWorkers)
                .activeJobs(activeJobsCount)
                .completedShifts(completedShiftsCount)
                .build();
    }


    public abstract String getUserInfoByTgId(Long targetTgId);
}