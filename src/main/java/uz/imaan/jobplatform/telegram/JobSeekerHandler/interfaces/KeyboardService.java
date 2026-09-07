package uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;

import java.util.Optional;

public interface KeyboardService {

    // ===== INLINE KEYBOARD =====
    InlineKeyboardMarkup getCategoryInlineKeyboard(Optional<JobSeekerProfile> profileOpt);

    // ===== REPLY KEYBOARD =====
    ReplyKeyboardMarkup getMainMenuKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getJobTypeKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getRegistrationCategoryKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getSearchCategoryKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getProfileKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getWalletKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getWalletKeyboard(Optional<JobSeekerProfile> profileOpt, boolean hasCard);

    ReplyKeyboardMarkup getSettingsKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getLanguageKeyboard();

    ReplyKeyboardMarkup getActiveJobsKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getJobActionKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getCancelKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getSubBackKeyboard(Optional<JobSeekerProfile> profileOpt);

    ReplyKeyboardMarkup getPhoneKeyboard();

    // ===== MATNLAR =====
    String getMainMenuText(Optional<JobSeekerProfile> profileOpt);

    String getText(Optional<JobSeekerProfile> profileOpt, String ru, String uz, String en);

}
