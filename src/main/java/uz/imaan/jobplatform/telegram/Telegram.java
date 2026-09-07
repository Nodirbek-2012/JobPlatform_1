package uz.imaan.jobplatform.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.repository.JobApplicationRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.JobSeekerHandler;

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

    private final Map<Long, UserRole> userRoles = new ConcurrentHashMap<>();

    @Autowired
    public Telegram(
            JobSeekerHandler jobSeekerHandler,
            EmployerHandler employerHandler,
            JobStore jobStore,
            JobSeekerProfileRepository jobSeekerProfileRepository,
            JobApplicationRepository jobApplicationRepository,
            @Value("${telegram.bot.token}") String botToken) {
        super(botToken);
        this.jobSeekerHandler = jobSeekerHandler;
        this.employerHandler = employerHandler;
        this.jobStore = jobStore;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.jobApplicationRepository = jobApplicationRepository;
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

            log.info("📩 Callback: chatId={}, data={}", chatId, callbackData);

            try {
                if (callbackData != null && callbackData.startsWith("emp_cat_")) {
                    SendMessage response = employerHandler.handleCallback(callbackQuery);
                    if (response != null) {
                        executeMessage(response);
                    }
                    return;
                }


                if (callbackData.startsWith("accept_app_") || callbackData.startsWith("reject_app_")) {
                    boolean isAccept = callbackData.startsWith("accept_app_");
                    String prefix = isAccept ? "accept_app_" : "reject_app_";
                    String[] parts = callbackData.replace(prefix, "").split("_");

                    Long appId = Long.parseLong(parts[0]);
                    Long seekerChatId = Long.parseLong(parts[1]);
                    Integer messageId = callbackQuery.getMessage().getMessageId();

                    Optional<JobApplication> appOpt = jobApplicationRepository.findById(appId);
                    if (appOpt.isPresent()) {
                        JobApplication app = appOpt.get();
                        app.setStatus(isAccept ? JobApplication.ApplicationStatus.ACCEPTED : JobApplication.ApplicationStatus.REJECTED);
                        jobApplicationRepository.save(app);
                    }

                    EditMessageText editMsg = new EditMessageText();
                    editMsg.setChatId(chatId.toString());
                    editMsg.setMessageId(messageId);
                    editMsg.setText(isAccept ? "✅ **Ushbu ariza qabul qilindi!**" : "❌ **Ushbu ariza rad etildi.**");
                    editMsg.setParseMode("Markdown");
                    executeEditMessage(editMsg);

                    String seekerText = isAccept ?
                            "🎉 **Xushxabar!** Sizning arizangiz ish beruvchi tomonidan qabul qilindi. Tez orada siz bilan bog'lanishadi!" :
                            "😔 **Afsuski**, sizning arizangiz ish beruvchi tomonidan rad etildi.";

                    SendMessage seekerMsg = new SendMessage(seekerChatId.toString(), seekerText);
                    seekerMsg.setParseMode("Markdown");
                    executeMessage(seekerMsg);

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


                // ✅ YANGILANGAN QISM – PAGINATION ISHLATILADI
                if (callbackData.startsWith("category_")) {
                    String categoryKey = callbackData.replace("category_", ""); // to'g'ri
                    Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId); // chatId
                    SendMessage response = jobSeekerHandler.handleVacancyPagination(chatId, categoryKey, 0, profileOpt);
                    if (response != null) {
                        executeMessage(response);
                    }
                    return;
                }

                if (callbackData.equals("back_to_categories")) {
                    SendMessage response = new SendMessage();
                    response.setChatId(chatId.toString());
                    response.setText("📂 **Kategoriyani tanlang:**");
                    response.setParseMode("Markdown");
                    response.setReplyMarkup(jobSeekerHandler.getCategoryInlineKeyboard(Optional.empty()));
                    executeMessage(response);
                    return;
                }

                if (callbackData.startsWith("apply_")) {
                    Long vacancyId = Long.parseLong(callbackData.replace("apply_", ""));

                    Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
                    if (profileOpt.isEmpty()) {
                        SendMessage response = new SendMessage();
                        response.setChatId(chatId.toString());
                        response.setText("❌ Iltimos, avval ro'yxatdan o'ting!");
                        executeMessage(response);
                        return;
                    }

                    JobVacancy vacancy = jobStore.getAllVacancies().stream()
                            .filter(v -> v.getId().equals(vacancyId))
                            .findFirst()
                            .orElse(null);

                    if (vacancy == null) {
                        SendMessage response = new SendMessage();
                        response.setChatId(chatId.toString());
                        response.setText("❌ Vakansiya topilmadi!");
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
                        executeMessage(notifyMsg);
                    }

                    SendMessage response = new SendMessage();
                    response.setChatId(chatId.toString());
                    response.setText("✅ Ariza muvaffaqiyatli topshirildi!");
                    executeMessage(response);
                    return;
                }

            } catch (Exception e) {
                log.error("❌ Xatolik: {}", e.getMessage());
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

        if (text.equals("/start") || text.equals("🔄 Rolni o'zgartirish") || text.equals("Asosiy menyu") || text.equals("🏠 Asosiy menyu")) {
            userRoles.put(chatId, UserRole.NONE);
            sendRoleSelectionMenu(chatId, "Hush kelibsiz! Rolingizni tanlang:");
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
        message.setText("🌐 **Tilni tanlang / Выберите язык:**");
        message.setParseMode("Markdown");
        message.setReplyMarkup(getLanguageInlineKeyboard());
        executeMessage(message);
    }

    private InlineKeyboardMarkup getLanguageInlineKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
                .text("🇺🇿 O'zbek")
                .callbackData("lang_uz")
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text("🇷🇺 Русский")
                .callbackData("lang_ru")
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text("🇬🇧 English")
                .callbackData("lang_en")
                .build());

        rows.add(row1);
        markup.setKeyboard(rows);
        return markup;
    }

    // (Bu metod endi ishlatilmaydi, lekin boshqa joylarga tegmaslik uchun qoldirilgan)
    private String getCategoryName(String key) {
        return switch (key) {
            case "it" -> "💻 IT & Dasturlash";
            case "design" -> "🎨 Dizayn";
            case "construction" -> "🏗️ Qurilish";
            case "driver" -> "🚗 Haydovchi / Kuryer";
            case "education" -> "📚 Ta'lim / Repetitor";
            case "trade" -> "🛒 Savdo / Sotuvchi";
            case "cleaner" -> "🧹 Farrosh / Tozalash";
            case "cook" -> "👨‍🍳 Pazanda / Oshpaz";
            case "security" -> "🔒 Qorovul / Xavfsizlik";
            case "courier" -> "📦 Kuryer / Yetkazib berish";
            case "all" -> "Barcha vakansiyalar";
            default -> key;
        };
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Xatolik: {}", e.getMessage());
        }
    }

    private void executeEditMessage(EditMessageText message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Edit Message Xatolik: {}", e.getMessage());
        }
    }
}