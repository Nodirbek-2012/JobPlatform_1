package uz.imaan.jobplatform.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.imaan.jobplatform.employer.job.JobStore;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.jobseeker.dto.FavoriteVacancyDto;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.repository.JobApplicationRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.jobseeker.service.interfaces.FavoriteVacancyService;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.JobSeekerHandler;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.KeyboardService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class Telegram extends TelegramLongPollingBot {

    public enum UserRole {
        NONE,
        EMPLOYER,
        JOB_SEEKER
    }

    private final JobSeekerHandler jobSeekerHandler;
    private final EmployerHandler employerHandler;
    private final JobStore jobStore;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final FavoriteVacancyService favoriteVacancyService;
    private final KeyboardService keyboardService;

    private final Map<Long, UserRole> userRoles = new ConcurrentHashMap<>();

    @Autowired
    public Telegram(
            JobSeekerHandler jobSeekerHandler,
            EmployerHandler employerHandler,
            JobStore jobStore,
            JobSeekerProfileRepository jobSeekerProfileRepository,
            JobApplicationRepository jobApplicationRepository,
            FavoriteVacancyService favoriteVacancyService,
            KeyboardService keyboardService,
            @Value("8449248126:AAFPgTpsBD2o1k_cp8YbG8_wqp9o8KnRCss") String botToken) {
        super(botToken);
        this.jobSeekerHandler = jobSeekerHandler;
        this.employerHandler = employerHandler;
        this.jobStore = jobStore;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.favoriteVacancyService = favoriteVacancyService;
        this.keyboardService = keyboardService;
        log.info("✅ Telegram bot ishga tushdi!");
    }

    @Override
    public String getBotUsername() {
        return "@JobPlatformUzBot";
    }

    @Override
    public void onUpdateReceived(Update update) {

        // ============================================
        // 1. CALLBACK QUERY (INLINE TUGMALAR)
        // ============================================
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            String callbackData = callbackQuery.getData();
            Long chatId = callbackQuery.getMessage().getChatId();
            Integer messageId = callbackQuery.getMessage().getMessageId();

            log.info("📩 Callback: chatId={}, data={}", chatId, callbackData);

            try {
                if (callbackData != null && callbackData.startsWith("emp_cat_")) {
                    SendMessage response = employerHandler.handleCallback(callbackQuery);
                    if (response != null) {
                        executeMessage(response);
                    }
                    return;
                }

                if ("noop".equals(callbackData) || "ignore".equals(callbackData)) {
                    sendAnswerCallback(callbackQuery.getId(), null, false);
                    return;
                }

                if (callbackData.startsWith("fav_toggle_")) {
                    String[] parts = callbackData.replace("fav_toggle_", "").split("_");
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

                    sendAnswerCallback(callbackQuery.getId(), alertText, false);

                    InlineKeyboardMarkup updatedKeyboard = buildVacancyDetailMarkup(vacancyId, isSaved, categoryKey, returnPage);
                    EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
                    editMarkup.setChatId(chatId.toString());
                    editMarkup.setMessageId(messageId);
                    editMarkup.setReplyMarkup(updatedKeyboard);
                    executeEditReplyMarkup(editMarkup);
                    return;
                }

                if (callbackData.startsWith("fav_remove_")) {
                    String[] parts = callbackData.replace("fav_remove_", "").split("_");
                    Long vacancyId = Long.parseLong(parts[0]);
                    int currentPage = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

                    favoriteVacancyService.toggleFavorite(chatId, vacancyId);

                    Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
                    String lang = profileOpt.map(JobSeekerProfile::getLanguage).orElse("uz");
                    String alertText = switch (lang) {
                        case "ru" -> "❌ Удалено из избранного!";
                        case "en" -> "❌ Removed from favorites!";
                        default -> "❌ Saralanganlardan o'chirildi!";
                    };
                    sendAnswerCallback(callbackQuery.getId(), alertText, false);

                    showFavoritesPage(chatId, currentPage, messageId);
                    return;
                }

                if (callbackData.startsWith("fav_page_")) {
                    sendAnswerCallback(callbackQuery.getId(), null, false);
                    int targetPage = Integer.parseInt(callbackData.replace("fav_page_", ""));
                    showFavoritesPage(chatId, targetPage, messageId);
                    return;
                }

                if (callbackData.startsWith("view_vac_")) {
                    sendAnswerCallback(callbackQuery.getId(), null, false);

                    String[] parts = callbackData.replace("view_vac_", "").split("_");
                    Long vacancyId = Long.parseLong(parts[0]);
                    String categoryKey = parts[1];
                    int returnPage = Integer.parseInt(parts[2]);

                    JobVacancy vacancy = jobStore.getAllVacancies().stream()
                            .filter(v -> v.getId().equals(vacancyId))
                            .findFirst()
                            .orElse(null);

                    if (vacancy == null) {
                        sendAnswerCallback(callbackQuery.getId(), "❌ Vakansiya topilmadi!", true);
                        return;
                    }

                    boolean isFav = favoriteVacancyService.isFavorite(chatId, vacancyId);

                    String cardText = String.format("""
                            📌 <b>%s</b>
                            
                            🏢 <b>Kompaniya:</b> %s
                            📂 <b>Kategoriya:</b> %s
                            🕒 <b>Bandlik turi:</b> %s
                            ⏱ <b>Ish vaqti:</b> %s
                            💰 <b>Maosh:</b> %s
                            
                            📝 <b>Talablar:</b>
                            %s
                            
                            📞 <b>Bog'lanish:</b> %s
                            """,
                            escapeHtml(vacancy.getTitle()),
                            escapeHtml(vacancy.getCompanyName() != null ? vacancy.getCompanyName() : "Ko'rsatilmagan"),
                            escapeHtml(vacancy.getCategory() != null ? vacancy.getCategory() : "Ko'rsatilmagan"),
                            escapeHtml(vacancy.getType() != null ? vacancy.getType() : "To'liq bandlik"),
                            escapeHtml(vacancy.getWorkHours() != null ? vacancy.getWorkHours() : "Kelishuv asosida"),
                            escapeHtml(vacancy.getSalary() != null ? vacancy.getSalary() : "Kelishiladi"),
                            escapeHtml(vacancy.getRequirements() != null ? vacancy.getRequirements() : "Ko'rsatilmadi"),
                            escapeHtml(vacancy.getPhoneNumber() != null ? vacancy.getPhoneNumber() : "Ariza topshirilgach beriladi")
                    );

                    InlineKeyboardMarkup cardKeyboard = buildVacancyDetailMarkup(vacancyId, isFav, categoryKey, returnPage);

                    EditMessageText editMsg = new EditMessageText();
                    editMsg.setChatId(chatId.toString());
                    editMsg.setMessageId(messageId);
                    editMsg.setText(cardText);
                    editMsg.setParseMode("HTML");
                    editMsg.setReplyMarkup(cardKeyboard);
                    executeEditMessage(editMsg);
                    return;
                }

                if (callbackData.startsWith("vacancy_page_")) {
                    sendAnswerCallback(callbackQuery.getId(), null, false);
                    String[] parts = callbackData.replace("vacancy_page_", "").split("_");
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
                        executeEditMessage(editMsg);
                    }
                    return;
                }

                if (callbackData.startsWith("category_")) {
                    sendAnswerCallback(callbackQuery.getId(), null, false);
                    String categoryKey = callbackData.replace("category_", "");
                    Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
                    SendMessage response = jobSeekerHandler.handleVacancyPagination(chatId, categoryKey, 0, profileOpt);
                    if (response != null) {
                        executeMessage(response);
                    }
                    return;
                }

                if (callbackData.equals("back_to_categories")) {
                    sendAnswerCallback(callbackQuery.getId(), null, false);
                    SendMessage response = new SendMessage();
                    response.setChatId(chatId.toString());
                    response.setText("📂 <b>Kategoriyani tanlang:</b>");
                    response.setParseMode("HTML");
                    response.setReplyMarkup(jobSeekerHandler.getCategoryInlineKeyboard(Optional.empty()));
                    executeMessage(response);
                    return;
                }

                if (callbackData.startsWith("apply_")) {
                    sendAnswerCallback(callbackQuery.getId(), null, false);
                    Long vacancyId = Long.parseLong(callbackData.replace("apply_", ""));

                    Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
                    if (profileOpt.isEmpty()) {
                        SendMessage response = new SendMessage(chatId.toString(), "❌ Iltimos, avval ro'yxatdan o'ting!");
                        executeMessage(response);
                        return;
                    }

                    JobVacancy vacancy = jobStore.getAllVacancies().stream()
                            .filter(v -> v.getId().equals(vacancyId))
                            .findFirst()
                            .orElse(null);

                    if (vacancy == null) {
                        SendMessage response = new SendMessage(chatId.toString(), "❌ Vakansiya topilmadi!");
                        executeMessage(response);
                        return;
                    }

                    JobSeekerProfile profile = profileOpt.get();
                    JobApplication application = new JobApplication();
                    application.setJobId(vacancy.getId());
                    application.setJobSeekerId(profile.getId());
                    application.setCoverLetter("Ariza topshirildi");
                    application.setStatus(JobApplication.ApplicationStatus.PENDING);
                    JobApplication savedApp = jobApplicationRepository.save(application);

                    Long employerChatId = vacancy.getEmployerChatId();
                    if (employerChatId != null) {
                        SendMessage notifyMsg = employerHandler.buildApplicationNotification(
                                employerChatId,
                                savedApp.getId(),
                                chatId,
                                vacancy.getTitle(),
                                profile.getFullName() != null ? profile.getFullName() : "Kiritilmagan",
                                profile.getPhoneNumber() != null ? profile.getPhoneNumber() : "Kiritilmagan",
                                "uz"
                        );
                        notifyMsg.setReplyMarkup(buildApplicationTrackingKeyboard(savedApp.getId(), chatId));
                        executeMessage(notifyMsg);
                    }

                    SendMessage response = new SendMessage(chatId.toString(), "✅ Arizangiz ish beruvchiga yuborildi!");
                    executeMessage(response);
                    return;
                }

                if (callbackData.startsWith("app_status_")) {
                    sendAnswerCallback(callbackQuery.getId(), null, false);

                    String[] parts = callbackData.replace("app_status_", "").split("_");
                    String action = parts[0];
                    Long appId = Long.parseLong(parts[1]);
                    Long seekerChatId = Long.parseLong(parts[2]);

                    Optional<JobApplication> appOpt = jobApplicationRepository.findById(appId);
                    if (appOpt.isEmpty()) {
                        sendAnswerCallback(callbackQuery.getId(), "❌ Ariza topilmadi!", true);
                        return;
                    }

                    JobApplication app = appOpt.get();
                    String employerStatusText = "";
                    String seekerNotificationText = "";

                    String jobTitle = jobStore.getAllVacancies().stream()
                            .filter(v -> v.getId().equals(app.getJobId()))
                            .map(JobVacancy::getTitle)
                            .findFirst()
                            .orElse("Vakansiya");

                    boolean isFinalDecision = false;

                    switch (action) {
                        case "view" -> {
                            app.setStatus(JobApplication.ApplicationStatus.VIEWED);
                            employerStatusText = "👁 <b>Arizani ko'rib chiqildi deb belgiladingiz.</b>\nNomzodga bildirishnoma yuborildi.";
                            seekerNotificationText = String.format("""
                                    👁 <b>Arizangiz ko'rib chiqildi!</b>
                                    
                                    Ish beruvchi <b>«%s»</b> vakansiyasi bo'yicha arizangizni ko'rib chiqdi. Keyingi bosqich kutilmoqda.
                                    """, escapeHtml(jobTitle));
                        }
                        case "interview" -> {
                            app.setStatus(JobApplication.ApplicationStatus.INTERVIEW);
                            employerStatusText = "📅 <b>Nomzodni suhbatga chaqirdingiz.</b>\nNomzodga taklifnoma yuborildi.";
                            seekerNotificationText = String.format("""
                                    🎉 <b>Xushxabar! Suhbatga chaqirildingiz!</b>
                                    
                                    📌 <b>Vakansiya:</b> %s
                                    
                                    Ish beruvchi sizni suhbat (intervyu) bosqichiga taklif qildi. Tez orada siz bilan bog'lanishadi!
                                    """, escapeHtml(jobTitle));
                        }
                        case "accept" -> {
                            app.setStatus(JobApplication.ApplicationStatus.ACCEPTED);
                            employerStatusText = "✅ <b>Nomzod ishga qabul qilindi!</b>";
                            seekerNotificationText = String.format("""
                                    🏆 <b>Tabriklaymiz! Siz ishga qabul qilindingiz!</b>
                                    
                                    📌 <b>Vakansiya:</b> %s
                                    
                                    Ish beruvchi arizangizni ma'qulladi. Jamoaga xush kelibsiz!
                                    """, escapeHtml(jobTitle));
                            isFinalDecision = true;
                        }
                        case "reject" -> {
                            app.setStatus(JobApplication.ApplicationStatus.REJECTED);
                            employerStatusText = "❌ <b>Ariza rad etildi.</b>";
                            seekerNotificationText = String.format("""
                                    😔 <b>Arizangiz bo'yicha javob:</b>
                                    
                                    📌 <b>Vakansiya:</b> %s
                                    
                                    Afsuski, ushbu vakansiya bo'yicha boshqa nomzod tanlandi. Yangi e'lonlarga ariza topshirishda davom eting!
                                    """, escapeHtml(jobTitle));
                            isFinalDecision = true;
                        }
                    }

                    jobApplicationRepository.save(app);

                    EditMessageText editEmployerMsg = new EditMessageText();
                    editEmployerMsg.setChatId(chatId.toString());
                    editEmployerMsg.setMessageId(messageId);
                    editEmployerMsg.setText(employerStatusText);
                    editEmployerMsg.setParseMode("HTML");
                    if (!isFinalDecision) {
                        editEmployerMsg.setReplyMarkup(buildApplicationTrackingKeyboard(appId, seekerChatId));
                    } else {
                        editEmployerMsg.setReplyMarkup(null);
                    }
                    executeEditMessage(editEmployerMsg);

                    SendMessage notifySeeker = new SendMessage();
                    notifySeeker.setChatId(seekerChatId.toString());
                    notifySeeker.setText(seekerNotificationText);
                    notifySeeker.setParseMode("HTML");
                    executeMessage(notifySeeker);

                    return;
                }

                if (callbackData.startsWith("lang_")) {
                    String language = callbackData.replace("lang_", "");
                    jobSeekerHandler.updateLanguage(chatId, language);

                    String responseText = switch (language) {
                        case "uz" -> "✅ Til O'zbek tiliga o'zgartirildi!";
                        case "ru" -> "✅ Язык изменен на Русский!";
                        case "en" -> "✅ Language changed to English!";
                        default -> "❌ Noto'g'ri tanlov!";
                    };

                    SendMessage response = new SendMessage();
                    response.setChatId(chatId.toString());
                    response.setText(responseText);
                    response.setReplyMarkup(null);
                    executeMessage(response);
                    return;
                }

            } catch (Exception e) {
                log.error("❌ Xatolik: {}", e.getMessage(), e);
            }
            return;
        }

        // ============================================
        // 2. TEKST XABARLAR
        // ============================================
        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.hasText() ? message.getText() : "";

        log.info("📩 Xabar: chatId={}, text={}", chatId, text);

        // Saralanganlar
        if (text.contains("Saralanganlar") || text.contains("Избранное") || text.contains("Favorites")) {
            showFavoritesPage(chatId, 0, null);
            return;
        }

        // ===== "Asosiy menyu" va "/start" endi rol tanlashga olib bormaydi =====
        // Faqat "/start" va "🔄 Rolni o'zgartirish" rol tanlashga olib boradi.
        if (text.equals("/start") || text.equals("🔄 Rolni o'zgartirish")) {
            userRoles.put(chatId, UserRole.NONE);
            sendRoleSelectionMenu(chatId, "Xush kelibsiz! Rolingizni tanlang:");
            return;
        }

        if (text.equals("/language") || text.equals("🌐 Til") || text.equals("🌐 Язык") || text.equals("🌐 Language")) {
            sendLanguageSelectionMenu(chatId);
            return;
        }

        if (text.contains("Employer (Ish beruvchi)") || text.contains("Ish beruvchi (Employer)")) {
            userRoles.put(chatId, UserRole.EMPLOYER);
        } else if (text.contains("JobSeeker (Ish izlovchi)") || text.contains("Ish izlovchi (JobSeeker)")) {
            userRoles.put(chatId, UserRole.JOB_SEEKER);
        }

        UserRole role = userRoles.getOrDefault(chatId, UserRole.NONE);

        if (role == UserRole.EMPLOYER) {
            SendMessage response = employerHandler.handleEmployer(message);
            if (response != null) executeMessage(response);
        } else if (role == UserRole.JOB_SEEKER) {
            SendMessage response = jobSeekerHandler.handleJobSeeker(message);
            if (response != null) executeMessage(response);
        } else {
            sendRoleSelectionMenu(chatId, "Iltimos, avval rolingizni tanlang:");
        }
    }

    // ============================================
    // YORDAMCHI METODLAR
    // ============================================
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
        InlineKeyboardMarkup cardKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton applyBtn = new InlineKeyboardButton();
        applyBtn.setText("✍️ Ariza topshirish");
        applyBtn.setCallbackData("apply_" + vacancyId);
        rows.add(List.of(applyBtn));

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

    private void showFavoritesPage(Long chatId, int pageNumber, Integer messageId) {
        Page<FavoriteVacancyDto> page = favoriteVacancyService.getFavorites(chatId, PageRequest.of(pageNumber, 1));
        Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
        String lang = profileOpt.map(JobSeekerProfile::getLanguage).orElse("uz");

        if (page.isEmpty() && pageNumber > 0) {
            showFavoritesPage(chatId, pageNumber - 1, messageId);
            return;
        }

        if (page.isEmpty()) {
            String emptyText = switch (lang) {
                case "ru" -> "⭐ У вас пока нет сохраненных вакансий.";
                case "en" -> "⭐ You don't have any saved vacancies yet.";
                default -> "⭐ Sizda hali saqlangan vakansiyalar mavjud emas.";
            };

            if (messageId != null) {
                EditMessageText editMsg = new EditMessageText();
                editMsg.setChatId(chatId.toString());
                editMsg.setMessageId(messageId);
                editMsg.setText(emptyText);
                editMsg.setReplyMarkup(null);
                executeEditMessage(editMsg);
            } else {
                SendMessage emptyMsg = new SendMessage(chatId.toString(), emptyText);
                executeMessage(emptyMsg);
            }
            return;
        }

        FavoriteVacancyDto item = page.getContent().get(0);
        JobVacancy vacancy = jobStore.getAllVacancies().stream()
                .filter(v -> v.getId().equals(item.vacancyId()))
                .findFirst()
                .orElse(null);

        String company = (vacancy != null && vacancy.getCompanyName() != null) ? vacancy.getCompanyName() : "Ko'rsatilmagan";
        String requirements = (vacancy != null && vacancy.getRequirements() != null) ? vacancy.getRequirements() : "Ko'rsatilmadi";
        String phone = (vacancy != null && vacancy.getPhoneNumber() != null) ? vacancy.getPhoneNumber() : "Ariza topshirilgach beriladi";

        String text;
        if ("ru".equals(lang)) {
            text = String.format("""
                    ⭐ <b>Избранная вакансия (%d / %d):</b>
                    
                    📌 <b>%s</b>
                    
                    🏢 <b>Компания:</b> %s
                    📂 <b>Категория:</b> %s
                    🕒 <b>Тип работы:</b> %s
                    ⏱ <b>График:</b> %s
                    💰 <b>Зарплата:</b> %s
                    
                    📝 <b>Требования:</b>
                    %s
                    
                    📞 <b>Контакты:</b> %s
                    """,
                    page.getNumber() + 1, page.getTotalPages(),
                    escapeHtml(item.title()), escapeHtml(company), escapeHtml(item.category()),
                    escapeHtml(item.type()), escapeHtml(item.workHours()), escapeHtml(item.salary()),
                    escapeHtml(requirements), escapeHtml(phone)
            );
        } else if ("en".equals(lang)) {
            text = String.format("""
                    ⭐ <b>Saved vacancy (%d / %d):</b>
                    
                    📌 <b>%s</b>
                    
                    🏢 <b>Company:</b> %s
                    📂 <b>Category:</b> %s
                    🕒 <b>Job type:</b> %s
                    ⏱ <b>Work hours:</b> %s
                    💰 <b>Salary:</b> %s
                    
                    📝 <b>Requirements:</b>
                    %s
                    
                    📞 <b>Contact:</b> %s
                    """,
                    page.getNumber() + 1, page.getTotalPages(),
                    escapeHtml(item.title()), escapeHtml(company), escapeHtml(item.category()),
                    escapeHtml(item.type()), escapeHtml(item.workHours()), escapeHtml(item.salary()),
                    escapeHtml(requirements), escapeHtml(phone)
            );
        } else {
            text = String.format("""
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
        }

        InlineKeyboardMarkup markup = keyboardService.buildFavoritesPaginationKeyboard(
                page.getNumber(), page.getTotalPages(), item.vacancyId(), profileOpt
        );

        if (messageId != null) {
            EditMessageText editMsg = new EditMessageText();
            editMsg.setChatId(chatId.toString());
            editMsg.setMessageId(messageId);
            editMsg.setText(text);
            editMsg.setParseMode("HTML");
            editMsg.setReplyMarkup(markup);
            executeEditMessage(editMsg);
        } else {
            SendMessage sendMsg = new SendMessage();
            sendMsg.setChatId(chatId.toString());
            sendMsg.setText(text);
            sendMsg.setParseMode("HTML");
            sendMsg.setReplyMarkup(markup);
            executeMessage(sendMsg);
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void sendRoleSelectionMenu(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Employer (Ish beruvchi)"));
        row.add(new KeyboardButton("JobSeeker (Ish izlovchi)"));

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    private void sendLanguageSelectionMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🌐 <b>Tilni tanlang / Выберите язык:</b>");
        message.setParseMode("HTML");
        message.setReplyMarkup(getLanguageInlineKeyboard());
        executeMessage(message);
    }

    private InlineKeyboardMarkup getLanguageInlineKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder().text("🇺🇿 O'zbek").callbackData("lang_uz").build());
        row1.add(InlineKeyboardButton.builder().text("🇷🇺 Русский").callbackData("lang_ru").build());
        row1.add(InlineKeyboardButton.builder().text("🇬🇧 English").callbackData("lang_en").build());

        rows.add(row1);
        markup.setKeyboard(rows);
        return markup;
    }

    public void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Xatolik: {}", e.getMessage());
        }
    }

    public void executeEditMessage(EditMessageText message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Edit Message Xatolik: {}", e.getMessage());
        }
    }

    public void executeEditReplyMarkup(EditMessageReplyMarkup markup) {
        try {
            execute(markup);
        } catch (TelegramApiException e) {
            log.error("❌ EditReplyMarkup Xatolik: {}", e.getMessage());
        }
    }

    public void sendAnswerCallback(String callbackQueryId, String text, boolean showAlert) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQueryId);
            if (text != null) {
                answer.setText(text);
                answer.setShowAlert(showAlert);
            }
            execute(answer);
        } catch (TelegramApiException e) {
            log.error("❌ AnswerCallback Xatolik: {}", e.getMessage());
        }
    }
}