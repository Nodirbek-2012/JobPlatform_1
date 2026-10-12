package uz.imaan.jobplatform.telegram.JobSeekerHandler.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.imaan.jobplatform.employer.job.JobStore;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;
import uz.imaan.jobplatform.jobseeker.dto.BankCardRequest;
import uz.imaan.jobplatform.jobseeker.entity.BankCard;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.entity.Subscription;
import uz.imaan.jobplatform.jobseeker.repository.BankCardRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobApplicationRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.jobseeker.repository.SubscriptionRepository;
import uz.imaan.jobplatform.jobseeker.service.interfaces.FavoriteVacancyService;
import uz.imaan.jobplatform.jobseeker.service.interfaces.WalletService;
import uz.imaan.jobplatform.telegram.EmployerHandler;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.JobSeekerState;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.JobSeekerHandler;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.KeyboardService;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.PaginationService;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.StateManager;
import uz.imaan.jobplatform.telegram.Telegram;
import uz.imaan.jobplatform.telegram.TelegramCallbackService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobSeekerHandlerImpl implements JobSeekerHandler {

    // ===== REPOSITORIES =====
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobVacancyRepository jobVacancyRepository;
    private final BankCardRepository bankCardRepository;
    private final SubscriptionRepository subscriptionRepository;

    // ===== SERVICES =====
    private final JobStore jobStore;
    private final WalletService walletService;
    private final EmployerHandler employerHandler;
    private final ApplicationContext applicationContext;
    private final FavoriteVacancyService favoriteVacancyService;

    // ===== YANGI SERVISLAR =====
    private final StateManager stateManager;
    private final KeyboardService keyboardService;
    private final PaginationService paginationService;

    // ============================================
    // TIL TEKSHIRISH
    // ============================================
    private boolean isRussian(Optional<JobSeekerProfile> profileOpt) {
        return profileOpt.isPresent() && "ru".equals(profileOpt.get().getLanguage());
    }

    private String getText(Optional<JobSeekerProfile> profileOpt, Long chatId, String ru, String uz) {
        if (profileOpt.isPresent()) {
            return "ru".equals(profileOpt.get().getLanguage()) ? ru : uz;
        }
        String tempLang = stateManager.getData(chatId, "tempLanguage");
        return "ru".equals(tempLang) ? ru : uz;
    }

    private String getText(Optional<JobSeekerProfile> profileOpt, Long chatId, String ru, String uz, String en) {
        return getText(profileOpt, chatId, ru, uz);
    }

    // ============================================
    // YORDAMCHI METODLAR
    // ============================================
    private SendMessage createMessage(Long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        if (keyboard != null) message.setReplyMarkup(keyboard);
        return message;
    }

    private SendMessage createHtmlMessage(Long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        if (keyboard != null) message.setReplyMarkup(keyboard);
        return message;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void executeMessage(SendMessage message) {
        try {
            Telegram bot = applicationContext.getBean(Telegram.class);
            bot.execute(message);
        } catch (Exception e) {
            log.error("❌ Xabar yuborishda xatolik: {}", e.getMessage(), e);
        }
    }

    private InlineKeyboardMarkup getLanguageInlineKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder().text("🇺🇿 O'zbek").callbackData("lang_uz").build());
        row.add(InlineKeyboardButton.builder().text("🇷🇺 Русский").callbackData("lang_ru").build());

        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }

    private ReplyKeyboardMarkup getEditProfileKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(isRussian(profileOpt) ? "👤 Ф.И.О" : "👤 Ism-familiya"));
        row1.add(new KeyboardButton(isRussian(profileOpt) ? "📞 Номер телефона" : "📞 Telefon raqam"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(isRussian(profileOpt) ? "💼 Профессия" : "💼 Kasb"));
        row2.add(new KeyboardButton(isRussian(profileOpt) ? "📝 Опыт работы" : "📝 Ish tajribasi"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton(isRussian(profileOpt) ? "⬅️ Назад в профиль" : "⬅️ Profilga qaytish"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        markup.setKeyboard(keyboard);
        return markup;
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) return "Mavjud emas";
        String clean = cardNumber.replaceAll("\\s+", "");
        if (clean.length() < 4) return "Mavjud emas";
        String lastFour = clean.substring(clean.length() - 4);
        return "**** **** **** " + lastFour;
    }

    private boolean isValidLuhn(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) return false;
        String clean = cardNumber.replaceAll("\\s+", "");
        if (clean.length() != 16) return false;

        int sum = 0;
        boolean alternate = false;
        for (int i = clean.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(clean.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) n = (n % 10) + 1;
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    // TELEFON RAQAM FORMATINI TEKSHIRISH
    private boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) return false;
        String clean = phone.replaceAll("[\\s\\-\\(\\)]", "");
        return clean.matches("^(\\+?998)?[0-9]{9}$");
    }

    // TELEFON RAQAMINI YAGONA STANDARTGA (+998XXXXXXXXX) KELTIRISH
    private String formatPhoneNumber(String phone) {
        String clean = phone.replaceAll("[\\s\\-\\(\\)]", "");
        if (clean.startsWith("+998")) return clean;
        if (clean.startsWith("998")) return "+" + clean;
        return "+998" + clean;
    }

    // ============================================
    // ASOSIY HANDLE METODI
    // ============================================

    @Override
    public SendMessage handleJobSeeker(Message message) {
        if (message == null) return null;

        Long chatId = message.getChatId();
        String text = message.hasText() ? message.getText().trim() : "";

        Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
        boolean isRegistered = profileOpt.isPresent();

        JobSeekerState state = stateManager.getState(chatId);

        // ===== /start yoki /menu =====
        if (text.equals("/start") || text.equals("/menu")) {
            if (isRegistered) {
                stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
                return createMessage(chatId,
                        keyboardService.getMainMenuText(profileOpt),
                        keyboardService.getMainMenuKeyboard(profileOpt));
            } else {
                stateManager.setState(chatId, JobSeekerState.WAITING_FOR_LANGUAGE);
                stateManager.putData(chatId, "registration", "true");
                String welcomeMsg = "🌐 **Xush kelibsiz!**\n\nIltimos, tilni tanlang / Пожалуйста, выберите язык:";
                return createMessage(chatId, welcomeMsg, getLanguageInlineKeyboard());
            }
        }

        // ===== BEKOR QILISH VA ORQAGA =====
        if (text.equals("⬅️ Profilga qaytish") || text.equals("⬅️ Назад в профиль")) {
            stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);
            return showProfile(chatId, profileOpt);
        }

        if (text.equals("❌ Bekor qilish") || text.contains("Bekor qilish") ||
                text.equals("Отмена") || text.contains("Отмена")) {

            if (state == JobSeekerState.EDIT_MENU ||
                    state == JobSeekerState.WAITING_FOR_EDIT_NAME ||
                    state == JobSeekerState.WAITING_FOR_EDIT_EXPERIENCE ||
                    state == JobSeekerState.WAITING_FOR_EDIT_PHONE ||
                    state == JobSeekerState.WAITING_FOR_PROFESSION ||
                    state == JobSeekerState.WAITING_FOR_PORTFOLIO ||
                    state == JobSeekerState.WAITING_FOR_PHOTO) {

                stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);
                executeMessage(createHtmlMessage(chatId, "❌ <b>Amal bekor qilindi.</b>", null));
                return showProfile(chatId, profileOpt);
            }

            if (state == JobSeekerState.WAITING_FOR_CARD_NUMBER || state == JobSeekerState.WAITING_FOR_CARD_HOLDER) {
                stateManager.setState(chatId, JobSeekerState.WALLET_MENU);
                return showWallet(chatId, profileOpt);
            }

            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            return createMessage(chatId, keyboardService.getMainMenuText(profileOpt),
                    keyboardService.getMainMenuKeyboard(profileOpt));
        }

        if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад")) {
            if (state == JobSeekerState.EDIT_MENU ||
                    state == JobSeekerState.WAITING_FOR_EDIT_NAME ||
                    state == JobSeekerState.WAITING_FOR_EDIT_EXPERIENCE ||
                    state == JobSeekerState.WAITING_FOR_EDIT_PHONE ||
                    state == JobSeekerState.WAITING_FOR_PROFESSION ||
                    state == JobSeekerState.WAITING_FOR_PORTFOLIO ||
                    state == JobSeekerState.WAITING_FOR_PHOTO) {

                stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);
                return showProfile(chatId, profileOpt);
            }

            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            return createMessage(chatId, keyboardService.getMainMenuText(profileOpt),
                    keyboardService.getMainMenuKeyboard(profileOpt));
        }

        if (text.equals("🏠 Главное меню") || text.equals("Asosiy menyu") || text.equals("🏠 Asosiy menyu")) {
            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            return createMessage(chatId, keyboardService.getMainMenuText(profileOpt),
                    keyboardService.getMainMenuKeyboard(profileOpt));
        }

        // ===== 1. «✏️ TAHRIRLASH» =====
        if (text.equals("✏️ Tahrirlash") || text.contains("Tahrirlash") || text.contains("Редактировать")) {
            stateManager.setState(chatId, JobSeekerState.EDIT_MENU);
            String promptMsg = getText(profileOpt, chatId,
                    "✏️ <b>Какую информацию вы хотите изменить?</b>\nВыберите один из разделов ниже:",
                    "✏️ <b>Qaysi ma'lumotingizni o'zgartirmoqchisiz?</b>\nPastdagi bo'limlardan birini tanlang:"
            );
            SendMessage msg = new SendMessage(chatId.toString(), promptMsg);
            msg.setParseMode("HTML");
            msg.setReplyMarkup(getEditProfileKeyboard(profileOpt));
            return msg;
        }

        // ===== 2. TAHRIRLASH MENYUSIDAGI TANLOVLAR =====
        if (state == JobSeekerState.EDIT_MENU && message.hasText()) {
            if (text.equals("👤 Ism-familiya") || text.contains("Ism") || text.contains("Ф.И.О")) {
                stateManager.setState(chatId, JobSeekerState.WAITING_FOR_EDIT_NAME);
                SendMessage msg = new SendMessage(chatId.toString(), "👤 Yangi ism va familiyangizni kiriting:");
                msg.setParseMode("HTML");
                msg.setReplyMarkup(keyboardService.getCancelKeyboard(profileOpt));
                return msg;
            }

            if (text.equals("💼 Kasb") || text.contains("Kasb") || text.contains("Профессия")) {
                stateManager.setState(chatId, JobSeekerState.WAITING_FOR_PROFESSION);
                SendMessage msg = new SendMessage(chatId.toString(), "💼 Yangi kasbingizni tanlang:");
                msg.setParseMode("HTML");
                msg.setReplyMarkup(keyboardService.getCategoryInlineKeyboard(profileOpt));
                return msg;
            }

            if (text.equals("📝 Ish tajribasi") || text.contains("tajriba") || text.contains("Опыт")) {
                stateManager.setState(chatId, JobSeekerState.WAITING_FOR_EDIT_EXPERIENCE);
                SendMessage msg = new SendMessage(chatId.toString(), "📝 Ish tajribangizni kiriting:");
                msg.setParseMode("HTML");
                msg.setReplyMarkup(keyboardService.getCancelKeyboard(profileOpt));
                return msg;
            }

            if (text.equals("📞 Telefon raqam") || text.contains("Telefon") || text.contains("Номер")) {
                stateManager.setState(chatId, JobSeekerState.WAITING_FOR_EDIT_PHONE);
                SendMessage msg = new SendMessage(chatId.toString(), "📞 Yangi telefon raqamingizni kiriting:");
                msg.setParseMode("HTML");
                msg.setReplyMarkup(keyboardService.getPhoneKeyboard());
                return msg;
            }
        }

        // ===== 3. TAHRIRNI SAQLASH =====
        if (state == JobSeekerState.WAITING_FOR_EDIT_NAME && message.hasText() && profileOpt.isPresent()) {
            JobSeekerProfile profile = profileOpt.get();
            profile.setFullName(text.trim());
            jobSeekerProfileRepository.save(profile);

            stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);
            String okMsg = getText(profileOpt, chatId,
                    "✅ <b>Ваше имя успешно обновлено!</b>",
                    "✅ <b>Ism-familiyangiz muvaffaqiyatli yangilandi!</b>");
            executeMessage(createHtmlMessage(chatId, okMsg, null));
            return showProfile(chatId, Optional.of(profile));
        }

        if (state == JobSeekerState.WAITING_FOR_EDIT_EXPERIENCE && message.hasText() && profileOpt.isPresent()) {
            JobSeekerProfile profile = profileOpt.get();
            profile.setExperience(text.trim());
            jobSeekerProfileRepository.save(profile);

            stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);
            String okMsg = getText(profileOpt, chatId,
                    "✅ <b>Опыт работы успешно обновлен!</b>",
                    "✅ <b>Ish tajribangiz yangilandi!</b>");
            executeMessage(createHtmlMessage(chatId, okMsg, null));
            return showProfile(chatId, Optional.of(profile));
        }

        if (state == JobSeekerState.WAITING_FOR_EDIT_PHONE && profileOpt.isPresent()) {
            String rawPhone = null;
            if (message.hasContact() && message.getContact() != null) {
                rawPhone = message.getContact().getPhoneNumber();
            } else if (message.hasText()) {
                rawPhone = message.getText().trim();
            }

            if (rawPhone == null || !isValidPhoneNumber(rawPhone)) {
                String errPhone = getText(profileOpt, chatId,
                        "❌ **Неверный формат номера телефона!**\n\nВведите номер в формате +998901234567:",
                        "❌ **Telefon raqam formati noto'g'ri!**\n\nIltimos, +998901234567 ko'rinishida yozing yoki tugmadan foydalaning:");
                return createMessage(chatId, errPhone, keyboardService.getPhoneKeyboard());
            }

            JobSeekerProfile profile = profileOpt.get();
            profile.setPhoneNumber(formatPhoneNumber(rawPhone));
            jobSeekerProfileRepository.save(profile);

            stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);
            String okMsg = getText(profileOpt, chatId,
                    "✅ <b>Номер телефона успешно обновлен!</b>",
                    "✅ <b>Telefon raqamingiz yangilandi!</b>");
            executeMessage(createHtmlMessage(chatId, okMsg, null));
            return showProfile(chatId, Optional.of(profile));
        }

        // ===== KASBNI O'ZGARTIRISH =====
        if (state == JobSeekerState.WAITING_FOR_PROFESSION && message.hasText()) {
            return handleProfessionUpdate(chatId, text, profileOpt);
        }

        // ===== KARTA QO'SHISH =====
        if (state == JobSeekerState.WAITING_FOR_CARD_NUMBER) {
            return handleCardNumber(chatId, text, profileOpt);
        }
        if (state == JobSeekerState.WAITING_FOR_CARD_HOLDER) {
            return handleCardHolder(chatId, text, profileOpt);
        }

        // ===== TIL TANLASH (MATNLI VARIANTDA) =====
        if (state == JobSeekerState.WAITING_FOR_LANGUAGE && message.hasText()) {
            return handleLanguageSelection(chatId, text, profileOpt);
        }

        // ===== PORTFOLIO =====
        if (state == JobSeekerState.WAITING_FOR_PORTFOLIO && message.hasText()) {
            return handlePortfolioInput(chatId, text, profileOpt);
        }

        // ===== RASM YUKLASH =====
        if (state == JobSeekerState.WAITING_FOR_PHOTO) {
            boolean isImageDoc = message.hasDocument() &&
                    message.getDocument().getMimeType() != null &&
                    message.getDocument().getMimeType().startsWith("image/");

            if (message.hasPhoto() || isImageDoc) {
                return handleProfilePhotoUpload(chatId, message, profileOpt);
            }
            String msg = getText(profileOpt, chatId,
                    "❌ Пожалуйста, отправьте именно фото!",
                    "❌ Iltimos, faqat rasm yuboring!"
            );
            return createMessage(chatId, msg, keyboardService.getCancelKeyboard(profileOpt));
        }

        // ===== REGISTRATSIYA BOSHLASH =====
        if (text.equals("JobSeeker (Ish izlovchi)") || text.equals("Ish izlovchi (JobSeeker)") ||
                text.equals("JobSeeker")) {
            return handleRegistrationStart(chatId, profileOpt);
        }

        // ===== REGISTRATSIYA: ISM KIRITILGANDA TO'G'RIDAN-TO'G'RI TELEFONGA O'TISH =====
        if (state == JobSeekerState.WAITING_FOR_NAME && message.hasText()) {
            stateManager.putData(chatId, "fullName", text);
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_PHONE);

            String msg = getText(profileOpt, chatId,
                    "📱 **Отправьте свой номер телефона:**",
                    "📱 **Telefon raqamingizni yuboring:**"
            );
            return createMessage(chatId, msg, keyboardService.getPhoneKeyboard());
        }

        if (state == JobSeekerState.WAITING_FOR_PHONE) {
            return handlePhoneInput(chatId, message, profileOpt);
        }

        if (state == JobSeekerState.WAITING_FOR_JOB_TYPE && message.hasText()) {
            return handleJobTypeSelection(chatId, text, profileOpt);
        }

        if (state == JobSeekerState.WAITING_FOR_CATEGORY && message.hasText()) {
            return handleCategorySelection(chatId, text, profileOpt);
        }

        if (state == JobSeekerState.WAITING_FOR_EXPERIENCE && message.hasText()) {
            return handleExperienceInput(chatId, text, profileOpt);
        }

        if (state == JobSeekerState.APPLY_COMMENT && message.hasText()) {
            return handleApplyComment(chatId, text, profileOpt);
        }

        // ===== ASOSIY MENYU TUGMALARI =====
        switch (text) {
            case "🔍 Ish qidirish":
            case "🔍 Поиск работы":
                stateManager.setState(chatId, JobSeekerState.JOB_SEARCH);
                String searchMsg = "📂 <b>Kategoriyalardan birini tanlang</b> yoki qidirayotgan kasbingizni to'g'ridan-to'g'ri yozib yuboring:";
                return createHtmlMessage(chatId, searchMsg, keyboardService.getCategoryInlineKeyboard(profileOpt));

            case "⭐ Saralanganlar":
            case "⭐ Избранное":
            case "Saralanganlar":
                Telegram bot = applicationContext.getBean(Telegram.class);
                TelegramCallbackService callbackService = applicationContext.getBean(TelegramCallbackService.class);
                callbackService.showFavoritesPage(chatId, 0, null, bot);
                return null;

            case "⚡ Faol ishlarim":
            case "⚡ Мои активные работы":
                stateManager.setState(chatId, JobSeekerState.ACTIVE_JOBS);
                String activeMsg = getText(profileOpt, chatId,
                        "⚡ **Раздел моих активных работ:**",
                        "⚡ **Faol ishlarim bo'limi:**"
                );
                return createMessage(chatId, activeMsg, keyboardService.getActiveJobsKeyboard(profileOpt));

            case "👤 Profilim":
            case "👤 Мой профиль":
                stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);
                return showProfile(chatId, profileOpt);

            case "📂 Arizalar":
            case "📂 Мои заявки":
                stateManager.setState(chatId, JobSeekerState.APPLICATIONS);
                return handleShowApplications(chatId, profileOpt);

            case "💳 Hamyon":
            case "💳 Кошелек":
                stateManager.setState(chatId, JobSeekerState.WALLET_MENU);
                return showWallet(chatId, profileOpt);

            case "⚙️ Sozlamalar":
            case "⚙️ Настройки":
                stateManager.setState(chatId, JobSeekerState.SETTINGS_MENU);
                return handleSettingsMenu(chatId, text, profileOpt);
        }

        if (state == JobSeekerState.VIEW_JOB_DETAILS) {
            return handleViewJobDetails(chatId, text, profileOpt);
        }

        if (state == JobSeekerState.PROFILE_MENU) {
            return handleProfileMenu(chatId, text, profileOpt);
        }

        if (state == JobSeekerState.WALLET_MENU) {
            return handleWalletMenu(chatId, text, profileOpt);
        }

        // ===== FAOL ISHLAR =====
        if (state == JobSeekerState.ACTIVE_JOBS) {
            if (text.contains("Joriy ishlar") || text.contains("Текущие работы")) {
                if (profileOpt.isEmpty()) {
                    return createHtmlMessage(chatId, "❌ Profil topilmadi!", keyboardService.getActiveJobsKeyboard(profileOpt));
                }

                Long seekerId = profileOpt.get().getId();

                List<JobApplication> activeJobs = jobApplicationRepository.findByJobSeekerId(seekerId).stream()
                        .filter(app -> app.getStatus() == JobApplication.ApplicationStatus.ACCEPTED)
                        .toList();

                if (activeJobs.isEmpty()) {
                    String msg = getText(profileOpt, chatId,
                            "📋 На данный момент у вас нет активных работ.",
                            "📋 Hozircha qabul qilingan faol ishlaringiz mavjud emas."
                    );
                    return createHtmlMessage(chatId, msg, keyboardService.getActiveJobsKeyboard(profileOpt));
                }

                StringBuilder sb = new StringBuilder();
                sb.append(getText(profileOpt, chatId,
                        "💼 <b>Ваши текущие (активные) работы:</b>\n\n",
                        "💼 <b>Sizning joriy (faol) ishlaringiz:</b>\n\n"
                ));

                List<JobVacancy> allVacancies = jobStore.getAllVacancies();
                int counter = 1;
                for (JobApplication app : activeJobs) {
                    JobVacancy vac = allVacancies.stream()
                            .filter(v -> v.getId().equals(app.getJobId()))
                            .findFirst()
                            .orElse(null);

                    String vacTitle = vac != null ? vac.getTitle() : "Vakansiya #" + app.getJobId();
                    String company = (vac != null && vac.getCompanyName() != null) ? vac.getCompanyName() : "Ko'rsatilmagan";
                    String salary = (vac != null && vac.getSalary() != null) ? vac.getSalary() : "Kelishilgan holda";
                    String phone = (vac != null && vac.getPhoneNumber() != null) ? vac.getPhoneNumber() : "Mavjud emas";

                    sb.append(counter++).append(". 📌 <b>").append(escapeHtml(vacTitle)).append("</b>\n")
                            .append("🏢 <b>Kompaniya:</b> ").append(escapeHtml(company)).append("\n")
                            .append("💰 <b>Maosh:</b> ").append(escapeHtml(salary)).append("\n")
                            .append("📞 <b>Bog'lanish:</b> <code>").append(escapeHtml(phone)).append("</code>\n")
                            .append("✅ <b>Holat:</b> Ishga qabul qilingan\n")
                            .append("───────────────\n");
                }

                return createHtmlMessage(chatId, sb.toString(), keyboardService.getActiveJobsKeyboard(profileOpt));
            }

            if (text.contains("Topshiriqlar") || text.contains("Задания") || text.contains("Vazifalar") || text.contains("Задачи")) {
                String msg = getText(profileOpt, chatId,
                        "📝 У вас пока нет назначенных задач.",
                        "📝 Hozircha sizga berilgan faol vazifalar yoki topshiriqlar mavjud emas."
                );
                return createHtmlMessage(chatId, msg, keyboardService.getActiveJobsKeyboard(profileOpt));
            }
        }

        if (state == JobSeekerState.SETTINGS_MENU) {
            return handleSettingsMenu(chatId, text, profileOpt);
        }

        // Qidiruv
        if (message.hasText() && !text.startsWith("/") && text.length() >= 2) {
            if (state == JobSeekerState.JOB_SEARCH || state == JobSeekerState.MAIN_MENU || state == JobSeekerState.NONE) {
                stateManager.setState(chatId, JobSeekerState.JOB_SEARCH);
                return paginationService.handleSearchPagination(chatId, text.trim(), "all", 0, profileOpt);
            }
        }

        String defaultMsg = getText(profileOpt, chatId,
                "Пожалуйста, выберите один из вариантов.",
                "Iltimos, tugmalardan birini tanlang."
        );
        return createMessage(chatId, defaultMsg, keyboardService.getMainMenuKeyboard(profileOpt));
    }

    // ============================================
    // YORDAMCHI HANDLER METODLARI
    // ============================================
    private SendMessage handleRegistrationStart(Long chatId, Optional<JobSeekerProfile> profileOpt) {
        if (profileOpt.isPresent()) {
            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            return createMessage(chatId, keyboardService.getMainMenuText(profileOpt),
                    keyboardService.getMainMenuKeyboard(profileOpt));
        } else {
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_LANGUAGE);
            stateManager.putData(chatId, "registration", "true");
            String msg = "🌐 **Xush kelibsiz!**\n\nIltimos, tilni tanlang / Пожалуйста, выберите язык:";
            return createMessage(chatId, msg, getLanguageInlineKeyboard());
        }
    }

    // TELEFON RAQAMINI TEKSHIRIB QABUL QILISH
    private SendMessage handlePhoneInput(Long chatId, Message message, Optional<JobSeekerProfile> profileOpt) {
        String rawPhone = null;

        if (message.hasContact() && message.getContact() != null) {
            rawPhone = message.getContact().getPhoneNumber();
        } else if (message.hasText()) {
            rawPhone = message.getText().trim();
        }

        if (rawPhone == null || !isValidPhoneNumber(rawPhone)) {
            String msg = getText(profileOpt, chatId,
                    "❌ **Неверный формат номера телефона!**\n\nПожалуйста, отправьте контакт через кнопку или введите номер вручную (+998901234567):",
                    "❌ **Telefon raqam formati noto'g'ri!**\n\nIltimos, pastdagi tugma orqali yuboring yoki to'g'ri formatda yozing (+998901234567):"
            );
            return createMessage(chatId, msg, keyboardService.getPhoneKeyboard());
        }

        String phone = formatPhoneNumber(rawPhone);
        stateManager.putData(chatId, "phone", phone);

        JobSeekerProfile profile = new JobSeekerProfile();
        profile.setUserId(chatId);
        profile.setFullName(stateManager.getData(chatId, "fullName"));
        profile.setPhoneNumber(phone);

        String tempLang = stateManager.getData(chatId, "tempLanguage");
        profile.setLanguage(tempLang != null ? tempLang : "uz");

        jobSeekerProfileRepository.save(profile);

        stateManager.setState(chatId, JobSeekerState.WAITING_FOR_JOB_TYPE);
        String msg = getText(Optional.of(profile), chatId,
                "🛠 **Какую работу вы ищете?**\n\nПожалуйста, выберите один из вариантов:",
                "🛠 **Qanday turdagi ish qidiryapsiz?**\n\nIltimos, quyidagi tugmalardan birini tanlang:"
        );
        return createMessage(chatId, msg, keyboardService.getJobTypeKeyboard(Optional.of(profile)));
    }

    private SendMessage handleJobTypeSelection(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        String jobType = text;

        if (jobType.equals("🔧 Oddiy ishchi") || jobType.equals("🔧 Обычный рабочий")) {
            JobSeekerProfile profile = profileOpt.orElseThrow(() -> new RuntimeException("Profil topilmadi!"));
            profile.setPreferredJobType("Oddiy ishchi");
            profile.setProfession("Oddiy ishchi");
            jobSeekerProfileRepository.save(profile);

            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            stateManager.clearData(chatId);

            String msg = getText(Optional.of(profile), chatId,
                    "✅ **Вы успешно зарегистрировались!**\n\n🔧 **Тип работы:** Обычный рабочий",
                    "✅ **Muvaffaqiyatli ro'yxatdan o'tdingiz!**\n\n🔧 **Ish turi:** Oddiy ishchi"
            );
            return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(Optional.of(profile)));
        }

        if (jobType.equals("👨‍💻 Kasbim bo'yicha") || jobType.equals("👨‍💻 По профессии")) {
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_CATEGORY);
            String msg = getText(profileOpt, chatId,
                    "📂 **Выберите категорию, соответствующую вашей профессии:**",
                    "📂 **Kasbingizga mos kategoriyani tanlang:**"
            );
            return createMessage(chatId, msg, keyboardService.getCategoryInlineKeyboard(profileOpt));
        }

        return createMessage(chatId,
                getText(profileOpt, chatId,
                        "❌ Пожалуйста, выберите один из вариантов:",
                        "❌ Iltimos, quyidagi tugmalardan birini tanlang:"
                ),
                keyboardService.getJobTypeKeyboard(profileOpt));
    }

    private SendMessage handleCategorySelection(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        String category = text;
        stateManager.putData(chatId, "category", category);
        stateManager.putData(chatId, "profession", category);

        stateManager.setState(chatId, JobSeekerState.WAITING_FOR_EXPERIENCE);
        String msg = getText(profileOpt, chatId,
                "📝 **Расскажите о своем опыте работы:**\n\nСколько времени вы работаете?",
                "📝 **Ish tajribangiz haqida ma'lumot bering:**\n\nQancha vaqtdan beri ishlayapsiz?"
        );
        return createMessage(chatId, msg, keyboardService.getCancelKeyboard(profileOpt));
    }

    private SendMessage handleExperienceInput(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        stateManager.putData(chatId, "experience", text);

        JobSeekerProfile profile = profileOpt.orElseThrow(() -> new RuntimeException("Profil topilmadi!"));
        profile.setExperience(stateManager.getData(chatId, "experience"));
        profile.setProfession(stateManager.getData(chatId, "profession"));
        profile.setCategory(stateManager.getData(chatId, "category"));
        profile.setPreferredJobType("Kasbim bo'yicha");
        jobSeekerProfileRepository.save(profile);

        if (profile.getCategory() != null) {
            if (!subscriptionRepository.existsByUserIdAndCategory(profile.getUserId(), profile.getCategory())) {
                Subscription subscription = Subscription.builder()
                        .userId(profile.getUserId())
                        .category(profile.getCategory())
                        .build();
                subscriptionRepository.save(subscription);
            }
        }

        stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
        stateManager.clearData(chatId);

        String msg = getText(Optional.of(profile), chatId,
                String.format(
                        "✅ **Вы успешно зарегистрировались!**\n\n" +
                                "📂 **Категория:** %s\n" +
                                "💼 **Ваша профессия:** %s\n" +
                                "📝 **Ваш опыт:** %s",
                        profile.getCategory(), profile.getProfession(), profile.getExperience()
                ),
                String.format(
                        "✅ **Muvaffaqiyatli ro'yxatdan o'tdingiz!**\n\n" +
                                "📂 **Kategoriya:** %s\n" +
                                "💼 **Kasbingiz:** %s\n" +
                                "📝 **Tajribangiz:** %s",
                        profile.getCategory(), profile.getProfession(), profile.getExperience()
                )
        );
        return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(Optional.of(profile)));
    }

    private SendMessage handleProfessionUpdate(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        String profession = text;
        JobSeekerProfile profile = profileOpt.orElseThrow(() -> new RuntimeException("Profil topilmadi!"));
        profile.setProfession(profession);
        profile.setCategory(profession);
        profile.setPreferredJobType("Kasbim bo'yicha");
        jobSeekerProfileRepository.save(profile);

        if (profile.getCategory() != null) {
            if (!subscriptionRepository.existsByUserIdAndCategory(profile.getUserId(), profile.getCategory())) {
                Subscription subscription = Subscription.builder()
                        .userId(profile.getUserId())
                        .category(profile.getCategory())
                        .build();
                subscriptionRepository.save(subscription);
            }
        }

        stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);
        String msg = getText(profileOpt, chatId,
                "✅ Профессия успешно обновлена!\n\n💼 Новая профессия: `" + profession + "`",
                "✅ Kasb muvaffaqiyatli yangilandi!\n\n💼 Yangi kasb: `" + profession + "`"
        );
        executeMessage(createMessage(chatId, msg, null));
        return showProfile(chatId, Optional.of(profile));
    }

    private SendMessage handleApplyComment(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        String coverLetterText = text;
        int jobIndex = Integer.parseInt(stateManager.getData(chatId, "selectedJobIndex") != null ?
                stateManager.getData(chatId, "selectedJobIndex") : "0");

        JobSeekerProfile profile = profileOpt.orElseGet(() -> {
            JobSeekerProfile p = new JobSeekerProfile();
            p.setUserId(chatId);
            return jobSeekerProfileRepository.save(p);
        });

        JobApplication application = new JobApplication();
        application.setJobId((long) jobIndex);
        application.setJobSeekerId(profile.getId());
        application.setCoverLetter(coverLetterText);
        application.setStatus(JobApplication.ApplicationStatus.PENDING);
        jobApplicationRepository.save(application);

        List<JobVacancy> allVacancies = jobStore.getAllVacancies();
        if (jobIndex >= 0 && jobIndex < allVacancies.size()) {
            JobVacancy selectedJob = allVacancies.get(jobIndex);
            Long employerChatId = selectedJob.getEmployerChatId();
            if (employerChatId != null) {
                String notifyText = String.format(
                        "📩 **Vakansiyangizga yangi ariza keldi!**\n\n" +
                                "📌 **Vakansiya:** %s\n" +
                                "👤 **Nomzod:** %s\n" +
                                "📞 **Tel:** %s\n" +
                                "📝 **Tajriba:** %s\n" +
                                "🛠 **Ish turi:** %s\n" +
                                "✍️ **Izoh:** %s",
                        selectedJob.getTitle(),
                        profile.getFullName() != null ? profile.getFullName() : "Kiritilmagan",
                        profile.getPhoneNumber() != null ? profile.getPhoneNumber() : "Kiritilmagan",
                        profile.getExperience() != null ? profile.getExperience() : "Kiritilmagan",
                        profile.getPreferredJobType() != null ? profile.getPreferredJobType() : "Kiritilmagan",
                        coverLetterText
                );
                SendMessage notifyMsg = new SendMessage(employerChatId.toString(), notifyText);
                notifyMsg.setParseMode("Markdown");
                try {
                    Telegram bot = applicationContext.getBean(Telegram.class);
                    bot.execute(notifyMsg);
                } catch (Exception e) {
                    log.error("❌ Xatolik: {}", e.getMessage(), e);
                }
            }
        }

        stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
        String msg = getText(profileOpt, chatId,
                "✅ **Заявка отправлена!**",
                "✅ **Ariza yuborildi!**"
        );
        return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
    }

    private SendMessage handleViewJobDetails(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        if (text.startsWith("📌 [")) {
            try {
                int index = Integer.parseInt(text.substring(text.indexOf("[") + 1, text.indexOf("]")));
                List<JobVacancy> allVacancies = jobStore.getAllVacancies();
                if (index >= 0 && index < allVacancies.size()) {
                    JobVacancy selectedVacancy = allVacancies.get(index);
                    stateManager.putData(chatId, "selectedJobIndex", String.valueOf(index));
                    String detail = String.format(
                            "📄 **Ish ma'lumotlari:**\n\n" +
                                    "🔹 **Nomi:** %s\n" +
                                    "📂 **Kategoriya:** %s\n" +
                                    "⏱ **Bandlik turi:** %s\n" +
                                    "💰 **Maosh:** %s",
                            selectedVacancy.getTitle(),
                            selectedVacancy.getCategory() != null ? selectedVacancy.getCategory() : "Ko'rsatilmagan",
                            selectedVacancy.getType() != null ? selectedVacancy.getType() : "To'liq kun",
                            selectedVacancy.getSalary() != null ? selectedVacancy.getSalary() : "Kelishilgan holda"
                    );
                    return createMessage(chatId, detail, keyboardService.getJobActionKeyboard(profileOpt));
                }
            } catch (Exception e) {
                String msg = getText(profileOpt, chatId,
                        "Ошибка при загрузке информации о вакансии.",
                        "Vakansiya ma'lumotlarini yuklashda xatolik."
                );
                return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
            }
        }

        if (text.equals("📝 Ariza berish") || text.equals("📝 Подать заявку")) {
            stateManager.setState(chatId, JobSeekerState.APPLY_COMMENT);
            String msg = getText(profileOpt, chatId,
                    "✍️ **Напишите сопроводительное письмо:**\n\nКратко расскажите о себе и своем опыте:",
                    "✍️ **Cover letter (Izoh) yozing:**\n\nO'zingiz va tajribangiz haqida qisqacha ma'lumot qoldiring:"
            );
            return createMessage(chatId, msg, keyboardService.getCancelKeyboard(profileOpt));
        }
        return null;
    }

    // ============================================
    // PROFIL METODLARI
    // ============================================

    @Override
    public SendMessage showProfile(Long chatId, Optional<JobSeekerProfile> profileOpt) {
        if (profileOpt.isEmpty()) {
            String msg = getText(profileOpt, chatId,
                    "❌ Вы еще не зарегистрированы!",
                    "❌ Siz hali ro'yxatdan o'tmagansiz!"
            );
            return createHtmlMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
        }

        JobSeekerProfile profile = profileOpt.get();
        String profileInfoText = getProfileInfo(profile, profileOpt);

        if (profile.getPhotoFileId() != null && !profile.getPhotoFileId().isBlank()) {
            try {
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId.toString());
                sendPhoto.setPhoto(new InputFile(profile.getPhotoFileId()));
                sendPhoto.setCaption(profileInfoText);
                sendPhoto.setParseMode("HTML");
                sendPhoto.setReplyMarkup(keyboardService.getProfileKeyboard(profileOpt));

                Telegram bot = applicationContext.getBean(Telegram.class);
                bot.execute(sendPhoto);
                return null;
            } catch (Exception e) {
                log.warn("⚠️ Profil rasmini yuborishda xatolik: {}", e.getMessage());
            }
        }

        return createHtmlMessage(chatId, profileInfoText, keyboardService.getProfileKeyboard(profileOpt));
    }

    @Override
    public SendMessage handleProfileMenu(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        JobSeekerProfile profile = profileOpt.orElse(new JobSeekerProfile());
        double rating = profile.getRating() != null ? profile.getRating() : 0.0;

        if (text.contains("Ma'lumotlar") || text.contains("Информация")) {
            return showProfile(chatId, profileOpt);
        }
        if (text.contains("Portfolio") || text.contains("Портфолио")) {
            return showPortfolio(chatId, profileOpt);
        }

        if (text.contains("Reyting") || text.contains("Рейтинг")) {
            int count = profile.getReviewCount() != null ? profile.getReviewCount() : 0;
            String msg = getText(profileOpt, chatId,
                    String.format("⭐ **Ваш текущий рейтинг:** %.1f / 5.0 (%d оценок)", rating, count),
                    String.format("⭐ **Sizning joriy reytingingiz:** %.1f / 5.0 (%d ta baho)", rating, count)
            );
            return createMessage(chatId, msg, keyboardService.getProfileKeyboard(profileOpt));
        }

        if (text.contains("Rasm") || text.contains("Фото")) {
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_PHOTO);
            String msg = getText(profileOpt, chatId,
                    "🖼 **Фото профиля:**\n\nОтправьте боту фото для обновления аватара:",
                    "🖼 **Profil rasmi:**\n\nProfil rasmingizni yangilash uchun botga rasm yuboring:"
            );
            return createMessage(chatId, msg, keyboardService.getCancelKeyboard(profileOpt));
        }

        if (text.contains("Tahrirlash") || text.contains("Редактировать")) {
            stateManager.setState(chatId, JobSeekerState.EDIT_MENU);
            SendMessage msg = new SendMessage(chatId.toString(), "✏️ <b>Qaysi ma'lumotingizni o'zgartirmoqchisiz?</b>\nPastdagi bo'limlardan birini tanlang:");
            msg.setParseMode("HTML");
            msg.setReplyMarkup(getEditProfileKeyboard(profileOpt));
            return msg;
        }

        if (text.contains("Kasb") || text.contains("Профессия")) {
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_PROFESSION);
            String msg = getText(profileOpt, chatId,
                    "📂 **Выберите новую профессию из списка ниже:**",
                    "📂 **Pastdagi ro'yxatdan yangi kasbni tanlang:**"
            );
            SendMessage sendMsg = new SendMessage(chatId.toString(), msg);
            sendMsg.setParseMode("Markdown");
            sendMsg.setReplyMarkup(keyboardService.getCategoryInlineKeyboard(profileOpt));
            return sendMsg;
        }

        if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад")) {
            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            return createMessage(chatId, keyboardService.getMainMenuText(profileOpt), keyboardService.getMainMenuKeyboard(profileOpt));
        }

        String msg = getText(profileOpt, chatId, "👤 **Меню профиля**", "👤 **Profil menyusi**");
        return createMessage(chatId, msg, keyboardService.getProfileKeyboard(profileOpt));
    }

    private SendMessage handleProfilePhotoUpload(Long chatId, Message message, Optional<JobSeekerProfile> profileOpt) {
        String fileId = null;

        if (message.hasPhoto()) {
            List<PhotoSize> photos = message.getPhoto();
            fileId = photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .map(PhotoSize::getFileId)
                    .orElse(photos.get(photos.size() - 1).getFileId());
        } else if (message.hasDocument()) {
            String mime = message.getDocument().getMimeType();
            if (mime != null && mime.startsWith("image/")) {
                fileId = message.getDocument().getFileId();
            }
        }

        if (fileId == null) {
            String errorMsg = getText(profileOpt, chatId,
                    "❌ Не удалось загрузить фото. Пожалуйста, отправьте изображение!",
                    "❌ Rasmni yuklab bo'lmadi. Iltimos, haqiqiy rasm faylini yuboring!"
            );
            return createMessage(chatId, errorMsg, keyboardService.getCancelKeyboard(profileOpt));
        }

        JobSeekerProfile profile = profileOpt.orElseGet(() -> {
            JobSeekerProfile p = new JobSeekerProfile();
            p.setUserId(chatId);
            return p;
        });

        profile.setPhotoFileId(fileId);
        jobSeekerProfileRepository.save(profile);

        stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);

        String msg = getText(profileOpt, chatId,
                "✅ Фото профиля успешно сохранено!",
                "✅ Profil rasmi muvaffaqiyatli saqlandi!"
        );
        executeMessage(createMessage(chatId, msg, null));
        return showProfile(chatId, Optional.of(profile));
    }

    @Override
    public String getProfileInfo(JobSeekerProfile profile, Optional<JobSeekerProfile> profileOpt) {
        String jobTypeDisplay;
        String preferred = profile.getPreferredJobType();

        if (preferred != null && !preferred.isBlank()) {
            if ("PROFESSIONAL".equalsIgnoreCase(preferred) || "Kasbim bo'yicha".equalsIgnoreCase(preferred) || "По профессии".equalsIgnoreCase(preferred)) {
                jobTypeDisplay = (profile.getProfession() != null && !profile.getProfession().isBlank())
                        ? "👨‍💻 " + profile.getProfession()
                        : (isRussian(profileOpt) ? "👨‍💻 По специальности" : "👨‍💻 Mutaxassislik bo'yicha");
            } else {
                jobTypeDisplay = preferred;
            }
        } else if (profile.getProfession() != null && !profile.getProfession().isBlank()) {
            jobTypeDisplay = "👨‍💻 " + profile.getProfession();
        } else {
            jobTypeDisplay = isRussian(profileOpt) ? "Не указан" : "Ko'rsatilmagan";
        }

        String cardDisplay = "❌ Karta mavjud emas";
        try {
            if (profile.getBankCards() != null && !profile.getBankCards().isEmpty()) {
                BankCard activeCard = profile.getBankCards().stream()
                        .filter(card -> card.getIsActive() != null && card.getIsActive())
                        .findFirst().orElse(profile.getBankCards().get(0));
                String cardNumber = activeCard.getCardNumber();
                if (cardNumber != null && !cardNumber.isEmpty()) {
                    cardDisplay = maskCardNumber(cardNumber);
                }
                if (profile.getBankCards().size() > 1) {
                    cardDisplay += " (+" + (profile.getBankCards().size() - 1) + " ta karta)";
                }
            }
        } catch (Exception e) {
            cardDisplay = "❌ Karta ma'lumoti olinmadi";
        }

        int count = profile.getReviewCount() != null ? profile.getReviewCount() : 0;
        double currentRating = profile.getRating() != null ? profile.getRating() : 0.0;
        String ratingDisplayUz = count > 0 ? String.format("%.1f (%d ta baho)", currentRating, count) : "0.0 (Hali baholanmagan)";
        String ratingDisplayRu = count > 0 ? String.format("%.1f (%d оценок)", currentRating, count) : "0.0 (Еще нет оценок)";

        if (isRussian(profileOpt)) {
            return String.format(
                    "👤 **Информация профиля:**\n\n" +
                            "📌 **Ф.И.О:** %s\n" +
                            "📞 **Телефон:** %s\n" +
                            "⭐ **Рейтинг:** %s\n" +
                            "💼 **Профессия:** %s\n" +
                            "📝 **Опыт:** %s\n" +
                            "🛠 **Тип работы:** %s\n" +
                            "💳 **Карта:** %s",
                    profile.getFullName() != null ? profile.getFullName() : "Не указано",
                    profile.getPhoneNumber() != null ? profile.getPhoneNumber() : "Не указан",
                    ratingDisplayRu,
                    profile.getProfession() != null ? profile.getProfession() : "Не указана",
                    profile.getExperience() != null ? profile.getExperience() : "Не указан",
                    jobTypeDisplay,
                    cardDisplay
            );
        }
        return String.format(
                "👤 **Profil ma'lumotlari:**\n\n" +
                        "📌 **F.I.O:** %s\n" +
                        "📞 **Tel:** %s\n" +
                        "⭐ **Reyting:** %s\n" +
                        "💼 **Kasb:** %s\n" +
                        "📝 **Tajriba:** %s\n" +
                        "🛠 **Ish turi:** %s\n" +
                        "💳 **Karta:** %s",
                profile.getFullName() != null ? profile.getFullName() : "Kiritilmagan",
                profile.getPhoneNumber() != null ? profile.getPhoneNumber() : "Kiritilmagan",
                ratingDisplayUz,
                profile.getProfession() != null ? profile.getProfession() : "Ko'rsatilmagan",
                profile.getExperience() != null ? profile.getExperience() : "Ko'rsatilmagan",
                jobTypeDisplay,
                cardDisplay
        );
    }

    // ============================================
    // PORTFOLIO METODLARI
    // ============================================
    @Override
    public SendMessage showPortfolio(Long chatId, Optional<JobSeekerProfile> profileOpt) {
        stateManager.setState(chatId, JobSeekerState.WAITING_FOR_PORTFOLIO);

        String currentPortfolio = profileOpt.map(JobSeekerProfile::getPortfolio).orElse(null);
        StringBuilder msg = new StringBuilder();

        if (currentPortfolio != null && !currentPortfolio.isEmpty()) {
            msg.append(getText(profileOpt, chatId,
                            "📁 **Ваше текущее портфолио:**\n\n",
                            "📁 **Sizning joriy portfoliongiz:**\n\n"))
                    .append(currentPortfolio)
                    .append("\n\n")
                    .append(getText(profileOpt, chatId,
                            "✏️ Отправьте новую ссылку или текст для обновления:",
                            "✏️ Yangilash uchun yangi havola yoki matn yuboring:"));
        } else {
            msg.append(getText(profileOpt, chatId,
                    "📁 Отправьте ссылку на ваше портфолио (GitHub, резюме и т.д.):",
                    "📁 Portfoliongizni kiriting (GitHub havola, loyihalar haqida matn yoki rezyume linki):"));
        }

        return createMessage(chatId, msg.toString(), keyboardService.getCancelKeyboard(profileOpt));
    }

    @Override
    public SendMessage handlePortfolioInput(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        JobSeekerProfile profile = profileOpt.orElseGet(() -> {
            JobSeekerProfile p = new JobSeekerProfile();
            p.setUserId(chatId);
            return p;
        });

        profile.setPortfolio(text);
        jobSeekerProfileRepository.save(profile);

        stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);

        String msg = getText(profileOpt, chatId,
                "✅ Портфолио успешно сохранено!",
                "✅ Portfolio muvaffaqiyatli saqlandi!"
        );
        executeMessage(createMessage(chatId, msg, null));
        return showProfile(chatId, Optional.of(profile));
    }

    @Override
    public String getProfileInfo(Long chatId, Optional<JobSeekerProfile> profileOpt) {
        if (profileOpt.isEmpty()) return "Profil topilmadi / Профиль не найден";

        JobSeekerProfile profile = profileOpt.get();

        String jobTypeDisplay;
        String preferred = profile.getPreferredJobType();
        if (preferred != null && !preferred.isBlank()) {
            if ("PROFESSIONAL".equalsIgnoreCase(preferred) || "Kasbim bo'yicha".equalsIgnoreCase(preferred) || "По профессии".equalsIgnoreCase(preferred)) {
                jobTypeDisplay = profile.getProfession() != null ? "👨‍💻 " + profile.getProfession() : "👨‍💻 Kasbim bo'yicha";
            } else {
                jobTypeDisplay = preferred;
            }
        } else if (profile.getProfession() != null && !profile.getProfession().isBlank()) {
            jobTypeDisplay = "👨‍💻 " + profile.getProfession();
        } else {
            jobTypeDisplay = isRussian(profileOpt) ? "Не указан" : "Ko'rsatilmagan";
        }

        String cardDisplay = bankCardRepository.findFirstByJobSeekerId(chatId)
                .map(card -> maskCardNumber(card.getCardNumber()))
                .orElse(getText(profileOpt, chatId, "Не указана", "Kiritilmagan"));

        return String.format(
                "%s\n\n" +
                        "👤 **%s:** %s\n" +
                        "📞 **%s:** %s\n" +
                        "💼 **%s:** %s\n" +
                        "🔧 **%s:** %s\n" +
                        "💳 **%s:** %s\n" +
                        "⭐ **%s:** %.1f",
                getText(profileOpt, chatId, "👤 **Ваш профиль:**", "👤 **Sizning profilingiz:**"),
                getText(profileOpt, chatId, "Ф.И.О", "F.I.O"),
                profile.getFullName(),
                getText(profileOpt, chatId, "Тел", "Tel"),
                profile.getPhoneNumber(),
                getText(profileOpt, chatId, "Профессия", "Kasb"),
                profile.getProfession() != null ? profile.getProfession() : "-",
                getText(profileOpt, chatId, "Тип работы", "Ish turi"),
                jobTypeDisplay,
                getText(profileOpt, chatId, "Карта", "Karta"),
                cardDisplay,
                getText(profileOpt, chatId, "Рейтинг", "Reyting"),
                profile.getRating()
        );
    }

    // ============================================
    // HAMYON METODLARI
    // ============================================
    @Override
    public SendMessage handleWalletMenu(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        if (text.contains("Bank kartasi qo'shish") || text.contains("💳 Bank kartasi") ||
                text.contains("Добавить банковскую карту") || text.contains("💳 Банковская карта")) {

            if (profileOpt.isPresent()) {
                List<BankCard> bankCards = profileOpt.get().getBankCards();
                if (bankCards != null && !bankCards.isEmpty()) {
                    String msg = getText(profileOpt, chatId,
                            "⚠️ У вас уже есть карта! Сначала удалите текущую карту.",
                            "⚠️ Sizda allaqachon karta mavjud! Avval kartani o'chiring."
                    );
                    return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt, true));
                }
            }

            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_CARD_NUMBER);
            String msg = getText(profileOpt, chatId,
                    "💳 Введите номер вашей карты (16 цифр):",
                    "💳 Karta raqamingizni kiriting (16 xona):"
            );
            return createMessage(chatId, msg, keyboardService.getCancelKeyboard(profileOpt));
        }

        if (text.contains("Kartani o'chirish") || text.contains("🗑️ Kartani o'chirish") ||
                text.contains("Удалить карту") || text.contains("🗑️ Удалить карту")) {
            return handleDeleteCard(chatId, profileOpt);
        }

        if (text.contains("Hisob balansi") || text.contains("💰 Hisob balansi") ||
                text.contains("Баланс") || text.contains("💰 Баланс")) {
            return showWallet(chatId, profileOpt);
        }

        if (text.contains("Pul yechish") || text.contains("💸 Pul yechish") ||
                text.contains("Снять деньги") || text.contains("💸 Снять деньги")) {
            String msg = getText(profileOpt, chatId,
                    "⚠️ Минимальная сумма для снятия: 50,000 сум.",
                    "⚠️ Pul yechish uchun minimal summa: 50,000 so'm."
            );
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt));
        }

        if (text.contains("To'lov usullari") || text.contains("💳 To'lov usullari") ||
                text.contains("Способы оплаты") || text.contains("💳 Способы оплаты")) {
            String msg = getText(profileOpt, chatId,
                    "💳 **Доступные способы оплаты:**\n\n- Click\n- Payme\n- Uzum Bank",
                    "💳 **Mavjud to'lov usullari:**\n\n- Click\n- Payme\n- Uzum Bank"
            );
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt));
        }

        if (text.contains("To'lov tarixi") || text.contains("📜 To'lov tarixi") ||
                text.contains("История платежей") || text.contains("📜 История платежей")) {
            String msg = getText(profileOpt, chatId,
                    "📜 **История платежей:**\n\nНа данный момент операций нет.",
                    "📜 **To'lovlar tarixi:**\n\nHozircha amaliyotlar mavjud emas."
            );
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt));
        }

        return null;
    }

    @Override
    public SendMessage handleDeleteCard(Long chatId, Optional<JobSeekerProfile> profileOpt) {
        try {
            if (profileOpt.isEmpty()) {
                String msg = getText(profileOpt, chatId,
                        "❌ Вы еще не зарегистрированы!",
                        "❌ Siz hali ro'yxatdan o'tmagansiz!"
                );
                return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
            }

            JobSeekerProfile profile = profileOpt.get();
            List<BankCard> bankCards = profile.getBankCards();
            if (bankCards == null || bankCards.isEmpty()) {
                String msg = getText(profileOpt, chatId,
                        "❌ У вас нет сохраненных карт.",
                        "❌ Sizda saqlangan karta mavjud emas."
                );
                return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt, false));
            }

            BankCard cardToDelete = bankCards.get(0);
            String maskedCard = maskCardNumber(cardToDelete.getCardNumber());

            bankCards.remove(cardToDelete);
            profile.setBankCards(bankCards);
            jobSeekerProfileRepository.save(profile);

            String msg = getText(profileOpt, chatId,
                    "✅ Карта успешно удалена! (" + maskedCard + ")",
                    "✅ Karta muvaffaqiyatli o'chirildi! (" + maskedCard + ")"
            );
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt, false));
        } catch (Exception e) {
            log.error("❌ Kartani o'chirishda xatolik: {}", e.getMessage(), e);
            String msg = getText(profileOpt, chatId,
                    "❌ Ошибка при удалении карты: " + e.getMessage(),
                    "❌ Kartani o'chirishda xatolik: " + e.getMessage()
            );
            return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
        }
    }

    @Override
    public SendMessage showWallet(Long chatId, Optional<JobSeekerProfile> profileOpt) {
        try {
            Optional<JobSeekerProfile> profile = jobSeekerProfileRepository.findByUserId(chatId);
            if (profile.isEmpty()) {
                String msg = getText(profileOpt, chatId,
                        "❌ Вы еще не зарегистрированы!",
                        "❌ Siz hali ro'yxatdan o'tmagansiz!"
                );
                return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
            }

            JobSeekerProfile p = profile.get();
            String balance = p.getWalletBalance() != null ? p.getWalletBalance().toString() : "0";

            String cardNumberDisplay = "❌ Karta mavjud emas";
            String cardHolderDisplay = "";
            List<BankCard> bankCards = p.getBankCards();

            if (bankCards != null && !bankCards.isEmpty()) {
                BankCard activeCard = bankCards.stream()
                        .filter(card -> card.getIsActive() != null && card.getIsActive())
                        .findFirst().orElse(bankCards.get(0));

                String cardNumber = activeCard.getCardNumber();
                if (cardNumber != null && !cardNumber.isEmpty()) {
                    cardNumberDisplay = maskCardNumber(cardNumber);
                }

                String cardHolderName = activeCard.getCardHolderName();
                if (cardHolderName != null && !cardHolderName.isEmpty()) {
                    cardHolderDisplay = "👤 **Karta egasi:** " + cardHolderName;
                }

                if (bankCards.size() > 1) {
                    cardNumberDisplay += " (+" + (bankCards.size() - 1) + " ta karta)";
                }
            }

            String msg = getText(profileOpt, chatId,
                    "💳 **Кошелек и платежи:**\n\n" +
                            "💰 **Баланс:** " + balance + " сум\n" +
                            "💳 **Карта:** " + cardNumberDisplay + "\n" +
                            (cardHolderDisplay.isEmpty() ? "" : cardHolderDisplay + "\n") +
                            "\n📌 Используйте кнопки ниже:",
                    "💳 **Hamyon va To'lovlar:**\n\n" +
                            "💰 **Hisob balansi:** " + balance + " so'm\n" +
                            "💳 **Karta:** " + cardNumberDisplay + "\n" +
                            (cardHolderDisplay.isEmpty() ? "" : cardHolderDisplay + "\n") +
                            "\n📌 Quyidagi tugmalardan foydalaning:"
            );

            boolean hasCard = bankCards != null && !bankCards.isEmpty();
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt, hasCard));
        } catch (Exception e) {
            log.error("❌ Hamyonni ko'rsatishda xatolik: {}", e.getMessage(), e);
            String msg = getText(profileOpt, chatId,
                    "❌ Произошла ошибка!",
                    "❌ Xatolik yuz berdi!"
            );
            return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
        }
    }

    // ============================================
    // ARIZALAR METODLARI
    // ============================================
    @Override
    public SendMessage handleShowApplications(Long chatId, Optional<JobSeekerProfile> profileOpt) {
        if (profileOpt.isEmpty()) {
            String msg = getText(profileOpt, chatId,
                    "📂 Чтобы увидеть свои заявки, сначала зарегистрируйтесь.",
                    "📂 Arizalaringizni ko'rish uchun avval ro'yxatdan o'ting."
            );
            return createHtmlMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
        }

        Long jobSeekerId = profileOpt.get().getId();
        List<JobApplication> myApps = jobApplicationRepository.findByJobSeekerId(jobSeekerId);

        if (myApps == null || myApps.isEmpty()) {
            String msg = getText(profileOpt, chatId,
                    "📂 Вы еще не подавали заявок на вакансии.",
                    "📂 Siz hali hech qanday vakansiyaga ariza topshirmagansiz."
            );
            return createHtmlMessage(chatId, msg, keyboardService.getSubBackKeyboard(profileOpt));
        }

        String title = getText(profileOpt, chatId,
                "📋 <b>Ваши заявки:</b>\n\n",
                "📋 <b>Siz yuborgan arizalar:</b>\n\n"
        );
        StringBuilder sb = new StringBuilder(title);
        List<JobVacancy> allVacancies = jobStore.getAllVacancies();

        for (JobApplication app : myApps) {
            String jobTitle = allVacancies.stream()
                    .filter(v -> v.getId().equals(app.getJobId()))
                    .map(JobVacancy::getTitle)
                    .findFirst().orElse("❌ O'chirilgan vakansiya (ID: " + app.getJobId() + ")");

            String statusStr;
            if (isRussian(profileOpt)) {
                statusStr = switch (app.getStatus()) {
                    case PENDING -> "⏳ Ожидание (Работодатель еще не открыл)";
                    case VIEWED -> "👁 Просмотрено (Ожидается ответ)";
                    case INTERVIEW -> "📅 Приглашение на собеседование!";
                    case ACCEPTED -> "✅ Принято на работу!";
                    case REJECTED -> "❌ Отклонено";
                    case CANCELLED -> "🚫 Отменено";
                };
            } else {
                statusStr = switch (app.getStatus()) {
                    case PENDING -> "⏳ Kutilmoqda (Ish beruvchi hali ochmadi)";
                    case VIEWED -> "👁 Ko'rib chiqildi (Javob kutilmoqda)";
                    case INTERVIEW -> "📅 Suhbatga chaqirildingiz!";
                    case ACCEPTED -> "✅ Ishga qabul qilindingiz!";
                    case REJECTED -> "❌ Rad etildi";
                    case CANCELLED -> "🚫 Bekor qilingan";
                };
            }

            sb.append("📌 <b>Vakansiya:</b> ").append(escapeHtml(jobTitle)).append("\n")
                    .append("✍️ <b>Cover letter:</b> ").append(escapeHtml(app.getCoverLetter() != null ? app.getCoverLetter() : "Yo'q")).append("\n")
                    .append("📊 <b>Holat:</b> ").append(statusStr).append("\n")
                    .append("───────────────\n");
        }

        return createHtmlMessage(chatId, sb.toString(), keyboardService.getSubBackKeyboard(profileOpt));
    }

    // ============================================
    // SOZLAMALAR METODLARI
    // ============================================
    @Override
    public SendMessage handleSettingsMenu(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        JobSeekerState currentState = stateManager.getState(chatId);

        if (text.equals("🌐 Til") || text.equals("🌐 Язык") || text.contains("Til") || text.contains("Язык")) {
            stateManager.setPreviousState(chatId, currentState);
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_LANGUAGE);
            String msg = getText(profileOpt, chatId,
                    "🌐 **Выберите язык / Tilni tanlang:**",
                    "🌐 **Tilni tanlang / Выберите язык:**"
            );
            return createMessage(chatId, msg, getLanguageInlineKeyboard());
        }

        if (text.equals("🔒 Maxfiylik") || text.equals("🔒 Конфиденциальность") || text.contains("Maxfiylik") || text.contains("Конфиденциальность")) {
            String msg = getText(profileOpt, chatId,
                    "🔒 **Настройки конфиденциальности:**\n\nВаши данные надежно защищены.",
                    "🔒 **Maxfiylik sozlamalari:**\n\nSizning ma'lumotlaringiz xavfsiz saqlanadi."
            );
            return createMessage(chatId, msg, keyboardService.getSettingsKeyboard(profileOpt));
        }

        if (text.equals("🔔 Bildirishnoma") || text.equals("🔔 Уведомления") || text.contains("Bildirishnoma") || text.contains("Уведомления")) {
            String msg = getText(profileOpt, chatId,
                    "🔔 **Уведомления:** Включены ✅",
                    "🔔 **Bildirishnomalar:** Yoniq ✅"
            );
            return createMessage(chatId, msg, keyboardService.getSettingsKeyboard(profileOpt));
        }

        if (text.equals("❓ Yordam") || text.equals("❓ Помощь") || text.contains("Yordam") || text.contains("Помощь")) {
            String msg = getText(profileOpt, chatId,
                    "❓ **Центр помощи:**\n\nВ случае возникновения проблем свяжитесь с администратором.",
                    "❓ **Yordam markazi:**\n\nMuammo yuzaga kelsa, admin bilan bog'laning."
            );
            return createMessage(chatId, msg, keyboardService.getSettingsKeyboard(profileOpt));
        }

        if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад")) {
            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            return createMessage(chatId, keyboardService.getMainMenuText(profileOpt),
                    keyboardService.getMainMenuKeyboard(profileOpt));
        }

        String msg = getText(profileOpt, chatId,
                "⚙️ **Раздел настроек:**",
                "⚙️ **Sozlamalar bo'limi:**"
        );
        return createMessage(chatId, msg, keyboardService.getSettingsKeyboard(profileOpt));
    }

    // ============================================
    // TIL TANLASH (Matn orqali kelganda)
    // ============================================
    @Override
    public SendMessage handleLanguageSelection(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        String lang = null;
        if (text.equals("🇺🇿 O'zbek") || text.contains("O'zbek")) {
            lang = "uz";
        } else if (text.equals("🇷🇺 Русский") || text.contains("Русский")) {
            lang = "ru";
        } else {
            String msg = getText(profileOpt, chatId,
                    "❌ Пожалуйста, выберите язык:",
                    "❌ Iltimos, tilni tanlang:"
            );
            return createMessage(chatId, msg, getLanguageInlineKeyboard());
        }

        if (profileOpt.isPresent()) {
            profileOpt.get().setLanguage(lang);
            jobSeekerProfileRepository.save(profileOpt.get());
            profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
        } else {
            stateManager.putData(chatId, "tempLanguage", lang);
        }

        if (stateManager.getData(chatId, "registration") != null &&
                "true".equals(stateManager.getData(chatId, "registration"))) {
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_NAME);
            stateManager.removeData(chatId, "registration");
            String msg = "ru".equals(lang)
                    ? "👤 **Регистрация в качестве соискателя:**\n\nВведите ваше имя и фамилию:"
                    : "👤 **Ish izlovchi sifatida ro'yxatdan o'tish:**\n\nIltimos, ism va familiyangizni kiriting:";
            return createMessage(chatId, msg, (ReplyKeyboard) null);
        }

        JobSeekerState previous = stateManager.getPreviousState(chatId);
        stateManager.removePreviousState(chatId);

        if (previous != null) {
            stateManager.setState(chatId, previous);
            if (previous == JobSeekerState.SETTINGS_MENU) {
                String msg = getText(profileOpt, chatId,
                        "⚙️ **Раздел настроек:**",
                        "⚙️ **Sozlamalar bo'limi:**"
                );
                return createMessage(chatId, msg, keyboardService.getSettingsKeyboard(profileOpt));
            } else {
                return createMessage(chatId, keyboardService.getMainMenuText(profileOpt),
                        keyboardService.getMainMenuKeyboard(profileOpt));
            }
        } else {
            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            String msg = getText(profileOpt, chatId,
                    "✅ Язык изменен на Русский!",
                    "✅ Til O'zbek tiliga o'zgartirildi!"
            );
            return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
        }
    }

    // ============================================
    // KARTA QO'SHISH
    // ============================================
    @Override
    public SendMessage handleCardNumber(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        String cardNumber = text.replaceAll("\\s+", "");

        if (!isValidLuhn(cardNumber)) {
            String errorMsg = getText(profileOpt, chatId,
                    "❌ Неверный номер карты! Пожалуйста, введите правильный 16-значный номер карты:",
                    "❌ Karta raqami xato kiritildi! Iltimos, 16 xonali haqiqiy karta raqamini qaytadan kiriting:"
            );
            return createMessage(chatId, errorMsg, keyboardService.getCancelKeyboard(profileOpt));
        }

        stateManager.putData(chatId, "cardNumber", cardNumber);
        stateManager.setState(chatId, JobSeekerState.WAITING_FOR_CARD_HOLDER);

        String successMsg = getText(profileOpt, chatId,
                "👤 Введите имя владельца карты:\nНапример: ALI VALIYEV",
                "👤 Karta egasi ismini kiriting:\nMasalan: ALI VALIYEV"
        );
        return createMessage(chatId, successMsg, keyboardService.getCancelKeyboard(profileOpt));
    }

    @Override
    public SendMessage handleCardHolder(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        if (text.length() < 3) {
            return createMessage(chatId, "❌ Karta egasi ismi juda qisqa! Qaytadan kiriting:",
                    keyboardService.getCancelKeyboard(profileOpt));
        }

        if (profileOpt.isEmpty()) {
            String msg = getText(profileOpt, chatId,
                    "❌ Вы еще не зарегистрированы! Нажмите /start",
                    "❌ Siz hali ro'yxatdan o'tmagansiz! Iltimos, /start bosing."
            );
            return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
        }

        String cardNumber = stateManager.getData(chatId, "cardNumber");
        if (cardNumber == null || cardNumber.isEmpty()) {
            return createMessage(chatId, "Karta raqami topilmadi. Iltimos, /start dan qayta boshlang.",
                    keyboardService.getMainMenuKeyboard(profileOpt));
        }

        try {
            BankCardRequest request = new BankCardRequest(cardNumber, text.toUpperCase());
            walletService.addBankCard(chatId, request);

            stateManager.setState(chatId, JobSeekerState.WALLET_MENU);
            stateManager.removeData(chatId, "cardNumber");

            String maskedCard = maskCardNumber(cardNumber);
            String msg = getText(profileOpt, chatId,
                    "✅ Karta muvaffaqiyatli qo'shildi!\n\n💳 Karta: " + maskedCard + "\n👤 Egasi: " + text.toUpperCase(),
                    "✅ Karta muvaffaqiyatli qo'shildi!\n\n💳 Karta: " + maskedCard + "\n👤 Egasi: " + text.toUpperCase()
            );
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt));
        } catch (Exception e) {
            log.error("❌ Karta qo'shishda xatolik: {}", e.getMessage(), e);
            stateManager.setState(chatId, JobSeekerState.WALLET_MENU);
            String msg = getText(profileOpt, chatId,
                    "❌ Ошибка при добавлении карты: " + e.getMessage(),
                    "❌ Karta qo'shishda xatolik: " + e.getMessage()
            );
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt));
        }
    }

    // ============================================
    // UPDATE LANGUAGE
    // ============================================
    @Override
    public void updateLanguage(Long chatId, String languageCode) {
        Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);

        if (profileOpt.isPresent()) {
            JobSeekerProfile profile = profileOpt.get();
            profile.setLanguage(languageCode);
            jobSeekerProfileRepository.save(profile);
            log.info("✅ Til yangilandi: chatId={}, language={}", chatId, languageCode);
        } else {
            stateManager.putData(chatId, "tempLanguage", languageCode);
            log.info("✅ Til vaqtinchalik saqlandi: chatId={}, language={}", chatId, languageCode);
        }
    }

    // ============================================
    // CALLBACK HANDLER
    // ============================================
    @Override
    public SendMessage handleCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getMessage() == null) return null;

        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        if (callbackData != null && callbackData.startsWith("sq_")) {
            String[] parts = callbackData.split("_");
            if (parts.length >= 4) {
                String query = parts[1];
                String filter = parts[2];
                int page = Integer.parseInt(parts[3]);

                Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
                SendMessage newMsg = paginationService.handleSearchPagination(chatId, query, filter, page, profileOpt);

                try {
                    Telegram bot = applicationContext.getBean(Telegram.class);
                    EditMessageText editText = new EditMessageText();
                    editText.setChatId(chatId.toString());
                    editText.setMessageId(messageId);
                    editText.setText(newMsg.getText());
                    editText.setParseMode("HTML");
                    bot.execute(editText);

                    EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
                    editMarkup.setChatId(chatId.toString());
                    editMarkup.setMessageId(messageId);
                    editMarkup.setReplyMarkup((InlineKeyboardMarkup) newMsg.getReplyMarkup());
                    bot.execute(editMarkup);
                    return null;
                } catch (Exception e) {
                    log.error("❌ Xabarni tahrirlashda xatolik: {}", e.getMessage());
                    executeMessage(newMsg);
                    return null;
                }
            }
        }

        if (callbackData != null && callbackData.startsWith("vacancy_page_")) {
            String[] parts = callbackData.split("_");
            if (parts.length >= 4) {
                String categoryKey = parts[2];
                int page = Integer.parseInt(parts[3]);

                Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
                SendMessage newMsg = paginationService.handleVacancyPagination(chatId, categoryKey, page, profileOpt);

                try {
                    Telegram bot = applicationContext.getBean(Telegram.class);

                    EditMessageText editText = new EditMessageText();
                    editText.setChatId(chatId.toString());
                    editText.setMessageId(messageId);
                    editText.setText(newMsg.getText());
                    editText.setParseMode("Markdown");
                    bot.execute(editText);

                    EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
                    editMarkup.setChatId(chatId.toString());
                    editMarkup.setMessageId(messageId);
                    editMarkup.setReplyMarkup((InlineKeyboardMarkup) newMsg.getReplyMarkup());
                    bot.execute(editMarkup);

                    return null;
                } catch (Exception e) {
                    log.error("❌ Xabarni tahrirlashda xatolik: {}", e.getMessage(), e);
                    executeMessage(newMsg);
                    return null;
                }
            }
        }

        if ("back_to_categories".equals(callbackData)) {
            Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
            String msg = getText(profileOpt, chatId,
                    "📂 **Выберите категорию для поиска:**",
                    "📂 **Kategoriyani tanlang:**"
            );
            SendMessage response = new SendMessage();
            response.setChatId(chatId.toString());
            response.setText(msg);
            response.setParseMode("Markdown");
            response.setReplyMarkup(keyboardService.getCategoryInlineKeyboard(profileOpt));
            return response;
        }

        if (callbackData != null && callbackData.startsWith("category_")) {
            String categoryKey = callbackData.substring("category_".length());
            Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);

            SendMessage response = paginationService.handleVacancyPagination(chatId, categoryKey, 0, profileOpt);
            return response;
        }

        if (callbackData != null && callbackData.startsWith("apply_")) {
            Long vacancyId = Long.parseLong(callbackData.substring("apply_".length()));

            Optional<JobVacancy> vacancyOpt = jobVacancyRepository.findById(vacancyId);
            Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findByUserId(chatId);

            if (vacancyOpt.isPresent() && seekerOpt.isPresent()) {
                JobVacancy vacancy = vacancyOpt.get();
                JobSeekerProfile seeker = seekerOpt.get();

                Long employerChatId = vacancy.getEmployerChatId();

                SendMessage notification = employerHandler.buildApplicationNotification(
                        employerChatId,
                        vacancyId,
                        chatId,
                        vacancy.getTitle(),
                        seeker.getFullName(),
                        seeker.getPhoneNumber(),
                        "uz"
                );

                executeMessage(notification);

                String msg = getText(seekerOpt, chatId,
                        "✅ Ваша заявка успешно отправлена! Ожидайте ответа работодателя.",
                        "✅ Arizangiz muvaffaqiyatli topshirildi! Ish beruvchi javobini kuting."
                );
                return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(seekerOpt));
            } else {
                String msg = getText(seekerOpt, chatId,
                        "❌ Вакансия или профиль не найдены!",
                        "❌ Vakansiya yoki profilingiz topilmadi!"
                );
                return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(seekerOpt));
            }
        }

        return null;
    }

    @Override
    public SendMessage handleVacancyPagination(Long chatId, String categoryKey, int page, Optional<JobSeekerProfile> profileOpt) {
        return paginationService.handleVacancyPagination(chatId, categoryKey, page, profileOpt);
    }

    @Override
    public ReplyKeyboard getCategoryInlineKeyboard(Optional<Object> empty) {
        return keyboardService.getCategoryInlineKeyboard(Optional.empty());
    }

}