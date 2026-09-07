package uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;

import java.util.Optional;

public interface PaginationService {

    // Kategoriya bo'yicha qidirishni boshlash
    void handleCategorySearch(Long chatId, String categoryKey, Optional<JobSeekerProfile> profileOpt);

    // Vakansiyalarni sahifalab ko'rsatish
    SendMessage handleVacancyPagination(Long chatId, String categoryKey, int page,
                                        Optional<JobSeekerProfile> profileOpt);

}
