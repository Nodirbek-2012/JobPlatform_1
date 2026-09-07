package uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;

import java.util.Optional;

public interface JobSeekerHandler {

    SendMessage handleJobSeeker(Message message);

    void updateLanguage(Long chatId, String languageCode);

    SendMessage showProfile(Long chatId, Optional<JobSeekerProfile> profileOpt);

    String getProfileInfo(JobSeekerProfile profile, Optional<JobSeekerProfile> profileOpt);

    SendMessage handleProfileMenu(Long chatId, String text, Optional<JobSeekerProfile> profileOpt);

    SendMessage showWallet(Long chatId, Optional<JobSeekerProfile> profileOpt);

    SendMessage handleWalletMenu(Long chatId, String text, Optional<JobSeekerProfile> profileOpt);

    SendMessage handleDeleteCard(Long chatId, Optional<JobSeekerProfile> profileOpt);

    SendMessage handleShowApplications(Long chatId, Optional<JobSeekerProfile> profileOpt);

    SendMessage handleSettingsMenu(Long chatId, String text, Optional<JobSeekerProfile> profileOpt);

    SendMessage handleLanguageSelection(Long chatId, String text, Optional<JobSeekerProfile> profileOpt);

    SendMessage handleCardNumber(Long chatId, String text, Optional<JobSeekerProfile> profileOpt);

    SendMessage handleCardHolder(Long chatId, String text, Optional<JobSeekerProfile> profileOpt);

    SendMessage showPortfolio(Long chatId, Optional<JobSeekerProfile> profileOpt);

    SendMessage handlePortfolioInput(Long chatId, String text, Optional<JobSeekerProfile> profileOpt);

    String getProfileInfo(Long chatId, Optional<JobSeekerProfile> profileOpt);

    SendMessage handleCallback(CallbackQuery callbackQuery);

    // ✅ YANGI METOD
    SendMessage handleVacancyPagination(Long chatId, String categoryKey, int page,
                                        Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboard getCategoryInlineKeyboard(Optional<Object> empty);
}