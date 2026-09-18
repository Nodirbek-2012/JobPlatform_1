package uz.imaan.jobplatform.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.imaan.jobplatform.employer.job.JobStore;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;
import uz.imaan.jobplatform.jobseeker.dto.FavoriteVacancyDto;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerReview;
import uz.imaan.jobplatform.jobseeker.repository.JobApplicationRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerReviewRepository;
import uz.imaan.jobplatform.jobseeker.service.interfaces.FavoriteVacancyService;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.JobSeekerState;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.JobSeekerHandler;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.KeyboardService;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.StateManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramCallbackService {

    private final JobSeekerHandler jobSeekerHandler;
    private final EmployerHandler employerHandler;
    private final JobStore jobStore;
    private final JobVacancyRepository jobVacancyRepository;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobSeekerReviewRepository jobSeekerReviewRepository;
    private final FavoriteVacancyService favoriteVacancyService;
    private final KeyboardService keyboardService;
    private final StateManager stateManager;

    public void processCallback(CallbackQuery callbackQuery, Telegram bot) {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        log.info("📩 Callback: chatId={}, data={}", chatId, callbackData);

        try {
            // ============================================
            // 0. ISH BERUVCHI CALLBACKLARI (BARCHASI)
            // ============================================
            if (callbackData != null && (
                    callbackData.startsWith("emp_cat_") ||
                            callbackData.startsWith("emp_app_status_") ||
                            callbackData.startsWith("vac_edit_") ||
                            callbackData.startsWith("vac_delete_") ||
                            callbackData.startsWith("vac_toggle_") ||
                            callbackData.startsWith("vac_filled_")
            )) {
                SendMessage response = employerHandler.handleCallback(callbackQuery);
                if (response != null) bot.executeMessage(response);
                return;
            }

            // Bo'sh tugmalar
            if ("noop".equals(callbackData) || "ignore".equals(callbackData)) {
                bot.sendAnswerCallback(callbackQuery.getId(), null, false);
                return;
            }

            // 1. Saralanganlar (Toggle)
            if (callbackData.startsWith("fav_toggle_")) {
                handleFavoriteToggle(callbackQuery, chatId, messageId, bot);
                return;
            }

            // 2. Saralanganlardan o'chirish
            if (callbackData.startsWith("fav_remove_")) {
                handleFavoriteRemove(callbackQuery, chatId, messageId, bot);
                return;
            }

            // 3. Saralanganlar sahifasi
            if (callbackData.startsWith("fav_page_")) {
                bot.sendAnswerCallback(callbackQuery.getId(), null, false);
                int targetPage = Integer.parseInt(callbackData.replace("fav_page_", ""));
                showFavoritesPage(chatId, targetPage, messageId, bot);
                return;
            }

            // 4. Vakansiyani batafsil ko'rish
            if (callbackData.startsWith("view_vac_")) {
                handleViewVacancy(callbackQuery, chatId, messageId, bot);
                return;
            }

            // 5. Vakansiyalarni sahifalash
            if (callbackData.startsWith("vacancy_page_")) {
                handleVacancyPagination(callbackQuery, chatId, messageId, bot);
                return;
            }

            // 6. Kategoriya tanlash
            if (callbackData.startsWith("category_")) {
                handleCategoryClick(callbackQuery, chatId, bot);
                return;
            }

            // 7. Kategoriyalarga qaytish
            if ("back_to_categories".equals(callbackData)) {
                bot.sendAnswerCallback(callbackQuery.getId(), null, false);
                SendMessage response = new SendMessage(chatId.toString(), "📂 <b>Kategoriyani tanlang:</b>");
                response.setParseMode("HTML");
                response.setReplyMarkup(jobSeekerHandler.getCategoryInlineKeyboard(Optional.empty()));
                bot.executeMessage(response);
                return;
            }

            // 8. Ariza topshirish (JobSeeker)
            if (callbackData.startsWith("apply_")) {
                handleApply(callbackQuery, chatId, bot);
                return;
            }

            // 9. Eski app_status_ (eski bildirishnoma)
            if (callbackData.startsWith("app_status_")) {
                handleApplicationStatus(callbackQuery, chatId, messageId, bot);
                return;
            }

            // 10. Reyting
            if (callbackData.startsWith("rate_")) {
                handleRating(callbackQuery, chatId, messageId, bot);
                return;
            }

            // 11. Til
            if (callbackData.startsWith("lang_")) {
                handleLanguageSelection(callbackQuery, chatId, bot);
                return;
            }

        } catch (Exception e) {
            log.error("❌ Callback xatolik: {}", e.getMessage(), e);
        }
    }

    // ============================================
    // HANDLER METODLAR
    // ============================================

    private void handleCategoryClick(CallbackQuery callbackQuery, Long chatId, Telegram bot) {
        bot.sendAnswerCallback(callbackQuery.getId(), null, false);
        String categoryKey = callbackQuery.getData().replace("category_", "");
        Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
        JobSeekerState state = stateManager.getState(chatId);

        if (state == JobSeekerState.WAITING_FOR_CATEGORY) {
            String categoryName = getCategoryNameByKey(categoryKey);
            stateManager.putData(chatId, "category", categoryName);
            stateManager.putData(chatId, "profession", categoryName);
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_EXPERIENCE);

            String expPrompt = switch (profileOpt.map(JobSeekerProfile::getLanguage).orElse("uz")) {
                case "ru" -> "📝 <b>Расскажите о своем опыте работы:</b>\n\nСколько времени вы работаете?\n💡 <i>Пример:</i> <code>3 года Java разработчиком</code>";
                case "en" -> "📝 <b>Tell us about your work experience:</b>\n\nHow long have you been working?\n💡 <i>Example:</i> <code>3 years Java developer</code>";
                default -> "📝 <b>Ish tajribangiz haqida ma'lumot bering:</b>\n\nQancha vaqtdan beri ishlayapsiz?\n💡 <i>Misol:</i> <code>3 yil Java dasturchi</code>";
            };

            SendMessage expMsg = new SendMessage(chatId.toString(), expPrompt);
            expMsg.setParseMode("HTML");
            expMsg.setReplyMarkup(keyboardService.getCancelKeyboard(profileOpt));
            bot.executeMessage(expMsg);
            return;
        }

        // ⭐️ KASB YANGILANGANDA ISHLAYDIGAN QISM:
        if (state == JobSeekerState.WAITING_FOR_PROFESSION) {
            String categoryName = getCategoryNameByKey(categoryKey);
            JobSeekerProfile profile = null;
            if (profileOpt.isPresent()) {
                profile = profileOpt.get();
                profile.setProfession(categoryName);
                profile.setCategory(categoryName);
                jobSeekerProfileRepository.save(profile);
            }
            stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);

            // 1. «Kasb yangilandi» xabarini yuborish
            SendMessage okMsg = new SendMessage(chatId.toString(), "✅ Kasb yangilandi: <b>" + categoryName + "</b>");
            okMsg.setParseMode("HTML");
            bot.executeMessage(okMsg);

            // 2. Darhol to'liq profil kartasini va menyuni chiqarish
            SendMessage profileMsg = jobSeekerHandler.showProfile(chatId, Optional.ofNullable(profile));
            if (profileMsg != null) {
                bot.executeMessage(profileMsg);
            }
            return;
        }

        SendMessage response = jobSeekerHandler.handleVacancyPagination(chatId, categoryKey, 0, profileOpt);
        if (response != null) bot.executeMessage(response);
    }

    private void handleLanguageSelection(CallbackQuery callbackQuery, Long chatId, Telegram bot) {
        bot.sendAnswerCallback(callbackQuery.getId(), null, false);
        String language = callbackQuery.getData().replace("lang_", "");
        jobSeekerHandler.updateLanguage(chatId, language);

        JobSeekerState state = stateManager.getState(chatId);

        if (state == JobSeekerState.WAITING_FOR_LANGUAGE || "true".equals(stateManager.getData(chatId, "registration"))) {
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_NAME);
            stateManager.removeData(chatId, "registration");

            String namePrompt = switch (language) {
                case "ru" -> "👤 <b>Регистрация в качестве соискателя:</b>\n\nВведите ваше имя и фамилию.\n💡 <i>Пример:</i> <code>Ali Valiyev</code>";
                case "en" -> "👤 <b>Registration as a job seeker:</b>\n\nPlease enter your first and last name.\n💡 <i>Example:</i> <code>Ali Valiyev</code>";
                default -> "👤 <b>Ish izlovchi sifatida ro'yxatdan o'tish:</b>\n\nIltimos, ism va familiyangizni kiriting.\n💡 <i>Misol:</i> <code>Ali Valiyev</code>";
            };

            SendMessage nameMsg = new SendMessage(chatId.toString(), namePrompt);
            nameMsg.setParseMode("HTML");
            bot.executeMessage(nameMsg);
            return;
        }

        String responseText = switch (language) {
            case "uz" -> "✅ Til O'zbek tiliga o'zgartirildi!";
            case "ru" -> "✅ Язык изменен на Русский!";
            case "en" -> "✅ Language changed to English!";
            default -> "✅";
        };

        bot.executeMessage(new SendMessage(chatId.toString(), responseText));
    }

    private void handleRating(CallbackQuery callbackQuery, Long chatId, Integer messageId, Telegram bot) {
        String[] parts = callbackQuery.getData().replace("rate_", "").split("_");
        Long appId = Long.parseLong(parts[0]);
        int stars = Integer.parseInt(parts[1]);

        if (jobSeekerReviewRepository.existsByApplicationId(appId)) {
            bot.sendAnswerCallback(callbackQuery.getId(), "⚠️ Siz bu nomzodni baholagansiz!", true);
            return;
        }

        Optional<JobApplication> appOpt = jobApplicationRepository.findById(appId);
        if (appOpt.isEmpty()) {
            bot.sendAnswerCallback(callbackQuery.getId(), "❌ Ariza topilmadi!", true);
            return;
        }

        Long seekerId = appOpt.get().getJobSeekerId();

        JobSeekerReview review = JobSeekerReview.builder()
                .jobSeekerId(seekerId)
                .employerChatId(chatId)
                .applicationId(appId)
                .rating(stars)
                .createdAt(LocalDateTime.now())
                .build();
        jobSeekerReviewRepository.save(review);

        List<JobSeekerReview> allReviews = jobSeekerReviewRepository.findByJobSeekerId(seekerId);
        double avgRating = allReviews.stream().mapToInt(JobSeekerReview::getRating).average().orElse(0.0);
        avgRating = Math.round(avgRating * 10.0) / 10.0;

        Optional<JobSeekerProfile> seekerProfileOpt = jobSeekerProfileRepository.findById(seekerId);
        if (seekerProfileOpt.isPresent()) {
            JobSeekerProfile seekerProfile = seekerProfileOpt.get();
            seekerProfile.setRating(avgRating);
            seekerProfile.setReviewCount(allReviews.size());
            jobSeekerProfileRepository.save(seekerProfile);

            Long seekerChatId = seekerProfile.getUserId();
            if (seekerChatId != null) {
                String starEmojis = "⭐".repeat(stars);
                String seekerMsg = String.format("""
                        ⭐️ <b>Ish beruvchi sizga baho berdi!</b>
                        
                        Qo'yilgan baho: %s (<b>%d / 5</b>)
                        Sizning umumiy reytingingiz: <b>⭐ %.1f / 5.0</b> (%d ta baho)
                        """, starEmojis, stars, avgRating, allReviews.size());

                SendMessage notifyMsg = new SendMessage(seekerChatId.toString(), seekerMsg);
                notifyMsg.setParseMode("HTML");
                bot.executeMessage(notifyMsg);
            }
        }

        bot.sendAnswerCallback(callbackQuery.getId(), "⭐️ Bahoyingiz qabul qilindi!", false);

        String starEmojis = "⭐".repeat(stars);
        EditMessageText editEmployerMsg = new EditMessageText();
        editEmployerMsg.setChatId(chatId.toString());
        editEmployerMsg.setMessageId(messageId);
        editEmployerMsg.setText(String.format("✅ <b>Nomzod ishga qabul qilindi!</b>\n\n⭐️ <b>Sizning bahoyingiz:</b> %s (%d ta yulduzcha)\nBaholaganingiz uchun rahmat!", starEmojis, stars));
        editEmployerMsg.setParseMode("HTML");
        editEmployerMsg.setReplyMarkup(null);
        bot.executeEditMessage(editEmployerMsg);
    }

    private void handleApplicationStatus(CallbackQuery callbackQuery, Long chatId, Integer messageId, Telegram bot) {
        bot.sendAnswerCallback(callbackQuery.getId(), null, false);
        String[] parts = callbackQuery.getData().replace("app_status_", "").split("_");
        String action = parts[0];
        Long appId = Long.parseLong(parts[1]);
        Long seekerChatId = Long.parseLong(parts[2]);

        Optional<JobApplication> appOpt = jobApplicationRepository.findById(appId);
        if (appOpt.isEmpty()) {
            bot.sendAnswerCallback(callbackQuery.getId(), "❌ Ariza topilmadi!", true);
            return;
        }

        JobApplication app = appOpt.get();
        String employerStatusText = "";
        String seekerNotificationText = "";

        String jobTitle = jobStore.getAllVacancies().stream()
                .filter(v -> v.getId().equals(app.getJobId()))
                .map(JobVacancy::getTitle).findFirst().orElse("Vakansiya");

        boolean isFinalDecision = false;

        switch (action) {
            case "view" -> {
                app.setStatus(JobApplication.ApplicationStatus.VIEWED);
                employerStatusText = "👁 <b>Arizani ko'rib chiqildi deb belgiladingiz.</b>\nNomzodga bildirishnoma yuborildi.";
                seekerNotificationText = String.format("👁 <b>Arizangiz ko'rib chiqildi!</b>\n\nIsh beruvchi <b>«%s»</b> bo'yicha arizangizni ko'rib chiqdi.", escapeHtml(jobTitle));
            }
            case "interview" -> {
                app.setStatus(JobApplication.ApplicationStatus.INTERVIEW);
                employerStatusText = "📅 <b>Nomzodni suhbatga chaqirdingiz.</b>\nNomzodga taklifnoma yuborildi.";
                seekerNotificationText = String.format("🎉 <b>Xushxabar! Suhbatga chaqirildingiz!</b>\n\n📌 <b>Vakansiya:</b> %s\nTez orada siz bilan bog'lanishadi!", escapeHtml(jobTitle));
            }
            case "accept" -> {
                app.setStatus(JobApplication.ApplicationStatus.ACCEPTED);
                employerStatusText = "✅ <b>Nomzod ishga qabul qilindi!</b>\n\n⭐️ <b>Nomzodni baholang:</b>\nUshbu xodim bilan muloqot va hamkorlikni qanday baholaysiz?";
                seekerNotificationText = String.format("🏆 <b>Tabriklaymiz! Siz ishga qabul qilindingiz!</b>\n\n📌 <b>Vakansiya:</b> %s", escapeHtml(jobTitle));
                isFinalDecision = true;
            }
            case "reject" -> {
                app.setStatus(JobApplication.ApplicationStatus.REJECTED);
                employerStatusText = "❌ <b>Ariza rad etildi.</b>";
                seekerNotificationText = String.format("😔 <b>Arizangiz bo'yicha javob:</b>\n\n📌 <b>Vakansiya:</b> %s bo'yicha boshqa nomzod tanlandi.", escapeHtml(jobTitle));
                isFinalDecision = true;
            }
        }

        jobApplicationRepository.save(app);

        EditMessageText editEmployerMsg = new EditMessageText();
        editEmployerMsg.setChatId(chatId.toString());
        editEmployerMsg.setMessageId(messageId);
        editEmployerMsg.setText(employerStatusText);
        editEmployerMsg.setParseMode("HTML");

        if ("accept".equals(action)) {
            editEmployerMsg.setReplyMarkup(buildRatingKeyboard(appId));
        } else if (!isFinalDecision) {
            editEmployerMsg.setReplyMarkup(buildApplicationTrackingKeyboard(appId, seekerChatId));
        } else {
            editEmployerMsg.setReplyMarkup(null);
        }
        bot.executeEditMessage(editEmployerMsg);

        SendMessage notifySeeker = new SendMessage(seekerChatId.toString(), seekerNotificationText);
        notifySeeker.setParseMode("HTML");
        bot.executeMessage(notifySeeker);
    }


    private void handleApply(CallbackQuery callbackQuery, Long chatId, Telegram bot) {
        Long vacancyId = Long.parseLong(callbackQuery.getData().replace("apply_", ""));

        Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
        if (profileOpt.isEmpty()) {
            bot.sendAnswerCallback(callbackQuery.getId(), "❌ Ariza topshirish uchun avval ro'yxatdan o'ting!", true);
            return;
        }

        JobVacancy vacancy = jobStore.getAllVacancies().stream()
                .filter(v -> v.getId().equals(vacancyId)).findFirst().orElse(null);

        if (vacancy == null) {
            bot.sendAnswerCallback(callbackQuery.getId(), "❌ Vakansiya topilmadi!", true);
            return;
        }

        JobSeekerProfile profile = profileOpt.get();

        // ⭐️ 1. TAKRORIY ARIZANI BLOKLASH
        if (jobApplicationRepository.existsByJobIdAndJobSeekerId(vacancy.getId(), profile.getId())) {
            bot.sendAnswerCallback(callbackQuery.getId(), "⚠️ Siz bu vakansiyaga allaqachon ariza topshirgansiz!", true);
            return;
        }

        // ⭐️ 2. ARIZANI BAZAGA SAQLASH
        JobApplication application = new JobApplication();
        application.setJobId(vacancy.getId());
        application.setJobSeekerId(profile.getId());
        application.setCoverLetter("Ariza topshirildi");
        application.setStatus(JobApplication.ApplicationStatus.PENDING);
        JobApplication savedApp = jobApplicationRepository.save(application);

        // Telegram pop-up xabarnomasi
        bot.sendAnswerCallback(callbackQuery.getId(), "✅ Arizangiz muvaffaqiyatli yuborildi!", false);

        // ⭐️ 3. ISH BERUVCHIGA XABAR YUBORISH
        Long employerChatId = vacancy.getEmployerChatId();
        if (employerChatId != null) {
            SendMessage notifyMsg = employerHandler.buildApplicationNotification(
                    employerChatId, savedApp.getId(), chatId, vacancy.getTitle(),
                    profile.getFullName() != null ? profile.getFullName() : "Kiritilmagan",
                    profile.getPhoneNumber() != null ? profile.getPhoneNumber() : "Kiritilmagan",
                    "uz"
            );
            notifyMsg.setReplyMarkup(buildApplicationTrackingKeyboard(savedApp.getId(), chatId));
            bot.executeMessage(notifyMsg);
        }

        // ⭐️ 4. ISH IZLOVCHIGA TASDIQ XABARI
        SendMessage seekerMsg = new SendMessage(chatId.toString(),
                "✅ <b>Arizangiz ish beruvchiga yuborildi!</b>\n\n" +
                        "Holatni bilish uchun <b>«📂 Arizalar»</b> menyusini tekshirishingiz mumkin.");
        seekerMsg.setParseMode("HTML");
        bot.executeMessage(seekerMsg);
    }

    private void handleFavoriteToggle(CallbackQuery callbackQuery, Long chatId, Integer messageId, Telegram bot) {
        String[] parts = callbackQuery.getData().replace("fav_toggle_", "").split("_");
        Long vacancyId = Long.parseLong(parts[0]);
        String categoryKey = parts.length > 1 ? parts[1] : "all";
        int returnPage = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

        boolean isSaved = favoriteVacancyService.toggleFavorite(chatId, vacancyId);

        Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
        String lang = profileOpt.map(JobSeekerProfile::getLanguage).orElse("uz");

        String alertText = switch (lang) {
            case "ru" -> isSaved ? "⭐ Добавлено в избранное!" : "❌ Удалено из избранного!";
            case "en" -> isSaved ? "⭐ Saved to favorites!" : "❌ Removed from favorites!";
            default -> isSaved ? "⭐ Vakansiya saqlandi!" : "❌ Saralanganlardan o'chirildi!";
        };

        bot.sendAnswerCallback(callbackQuery.getId(), alertText, false);

        InlineKeyboardMarkup updatedKeyboard = buildVacancyDetailMarkup(vacancyId, isSaved, categoryKey, returnPage);
        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(chatId.toString());
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(updatedKeyboard);
        bot.executeEditReplyMarkup(editMarkup);
    }

    private void handleFavoriteRemove(CallbackQuery callbackQuery, Long chatId, Integer messageId, Telegram bot) {
        String[] parts = callbackQuery.getData().replace("fav_remove_", "").split("_");
        Long vacancyId = Long.parseLong(parts[0]);
        int currentPage = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

        favoriteVacancyService.toggleFavorite(chatId, vacancyId);
        bot.sendAnswerCallback(callbackQuery.getId(), "❌ Saralanganlardan o'chirildi!", false);
        showFavoritesPage(chatId, currentPage, messageId, bot);
    }

    private void handleViewVacancy(CallbackQuery callbackQuery, Long chatId, Integer messageId, Telegram bot) {
        bot.sendAnswerCallback(callbackQuery.getId(), null, false);
        String[] parts = callbackQuery.getData().replace("view_vac_", "").split("_");
        Long vacancyId = Long.parseLong(parts[0]);
        String categoryKey = parts[1];
        int returnPage = Integer.parseInt(parts[2]);

        JobVacancy vacancy = jobStore.getAllVacancies().stream()
                .filter(v -> v.getId().equals(vacancyId)).findFirst().orElse(null);

        if (vacancy == null) {
            bot.sendAnswerCallback(callbackQuery.getId(), "❌ Vakansiya topilmadi!", true);
            return;
        }

        boolean isFav = favoriteVacancyService.isFavorite(chatId, vacancyId);
        boolean isExternal = vacancy.getSourceUrl() != null && !vacancy.getSourceUrl().isBlank();

        String phoneText = (vacancy.getPhoneNumber() != null && !vacancy.getPhoneNumber().isBlank())
                ? vacancy.getPhoneNumber()
                : (isExternal ? "Havola orqali ko'ring" : "Ariza qabul qilingach beriladi");

        String cardText = String.format("""
                📌 <b>%s</b>
                
                🏢 <b>Kompaniya / Manba:</b> %s
                📂 <b>Kategoriya:</b> %s
                🕒 <b>Bandlik turi:</b> %s
                ⏱ <b>Ish vaqti:</b> %s
                💰 <b>Maosh:</b> %s
                
                📝 <b>Talablar va ma'lumot:</b>
                %s
                
                📞 <b>Bog'lanish:</b> <code>%s</code>
                """,
                escapeHtml(vacancy.getTitle()),
                escapeHtml(vacancy.getCompanyName() != null ? vacancy.getCompanyName() : "Ko'rsatilmagan"),
                escapeHtml(vacancy.getCategory() != null ? vacancy.getCategory() : "Ko'rsatilmagan"),
                escapeHtml(vacancy.getType() != null ? vacancy.getType() : "To'liq bandlik"),
                escapeHtml(vacancy.getWorkHours() != null ? vacancy.getWorkHours() : "Kelishuv asosida"),
                escapeHtml(vacancy.getSalary() != null ? vacancy.getSalary() : "Kelishiladi"),
                escapeHtml(vacancy.getRequirements() != null ? vacancy.getRequirements() : "Ko'rsatilmadi"),
                escapeHtml(phoneText)
        );

        EditMessageText editMsg = new EditMessageText();
        editMsg.setChatId(chatId.toString());
        editMsg.setMessageId(messageId);
        editMsg.setText(cardText);
        editMsg.setParseMode("HTML");
        editMsg.setReplyMarkup(buildVacancyDetailMarkup(vacancyId, isFav, categoryKey, returnPage));
        bot.executeEditMessage(editMsg);
    }

    private void handleVacancyPagination(CallbackQuery callbackQuery, Long chatId, Integer messageId, Telegram bot) {
        bot.sendAnswerCallback(callbackQuery.getId(), null, false);
        String[] parts = callbackQuery.getData().replace("vacancy_page_", "").split("_");
        String categoryKey = parts[0];
        int page = Integer.parseInt(parts[1]);

        Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
        SendMessage response = jobSeekerHandler.handleVacancyPagination(chatId, categoryKey, page, profileOpt);
        if (response != null) {
            EditMessageText editMsg = new EditMessageText();
            editMsg.setChatId(chatId.toString());
            editMsg.setMessageId(messageId);
            editMsg.setText(response.getText());
            editMsg.setParseMode("HTML");
            editMsg.setReplyMarkup((InlineKeyboardMarkup) response.getReplyMarkup());
            bot.executeEditMessage(editMsg);
        }
    }

    // ============================================
    // YORDAMCHI METODLAR VA KBD BUILDERLAR
    // ============================================

    public void showFavoritesPage(Long chatId, int pageNumber, Integer messageId, Telegram bot) {
        Page<FavoriteVacancyDto> page = favoriteVacancyService.getFavorites(chatId, PageRequest.of(pageNumber, 1));
        Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);

        if (page.isEmpty() && pageNumber > 0) {
            showFavoritesPage(chatId, pageNumber - 1, messageId, bot);
            return;
        }

        if (page.isEmpty()) {
            String emptyText = "⭐ Sizda hali saqlangan vakansiyalar mavjud emas.";
            if (messageId != null) {
                EditMessageText editMsg = new EditMessageText();
                editMsg.setChatId(chatId.toString());
                editMsg.setMessageId(messageId);
                editMsg.setText(emptyText);
                bot.executeEditMessage(editMsg);
            } else {
                bot.executeMessage(new SendMessage(chatId.toString(), emptyText));
            }
            return;
        }

        FavoriteVacancyDto item = page.getContent().get(0);
        JobVacancy vacancy = jobStore.getAllVacancies().stream().filter(v -> v.getId().equals(item.vacancyId())).findFirst().orElse(null);

        String company = (vacancy != null && vacancy.getCompanyName() != null) ? vacancy.getCompanyName() : "Ko'rsatilmagan";
        String requirements = (vacancy != null && vacancy.getRequirements() != null) ? vacancy.getRequirements() : "Ko'rsatilmadi";
        String phone = (vacancy != null && vacancy.getPhoneNumber() != null) ? vacancy.getPhoneNumber() : "Ariza topshirilgach beriladi";

        String text = String.format("""
                ⭐ <b>Saralangan vakansiya (%d / %d):</b>
                
                📌 <b>%s</b>
                
                🏢 <b>Kompaniya:</b> %s
                📂 <b>Kategoriya:</b> %s
                🕒 <b>Ish turi:</b> %s
                ⏱ <b>Ish vaqti:</b> %s
                💰 <b>Maosh:</b> %s
                
                📝 <b>Talablar:</b>
                %s
                
                📞 <b>Bog'lanish:</b> %s
                """,
                page.getNumber() + 1, page.getTotalPages(),
                escapeHtml(item.title()), escapeHtml(company), escapeHtml(item.category()),
                escapeHtml(item.type()), escapeHtml(item.workHours()), escapeHtml(item.salary()),
                escapeHtml(requirements), escapeHtml(phone)
        );

        InlineKeyboardMarkup markup = keyboardService.buildFavoritesPaginationKeyboard(page.getNumber(), page.getTotalPages(), item.vacancyId(), profileOpt);

        if (messageId != null) {
            EditMessageText editMsg = new EditMessageText();
            editMsg.setChatId(chatId.toString());
            editMsg.setMessageId(messageId);
            editMsg.setText(text);
            editMsg.setParseMode("HTML");
            editMsg.setReplyMarkup(markup);
            bot.executeEditMessage(editMsg);
        } else {
            SendMessage sendMsg = new SendMessage(chatId.toString(), text);
            sendMsg.setParseMode("HTML");
            sendMsg.setReplyMarkup(markup);
            bot.executeMessage(sendMsg);
        }
    }

    private InlineKeyboardMarkup buildRatingKeyboard(Long applicationId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> starRow = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("⭐ " + i);
            btn.setCallbackData("rate_" + applicationId + "_" + i);
            starRow.add(btn);
        }
        rows.add(starRow);
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup buildApplicationTrackingKeyboard(Long applicationId, Long seekerChatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton viewBtn = new InlineKeyboardButton();
        viewBtn.setText("👁 Ko'rib chiqildi");
        viewBtn.setCallbackData("app_status_view_" + applicationId + "_" + seekerChatId);
        rows.add(List.of(viewBtn));

        InlineKeyboardButton interviewBtn = new InlineKeyboardButton();
        interviewBtn.setText("📅 Suhbatga chaqirish");
        interviewBtn.setCallbackData("app_status_interview_" + applicationId + "_" + seekerChatId);
        rows.add(List.of(interviewBtn));

        InlineKeyboardButton acceptBtn = new InlineKeyboardButton();
        acceptBtn.setText("✅ Qabul qilish");
        acceptBtn.setCallbackData("app_status_accept_" + applicationId + "_" + seekerChatId);

        InlineKeyboardButton rejectBtn = new InlineKeyboardButton();
        rejectBtn.setText("❌ Rad etish");
        rejectBtn.setCallbackData("app_status_reject_" + applicationId + "_" + seekerChatId);
        rows.add(List.of(acceptBtn, rejectBtn));

        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup buildVacancyDetailMarkup(Long vacancyId, boolean isFav, String categoryKey, int returnPage) {
        JobVacancy vacancy = jobStore.getAllVacancies().stream()
                .filter(v -> v.getId().equals(vacancyId))
                .findFirst()
                .orElse(null);

        InlineKeyboardMarkup cardKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        boolean isExternal = vacancy != null && vacancy.getSourceUrl() != null && !vacancy.getSourceUrl().isBlank();

        if (isExternal) {
            InlineKeyboardButton openUrlBtn = new InlineKeyboardButton();
            openUrlBtn.setText("🌐 E'lonni ochish va bog'lanish");
            openUrlBtn.setUrl(vacancy.getSourceUrl());
            rows.add(List.of(openUrlBtn));
        } else {
            InlineKeyboardButton applyBtn = new InlineKeyboardButton();
            applyBtn.setText("✍️ Ariza topshirish");
            applyBtn.setCallbackData("apply_" + vacancyId);
            rows.add(List.of(applyBtn));
        }

        InlineKeyboardButton favBtn = new InlineKeyboardButton();
        favBtn.setText(isFav ? "🌟 Saqlangan (O'chirish)" : "⭐ Saralanganlarga saqlash");
        favBtn.setCallbackData("fav_toggle_" + vacancyId + "_" + categoryKey + "_" + returnPage);
        rows.add(List.of(favBtn));

        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("⬅️ Ro'yxatga qaytish");
        backBtn.setCallbackData("vacancy_page_" + categoryKey + "_" + returnPage);
        rows.add(List.of(backBtn));

        cardKeyboard.setKeyboard(rows);
        return cardKeyboard;
    }

    private String getCategoryNameByKey(String key) {
        return switch (key) {
            case "it" -> "IT & Dasturlash";
            case "design" -> "Dizayn";
            case "construction" -> "Qurilish";
            case "driver" -> "Haydovchi / Kuryer";
            case "education" -> "Ta'lim / Repetitor";
            case "trade" -> "Savdo / Sotuvchi";
            case "cleaner" -> "Farrosh / Tozalash";
            case "cook" -> "Pazanda / Oshpaz";
            case "security" -> "Qorovul / Xavfsizlik";
            case "courier" -> "Kuryer / Yetkazib berish";
            default -> "Barcha vakansiyalar";
        };
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}