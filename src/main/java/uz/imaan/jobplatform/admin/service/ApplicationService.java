package uz.imaan.jobplatform.admin.service;

import org.springframework.stereotype.Service;
import uz.imaan.jobplatform.admin.TelegramBotService;
import uz.imaan.jobplatform.jobseeker.repository.JobApplicationRepository;

@Service
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final TelegramBotService adminBotService;

    public ApplicationService(JobApplicationRepository applicationRepository,
                              TelegramBotService adminBotService) {
        this.applicationRepository = applicationRepository;
        this.adminBotService = adminBotService;
    }

    /*public void createApplication(JobSeekerProfile jobSeeker, JobVacancy vacancy) {
        // 1. Формируем красивый текст уведомления
        String text = String.format(
                "🔔 **Новая заявка на вакансию!**\n\n" +
                        "👤 **Соискатель:** %s\n" +
                        "📌 **Вакансия:** %s\n" +
                        "🆔 **ID Работодателя:** %s",
                jobSeeker.getFullName(),
                vacancy.getTitle(),
                vacancy.getEmployerChatId()
        );

        // 2. Отправляем уведомление админу в Telegram
        TelegramBotService.notifyAdmin(text);
    }*/
}