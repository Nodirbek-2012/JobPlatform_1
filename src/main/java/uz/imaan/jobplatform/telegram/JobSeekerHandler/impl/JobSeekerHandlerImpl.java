package uz.imaan.jobplatform.telegram.JobSeekerHandler.impl;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
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
import uz.imaan.jobplatform.jobseeker.service.interfaces.WalletService;
import uz.imaan.jobplatform.telegram.EmployerHandler;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.JobSeekerState;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.JobSeekerHandler;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.KeyboardService;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.PaginationService;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.StateManager;
import uz.imaan.jobplatform.telegram.Telegram;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobSeekerHandlerImpl implements JobSeekerHandler {

    // ===== INJECTED REPOSITORIES =====
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobVacancyRepository jobVacancyRepository;
    private final BankCardRepository bankCardRepository;
    private final SubscriptionRepository subscriptionRepository;

    // ===== INJECTED SERVICES =====
    private final JobStore jobStore;
    private final WalletService walletService;
    private final EmployerHandler employerHandler;
    private final ApplicationContext applicationContext;

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

    private boolean isEnglish(Optional<JobSeekerProfile> profileOpt) {
        return profileOpt.isPresent() && "en".equals(profileOpt.get().getLanguage());
    }

    private String getText(Optional<JobSeekerProfile> profileOpt, Long chatId, String ru, String uz, String en) {
        if (profileOpt.isPresent()) {
            String lang = profileOpt.get().getLanguage();
            if ("ru".equals(lang)) return ru;
            if ("en".equals(lang)) return en;
            return uz;
        }
        String tempLang = stateManager.getData(chatId, "tempLanguage");
        if ("ru".equals(tempLang)) return ru;
        if ("en".equals(tempLang)) return en;
        return uz;
    }

    // ============================================
    // YORDAMCHI METODLAR
    // ============================================
    private SendMessage createMessage(Long chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        if (keyboard != null) message.setReplyMarkup(keyboard);
        return message;
    }

    private SendMessage createMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        if (keyboard != null) message.setReplyMarkup(keyboard);
        return message;
    }

    private void executeMessage(SendMessage message) {
        try {
            Telegram bot = applicationContext.getBean(Telegram.class);
            bot.execute(message);
        } catch (Exception e) {
            log.error("❌ Xabar yuborishda xatolik: {}", e.getMessage(), e);
        }
    }

    // ============================================
    // KARTA NIQOBLASH VA LUHN VALIDATSIYASI
    // ============================================
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
                String tempLang = stateManager.getData(chatId, "tempLanguage");
                if (tempLang != null && !tempLang.isEmpty()) {
                    stateManager.setState(chatId, JobSeekerState.WAITING_FOR_NAME);
                    stateManager.putData(chatId, "registration", "true");
                    String msg = getText(profileOpt, chatId,
                            "👤 **Регистрация в качестве соискателя:**\n\nВведите ваше имя и фамилию.\n💡 *Пример:* `Ali Valiyev`",
                            "👤 **Ish izlovchi sifatida ro'yxatdan o'tish:**\n\nIltimos, ism va familiyangizni kiriting.\n💡 *Misol:* `Ali Valiyev`",
                            "👤 **Registration as a job seeker:**\n\nPlease enter your first and last name.\n💡 *Example:* `Ali Valiyev`"
                    );
                    return createMessage(chatId, msg, (ReplyKeyboardMarkup) null);
                } else {
                    stateManager.setState(chatId, JobSeekerState.WAITING_FOR_LANGUAGE);
                    stateManager.putData(chatId, "registration", "true");
                    String welcomeMsg = "🌐 **Xush kelibsiz!**\n\n" +
                            "Iltimos, tilni tanlang / Пожалуйста, выберите язык / Please select language:";
                    return createMessage(chatId, welcomeMsg, keyboardService.getLanguageKeyboard());
                }
            }
        }

        // ===== NAVIGATION =====
        if (text.equals("⬅️ Orqaga") || text.equals("❌ Bekor qilish") ||
                text.equals("⬅️ Назад") || text.equals("🏠 Главное меню") || text.equals("Asosiy menyu") ||
                text.equals("⬅️ Back") || text.equals("🏠 Main menu")) {
            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            return createMessage(chatId, keyboardService.getMainMenuText(profileOpt),
                    keyboardService.getMainMenuKeyboard(profileOpt));
        }

        // ===== KARTA QO'SHISH =====
        if (state == JobSeekerState.WAITING_FOR_CARD_NUMBER) {
            return handleCardNumber(chatId, text, profileOpt);
        }
        if (state == JobSeekerState.WAITING_FOR_CARD_HOLDER) {
            return handleCardHolder(chatId, text, profileOpt);
        }

        // ===== TIL TANLASH =====
        if (state == JobSeekerState.WAITING_FOR_LANGUAGE) {
            return handleLanguageSelection(chatId, text, profileOpt);
        }

        // ===== PORTFOLIO =====
        if (state == JobSeekerState.WAITING_FOR_PORTFOLIO && message.hasText()) {
            return handlePortfolioInput(chatId, text, profileOpt);
        }

        // ===== KATEGORIYA TANLASH (KASB O'ZGARTIRISH) =====
        if (state == JobSeekerState.WAITING_FOR_PROFESSION && message.hasText()) {
            return handleProfessionUpdate(chatId, text, profileOpt);
        }

        // ===== REGISTRATSIYA =====
        if (text.equals("JobSeeker (Ish izlovchi)") || text.equals("Ish izlovchi (JobSeeker)") ||
                text.equals("JobSeeker")) {
            return handleRegistrationStart(chatId, profileOpt);
        }

        // ===== REGISTRATSIYA BOSQICHLARI =====
        // Step 1: Ism
        if (state == JobSeekerState.WAITING_FOR_NAME && message.hasText()) {
            stateManager.putData(chatId, "fullName", text);
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_PASSPORT);
            String msg = getText(profileOpt, chatId,
                    "🪪 **Введите серию и номер паспорта:**\n\n💡 *Пример:* `AA1234567`",
                    "🪪 **Pasport seriya va raqamingizni kiriting:**\n\n💡 *Misol:* `AA1234567`",
                    "🪪 **Enter your passport series and number:**\n\n💡 *Example:* `AA1234567`"
            );
            return createMessage(chatId, msg, (ReplyKeyboardMarkup) null);
        }

        // Step 2: Pasport
        if (state == JobSeekerState.WAITING_FOR_PASSPORT && message.hasText()) {
            stateManager.putData(chatId, "passport", text);
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_PHONE);
            String msg = getText(profileOpt, chatId,
                    "📱 **Отправьте свой номер телефона:**",
                    "📱 **Telefon raqamingizni yuboring:**",
                    "📱 **Send your phone number:**"
            );
            return createMessage(chatId, msg, keyboardService.getPhoneKeyboard());
        }

        // Step 3: Telefon raqam
        if (state == JobSeekerState.WAITING_FOR_PHONE) {
            return handlePhoneInput(chatId, message, profileOpt);
        }

        // Step 4: Ish turi
        if (state == JobSeekerState.WAITING_FOR_JOB_TYPE && message.hasText()) {
            return handleJobTypeSelection(chatId, text, profileOpt);
        }

        // Step 5: Kategoriya tanlash
        if (state == JobSeekerState.WAITING_FOR_CATEGORY && message.hasText()) {
            return handleCategorySelection(chatId, text, profileOpt);
        }

        // Step 6: Tajriba
        if (state == JobSeekerState.WAITING_FOR_EXPERIENCE && message.hasText()) {
            return handleExperienceInput(chatId, text, profileOpt);
        }

        // ===== ISMNI TAHRIRLASH =====
        if (state == JobSeekerState.WAITING_FOR_EDIT_NAME && message.hasText()) {
            return handleEditName(chatId, text, profileOpt);
        }

        // ===== COVER LETTER =====
        if (state == JobSeekerState.APPLY_COMMENT && message.hasText()) {
            return handleApplyComment(chatId, text, profileOpt);
        }

        // ===== ASOSIY MENYU TUGMALARI =====
        switch (text) {
            case "🔍 Ish qidirish":
            case "🔍 Поиск работы":
            case "🔍 Job search":
                stateManager.setState(chatId, JobSeekerState.JOB_SEARCH);
                String searchMsg = getText(profileOpt, chatId,
                        "📂 **Выберите категорию для поиска:**",
                        "📂 **Kategoriyani tanlang:**",
                        "📂 **Select a category to search:**"
                );
                SendMessage searchMessage = new SendMessage();
                searchMessage.setChatId(chatId.toString());
                searchMessage.setText(searchMsg);
                searchMessage.setParseMode("Markdown");
                searchMessage.setReplyMarkup(keyboardService.getCategoryInlineKeyboard(profileOpt));
                return searchMessage;

            case "⚡ Faol ishlarim":
            case "⚡ Мои активные работы":
            case "⚡ My active jobs":
                stateManager.setState(chatId, JobSeekerState.ACTIVE_JOBS);
                String activeMsg = getText(profileOpt, chatId,
                        "⚡ **Раздел моих активных работ:**",
                        "⚡ **Faol ishlarim bo'limi:**",
                        "⚡ **My active jobs section:**"
                );
                return createMessage(chatId, activeMsg, keyboardService.getActiveJobsKeyboard(profileOpt));

            case "👤 Profilim":
            case "👤 Мой профиль":
            case "👤 My profile":
                stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);
                return showProfile(chatId, profileOpt);

            case "📂 Arizalar":
            case "📂 Мои заявки":
            case "📂 My applications":
                stateManager.setState(chatId, JobSeekerState.APPLICATIONS);
                return handleShowApplications(chatId, profileOpt);

            case "💳 Hamyon":
            case "💳 Кошелек":
            case "💳 Wallet":
                stateManager.setState(chatId, JobSeekerState.WALLET_MENU);
                return showWallet(chatId, profileOpt);

            case "⚙️ Sozlamalar":
            case "⚙️ Настройки":
            case "⚙️ Settings":
                stateManager.setState(chatId, JobSeekerState.SETTINGS_MENU);
                return handleSettingsMenu(chatId, text, profileOpt);
        }

        // ===== JOB_SEARCH HOLATI =====
        if (state == JobSeekerState.JOB_SEARCH) {
            return null;
        }

        // ===== VAKANSIYA TANLANGANDA =====
        if (state == JobSeekerState.VIEW_JOB_DETAILS) {
            return handleViewJobDetails(chatId, text, profileOpt);
        }

        // ===== PROFIL MENYUSI =====
        if (state == JobSeekerState.PROFILE_MENU) {
            return handleProfileMenu(chatId, text, profileOpt);
        }

        // ===== HAMYON MENYUSI =====
        if (state == JobSeekerState.WALLET_MENU) {
            return handleWalletMenu(chatId, text, profileOpt);
        }

        // ===== FAOL ISHLAR =====
        if (state == JobSeekerState.ACTIVE_JOBS) {
            if (text.contains("Joriy ishlar") || text.contains("Текущие работы") || text.contains("Current jobs") ||
                    text.contains("Topshiriqlar") || text.contains("Задания") || text.contains("Tasks") ||
                    text.contains("Vazifalar") || text.contains("Задачи") || text.contains("Assignments")) {
                String msg = getText(profileOpt, chatId,
                        "📋 На данный момент у вас нет активных работ.",
                        "📋 Hozircha faol ishlaringiz mavjud emas.",
                        "📋 You have no active jobs at the moment."
                );
                return createMessage(chatId, msg, keyboardService.getActiveJobsKeyboard(profileOpt));
            }
        }

        // ===== SOZLAMALAR =====
        if (state == JobSeekerState.SETTINGS_MENU) {
            return handleSettingsMenu(chatId, text, profileOpt);
        }

        // ===== DEFAULT =====
        String defaultMsg = getText(profileOpt, chatId,
                "Пожалуйста, выберите один из вариантов.",
                "Iltimos, tugmalardan birini tanlang.",
                "Please select one of the options."
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
            String tempLang = stateManager.getData(chatId, "tempLanguage");
            if (tempLang != null && !tempLang.isEmpty()) {
                stateManager.setState(chatId, JobSeekerState.WAITING_FOR_NAME);
                stateManager.putData(chatId, "registration", "true");
                String msg = getText(profileOpt, chatId,
                        "👤 **Регистрация в качестве соискателя:**\n\nВведите ваше имя и фамилию.\n💡 *Пример:* `Ali Valiyev`",
                        "👤 **Ish izlovchi sifatida ro'yxatdan o'tish:**\n\nIltimos, ism va familiyangizni kiriting.\n💡 *Misol:* `Ali Valiyev`",
                        "👤 **Registration as a job seeker:**\n\nPlease enter your first and last name.\n💡 *Example:* `Ali Valiyev`"
                );
                return createMessage(chatId, msg, (ReplyKeyboardMarkup) null);
            } else {
                stateManager.setState(chatId, JobSeekerState.WAITING_FOR_LANGUAGE);
                stateManager.putData(chatId, "registration", "true");
                String msg = "🌐 Iltimos, tilni tanlang / Пожалуйста, выберите язык / Please select language:";
                return createMessage(chatId, msg, keyboardService.getLanguageKeyboard());
            }
        }
    }

    private SendMessage handlePhoneInput(Long chatId, Message message, Optional<JobSeekerProfile> profileOpt) {
        String text = message.hasText() ? message.getText().trim() : "";
        String phone = null;

        if (message.hasContact() && message.getContact() != null) {
            phone = message.getContact().getPhoneNumber();
        } else if (text != null && !text.isEmpty()) {
            phone = text;
        }

        if (phone != null && !phone.isEmpty()) {
            stateManager.putData(chatId, "phone", phone);

            JobSeekerProfile profile = new JobSeekerProfile();
            profile.setUserId(chatId);
            profile.setFullName(stateManager.getData(chatId, "fullName"));
            profile.setPassportNumber(stateManager.getData(chatId, "passport"));
            profile.setPhoneNumber(phone);

            String tempLang = stateManager.getData(chatId, "tempLanguage");
            profile.setLanguage(tempLang != null ? tempLang : "uz");

            jobSeekerProfileRepository.save(profile);

            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_JOB_TYPE);
            String msg = getText(Optional.of(profile), chatId,
                    "🛠 **Какую работу вы ищете?**\n\nПожалуйста, выберите один из вариантов:",
                    "🛠 **Qanday turdagi ish qidiryapsiz?**\n\nIltimos, quyidagi tugmalardan birini tanlang:",
                    "🛠 **What type of job are you looking for?**\n\nPlease select one of the options:"
            );
            return createMessage(chatId, msg, keyboardService.getJobTypeKeyboard(Optional.of(profile)));
        } else {
            String msg = getText(profileOpt, chatId,
                    "❌ Номер телефона не отправлен. Пожалуйста, используйте кнопку ниже:",
                    "❌ Telefon raqam yuborilmadi. Iltimos, pastdagi tugma orqali yuboring:",
                    "❌ Phone number not sent. Please use the button below:"
            );
            return createMessage(chatId, msg, keyboardService.getPhoneKeyboard());
        }
    }

    private SendMessage handleJobTypeSelection(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        String jobType = text;

        if (jobType.equals("🔧 Oddiy ishchi") || jobType.equals("🔧 Обычный рабочий") || jobType.equals("🔧 Ordinary worker")) {
            JobSeekerProfile profile = profileOpt.orElseThrow(() -> new RuntimeException("Profil topilmadi!"));
            profile.setPreferredJobType("Oddiy ishchi");
            profile.setProfession("Oddiy ishchi");
            jobSeekerProfileRepository.save(profile);

            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            stateManager.clearData(chatId);

            String msg = getText(Optional.of(profile), chatId,
                    "✅ **Вы успешно зарегистрировались!**\n\n🔧 **Тип работы:** Обычный рабочий",
                    "✅ **Muvaffaqiyatli ro'yxatdan o'tdingiz!**\n\n🔧 **Ish turi:** Oddiy ishchi",
                    "✅ **You have successfully registered!**\n\n🔧 **Job type:** Ordinary worker"
            );
            return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(Optional.of(profile)));
        }

        if (jobType.equals("👨‍💻 Kasbim bo'yicha") || jobType.equals("👨‍💻 По профессии") || jobType.equals("👨‍💻 By profession")) {
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_CATEGORY);
            String msg = getText(profileOpt, chatId,
                    "📂 **Выберите категорию, соответствующую вашей профессии:**",
                    "📂 **Kasbingizga mos kategoriyani tanlang:**",
                    "📂 **Select the category matching your profession:**"
            );
            return createMessage(chatId, msg, keyboardService.getRegistrationCategoryKeyboard(profileOpt));
        }

        return createMessage(chatId,
                getText(profileOpt, chatId,
                        "❌ Пожалуйста, выберите один из вариантов:",
                        "❌ Iltimos, quyidagi tugmalardan birini tanlang:",
                        "❌ Please select one of the options:"
                ),
                keyboardService.getJobTypeKeyboard(profileOpt));
    }

    private SendMessage handleCategorySelection(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        String category = text;
        stateManager.putData(chatId, "category", category);
        stateManager.putData(chatId, "profession", category);

        stateManager.setState(chatId, JobSeekerState.WAITING_FOR_EXPERIENCE);
        String msg = getText(profileOpt, chatId,
                "📝 **Расскажите о своем опыте работы:**\n\nСколько времени вы работаете?\n💡 *Пример:* `3 года Java разработчиком`",
                "📝 **Ish tajribangiz haqida ma'lumot bering:**\n\nQancha vaqtdan beri ishlayapsiz?\n💡 *Misol:* `3 yil Java dasturchi`",
                "📝 **Tell us about your work experience:**\n\nHow long have you been working?\n💡 *Example:* `3 years Java developer`"
        );
        return createMessage(chatId, msg, keyboardService.getCancelKeyboard(profileOpt));
    }

    private SendMessage handleExperienceInput(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        stateManager.putData(chatId, "experience", text);

        JobSeekerProfile profile = profileOpt.orElseThrow(() -> new RuntimeException("Profil topilmadi!"));
        profile.setExperience(stateManager.getData(chatId, "experience"));
        profile.setProfession(stateManager.getData(chatId, "profession"));
        profile.setCategory(stateManager.getData(chatId, "category"));
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
                ),
                String.format(
                        "✅ **You have successfully registered!**\n\n" +
                                "📂 **Category:** %s\n" +
                                "💼 **Your profession:** %s\n" +
                                "📝 **Your experience:** %s",
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
                "✅ Kasb muvaffaqiyatli yangilandi!\n\n💼 Yangi kasb: `" + profession + "`",
                "✅ Profession successfully updated!\n\n💼 New profession: `" + profession + "`"
        );
        return createMessage(chatId, msg, keyboardService.getProfileKeyboard(profileOpt));
    }

    private SendMessage handleEditName(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        JobSeekerProfile profile = profileOpt.orElseGet(() -> {
            JobSeekerProfile p = new JobSeekerProfile();
            p.setUserId(chatId);
            return p;
        });
        profile.setFullName(text);
        jobSeekerProfileRepository.save(profile);

        stateManager.setState(chatId, JobSeekerState.PROFILE_MENU);
        String msg = getText(profileOpt, chatId,
                "✅ Имя и фамилия обновлены!\n\n👤 Новое Ф.И.О: `" + text + "`",
                "✅ Ism-familiyangiz yangilandi!\n\n👤 Yangi F.I.O: `" + text + "`",
                "✅ Name updated!\n\n👤 New name: `" + text + "`"
        );
        return createMessage(chatId, msg, keyboardService.getProfileKeyboard(profileOpt));
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
                                "🪪 **Pasport:** %s\n" +
                                "📞 **Tel:** %s\n" +
                                "📝 **Tajriba:** %s\n" +
                                "🛠 **Ish turi:** %s\n" +
                                "✍️ **Izoh:** %s",
                        selectedJob.getTitle(),
                        profile.getFullName() != null ? profile.getFullName() : "Kiritilmagan",
                        profile.getPassportNumber() != null ? profile.getPassportNumber() : "Kiritilmagan",
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
                "✅ **Ariza yuborildi!**",
                "✅ **Application sent!**"
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
                        "Vakansiya ma'lumotlarini yuklashda xatolik.",
                        "Error loading vacancy information."
                );
                return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
            }
        }

        if (text.equals("📝 Ariza berish") || text.equals("📝 Подать заявку") || text.equals("📝 Submit application")) {
            stateManager.setState(chatId, JobSeekerState.APPLY_COMMENT);
            String msg = getText(profileOpt, chatId,
                    "✍️ **Напишите сопроводительное письмо:**\n\nКратко расскажите о себе и своем опыте:",
                    "✍️ **Cover letter (Izoh) yozing:**\n\nO'zingiz va tajribangiz haqida qisqacha ma'lumot qoldiring:",
                    "✍️ **Write a cover letter:**\n\nBriefly tell about yourself and your experience:"
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
                    "❌ Siz hali ro'yxatdan o'tmagansiz!",
                    "❌ You are not registered yet!"
            );
            return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
        }
        String menuText = getText(profileOpt, chatId,
                "👤 **Profil menyusi**\n\nQuyidagi tugmalardan birini tanlang:",
                "👤 **Profil menyusi**\n\nQuyidagi tugmalardan birini tanlang:",
                "👤 **Profile menu**\n\nSelect one of the buttons below:"
        );
        return createMessage(chatId, menuText, keyboardService.getProfileKeyboard(profileOpt));
    }

    @Override
    public SendMessage handleProfileMenu(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        JobSeekerProfile profile = profileOpt.orElse(new JobSeekerProfile());
        double rating = profile.getRating() != null ? profile.getRating() : 0.0;

        if (text.contains("Ma'lumotlar") || text.contains("Информация") || text.contains("Information")) {
            return createMessage(chatId, getProfileInfo(profile, profileOpt), keyboardService.getProfileKeyboard(profileOpt));
        }
        if (text.contains("Portfolio") || text.contains("Портфолио") || text.contains("Portfolio")) {
            return showPortfolio(chatId, profileOpt);
        }
        if (text.contains("Reyting") || text.contains("Рейтинг") || text.contains("Rating")) {
            String msg = getText(profileOpt, chatId,
                    String.format("⭐ **Ваш текущий рейтинг:** %.1f / 5.0", rating),
                    String.format("⭐ **Sizning joriy reytingingiz:** %.1f / 5.0", rating),
                    String.format("⭐ **Your current rating:** %.1f / 5.0", rating)
            );
            return createMessage(chatId, msg, keyboardService.getProfileKeyboard(profileOpt));
        }
        if (text.contains("Rasm") || text.contains("Фото") || text.contains("Photo")) {
            String msg = getText(profileOpt, chatId,
                    "🖼 **Фото профиля:**\n\nОтправьте боту фото для обновления аватара:",
                    "🖼 **Profil rasmi:**\n\nProfil rasmingizni yangilash uchun botga rasm yuboring:",
                    "🖼 **Profile photo:**\n\nSend a photo to update your avatar:"
            );
            return createMessage(chatId, msg, keyboardService.getSubBackKeyboard(profileOpt));
        }
        if (text.contains("Kasb") || text.contains("Профессия") || text.contains("Profession")) {
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_PROFESSION);
            String msg = getText(profileOpt, chatId,
                    "📂 **Выберите новую профессию из списка:**\n\nЕсли вашей профессии нет в списке, нажмите кнопку ниже:",
                    "📂 **Yangi kasbni kategoriyadan tanlang:**\n\nAgar kasbingiz ro'yxatda bo'lmasa, pastdagi tugmani bosing:",
                    "📂 **Select a new profession from the list:**\n\nIf your profession is not in the list, click the button below:"
            );
            return createMessage(chatId, msg, keyboardService.getRegistrationCategoryKeyboard(profileOpt));
        }
        if (text.contains("Tahrirlash") || text.contains("Редактировать") || text.contains("Edit")) {
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_EDIT_NAME);
            String msg = getText(profileOpt, chatId,
                    "✏️ **Редактирование профиля:**\n\nВведите новое имя и фамилию:",
                    "✏️ **Profilni tahrirlash:**\n\nYangi ism va familiyangizni kiriting:",
                    "✏️ **Edit profile:**\n\nEnter new name:"
            );
            return createMessage(chatId, msg, keyboardService.getSubBackKeyboard(profileOpt));
        }
        String msg = getText(profileOpt, chatId, "👤 **Меню профиля**", "👤 **Profil menyusi**", "👤 **Profile menu**");
        return createMessage(chatId, msg, keyboardService.getProfileKeyboard(profileOpt));
    }

    @Override
    public String getProfileInfo(JobSeekerProfile profile, Optional<JobSeekerProfile> profileOpt) {
        String jobTypeDisplay;
        String preferred = profile.getPreferredJobType();

        if ("PROFESSIONAL".equals(preferred) && profile.getProfession() != null && !profile.getProfession().isEmpty()) {
            jobTypeDisplay = "👨‍💻 " + profile.getProfession();
        } else {
            if (isRussian(profileOpt)) {
                jobTypeDisplay = "🔧 Обычный рабочий";
            } else if (isEnglish(profileOpt)) {
                jobTypeDisplay = "🔧 Ordinary worker";
            } else {
                jobTypeDisplay = "🔧 Oddiy ishchi";
            }
            if (profile.getProfession() != null && !profile.getProfession().isEmpty() &&
                    ("Kasbim bo'yicha".equals(preferred) || "По профессии".equals(preferred) || "By profession".equals(preferred))) {
                jobTypeDisplay = "👨‍💻 " + profile.getProfession();
            }
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

        if (isRussian(profileOpt)) {
            return String.format(
                    "👤 **Информация профиля:**\n\n" +
                            "📌 **Ф.И.О:** %s\n" +
                            "🪪 **Паспорт:** %s\n" +
                            "📞 **Телефон:** %s\n" +
                            "⭐ **Рейтинг:** %.1f\n" +
                            "💼 **Профессия:** %s\n" +
                            "📝 **Опыт:** %s\n" +
                            "🛠 **Тип работы:** %s\n" +
                            "💳 **Карта:** %s",
                    profile.getFullName() != null ? profile.getFullName() : "Не указано",
                    profile.getPassportNumber() != null ? profile.getPassportNumber() : "Не указан",
                    profile.getPhoneNumber() != null ? profile.getPhoneNumber() : "Не указан",
                    profile.getRating() != null ? profile.getRating() : 0.0,
                    profile.getProfession() != null ? profile.getProfession() : "Не указана",
                    profile.getExperience() != null ? profile.getExperience() : "Не указан",
                    jobTypeDisplay,
                    cardDisplay
            );
        }
        if (isEnglish(profileOpt)) {
            return String.format(
                    "👤 **Profile information:**\n\n" +
                            "📌 **Full name:** %s\n" +
                            "🪪 **Passport:** %s\n" +
                            "📞 **Phone:** %s\n" +
                            "⭐ **Rating:** %.1f\n" +
                            "💼 **Profession:** %s\n" +
                            "📝 **Experience:** %s\n" +
                            "🛠 **Job type:** %s\n" +
                            "💳 **Card:** %s",
                    profile.getFullName() != null ? profile.getFullName() : "Not specified",
                    profile.getPassportNumber() != null ? profile.getPassportNumber() : "Not specified",
                    profile.getPhoneNumber() != null ? profile.getPhoneNumber() : "Not specified",
                    profile.getRating() != null ? profile.getRating() : 0.0,
                    profile.getProfession() != null ? profile.getProfession() : "Not specified",
                    profile.getExperience() != null ? profile.getExperience() : "Not specified",
                    jobTypeDisplay,
                    cardDisplay
            );
        }
        return String.format(
                "👤 **Profil ma'lumotlari:**\n\n" +
                        "📌 **F.I.O:** %s\n" +
                        "🪪 **Pasport:** %s\n" +
                        "📞 **Tel:** %s\n" +
                        "⭐ **Reyting:** %.1f\n" +
                        "💼 **Kasb:** %s\n" +
                        "📝 **Tajriba:** %s\n" +
                        "🛠 **Ish turi:** %s\n" +
                        "💳 **Karta:** %s",
                profile.getFullName() != null ? profile.getFullName() : "Kiritilmagan",
                profile.getPassportNumber() != null ? profile.getPassportNumber() : "Kiritilmagan",
                profile.getPhoneNumber() != null ? profile.getPhoneNumber() : "Kiritilmagan",
                profile.getRating() != null ? profile.getRating() : 0.0,
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
                            "📁 **Sizning joriy portfoliongiz:**\n\n",
                            "📁 **Your current portfolio:**\n\n"))
                    .append(currentPortfolio)
                    .append("\n\n")
                    .append(getText(profileOpt, chatId,
                            "✏️ Yangilash uchun yangi havola yoki matn yuboring:",
                            "✏️ Yangilash uchun yangi havola yoki matn yuboring:",
                            "✏️ Send new portfolio link or text to update:"));
        } else {
            msg.append(getText(profileOpt, chatId,
                    "📁 Отправьте ссылку на ваше портфолио (GitHub, резюме и т.д.):",
                    "📁 Portfoliongizni kiriting (GitHub havola, loyihalar haqida matn yoki rezyume linki):",
                    "📁 Please send your portfolio link (GitHub, resume, etc.):"));
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
                "✅ Portfolio muvaffaqiyatli saqlandi!",
                "✅ Portfolio successfully saved!"
        );

        return createMessage(chatId, msg, keyboardService.getProfileKeyboard(profileOpt));
    }

    @Override
    public String getProfileInfo(Long chatId, Optional<JobSeekerProfile> profileOpt) {
        if (profileOpt.isEmpty()) return "Profil topilmadi / Профиль не найден";

        JobSeekerProfile profile = profileOpt.get();

        String jobTypeDisplay;
        String preferred = profile.getPreferredJobType();
        if ("PROFESSIONAL".equals(preferred) && profile.getProfession() != null && !profile.getProfession().isEmpty()) {
            jobTypeDisplay = "👨‍💻 " + profile.getProfession();
        } else {
            if (isRussian(profileOpt)) jobTypeDisplay = "🔧 Обычный рабочий";
            else if (isEnglish(profileOpt)) jobTypeDisplay = "🔧 Ordinary worker";
            else jobTypeDisplay = "🔧 Oddiy ishchi";
        }

        String cardDisplay = bankCardRepository.findFirstByJobSeekerId(chatId)
                .map(card -> maskCardNumber(card.getCardNumber()))
                .orElse(getText(profileOpt, chatId, "Не указана", "Kiritilmagan", "Not set"));

        return String.format(
                "%s\n\n" +
                        "👤 **%s:** %s\n" +
                        "📞 **%s:** %s\n" +
                        "🪪 **%s:** %s\n" +
                        "💼 **%s:** %s\n" +
                        "🔧 **%s:** %s\n" +
                        "💳 **%s:** %s\n" +
                        "⭐ **%s:** %.1f",
                getText(profileOpt, chatId, "👤 **Sizning profilingiz:**", "👤 **Ваш профиль:**", "👤 **Your profile:**"),
                getText(profileOpt, chatId, "F.I.O", "Ф.И.О", "Full Name"),
                profile.getFullName(),
                getText(profileOpt, chatId, "Tel", "Тел", "Phone"),
                profile.getPhoneNumber(),
                getText(profileOpt, chatId, "Pasport", "Паспорт", "Passport"),
                profile.getPassportNumber(),
                getText(profileOpt, chatId, "Kasb", "Профессия", "Profession"),
                profile.getProfession() != null ? profile.getProfession() : "-",
                getText(profileOpt, chatId, "Ish turi", "Тип работы", "Job Type"),
                jobTypeDisplay,
                getText(profileOpt, chatId, "Karta", "Карта", "Card"),
                cardDisplay,
                getText(profileOpt, chatId, "Reyting", "Рейтинг", "Rating"),
                profile.getRating()
        );
    }

    // ============================================
    // HAMYON METODLARI
    // ============================================
    @Override
    public SendMessage handleWalletMenu(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        if (text.contains("Bank kartasi qo'shish") || text.contains("💳 Bank kartasi") ||
                text.contains("Добавить банковскую карту") || text.contains("💳 Банковская карта") ||
                text.contains("Add bank card") || text.contains("💳 Bank card")) {

            if (profileOpt.isPresent()) {
                List<BankCard> bankCards = profileOpt.get().getBankCards();
                if (bankCards != null && !bankCards.isEmpty()) {
                    String msg = getText(profileOpt, chatId,
                            "⚠️ У вас уже есть карта! Сначала удалите текущую карту.",
                            "⚠️ Sizda allaqachon karta mavjud! Avval kartani o'chiring.",
                            "⚠️ You already have a card! Please delete the current card first."
                    );
                    return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt, true));
                }
            }

            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_CARD_NUMBER);
            String msg = getText(profileOpt, chatId,
                    "💳 Введите номер вашей карты (16 цифр):",
                    "💳 Karta raqamingizni kiriting (16 xona):",
                    "💳 Enter your card number (16 digits):"
            );
            return createMessage(chatId, msg, keyboardService.getCancelKeyboard(profileOpt));
        }

        if (text.contains("Kartani o'chirish") || text.contains("🗑️ Kartani o'chirish") ||
                text.contains("Удалить карту") || text.contains("🗑️ Удалить карту") ||
                text.contains("Delete card") || text.contains("🗑️ Delete card")) {
            return handleDeleteCard(chatId, profileOpt);
        }

        if (text.contains("Hisob balansi") || text.contains("💰 Hisob balansi") ||
                text.contains("Баланс") || text.contains("💰 Баланс") ||
                text.contains("Balance") || text.contains("💰 Balance")) {
            return showWallet(chatId, profileOpt);
        }

        if (text.contains("Pul yechish") || text.contains("💸 Pul yechish") ||
                text.contains("Снять деньги") || text.contains("💸 Снять деньги") ||
                text.contains("Withdraw") || text.contains("💸 Withdraw")) {
            String msg = getText(profileOpt, chatId,
                    "⚠️ Минимальная сумма для снятия: 50,000 сум.",
                    "⚠️ Pul yechish uchun minimal summa: 50,000 so'm.",
                    "⚠️ Minimum withdrawal amount: 50,000 sum."
            );
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt));
        }

        if (text.contains("To'lov usullari") || text.contains("💳 To'lov usullari") ||
                text.contains("Способы оплаты") || text.contains("💳 Способы оплаты") ||
                text.contains("Payment methods") || text.contains("💳 Payment methods")) {
            String msg = getText(profileOpt, chatId,
                    "💳 **Доступные способы оплаты:**\n\n- Click\n- Payme\n- Uzum Bank",
                    "💳 **Mavjud to'lov usullari:**\n\n- Click\n- Payme\n- Uzum Bank",
                    "💳 **Available payment methods:**\n\n- Click\n- Payme\n- Uzum Bank"
            );
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt));
        }

        if (text.contains("To'lov tarixi") || text.contains("📜 To'lov tarixi") ||
                text.contains("История платежей") || text.contains("📜 История платежей") ||
                text.contains("Payment history") || text.contains("📜 Payment history")) {
            String msg = getText(profileOpt, chatId,
                    "📜 **История платежей:**\n\nНа данный момент операций нет.",
                    "📜 **To'lovlar tarixi:**\n\nHozircha amaliyotlar mavjud emas.",
                    "📜 **Payment history:\n\nNo transactions yet."
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
                        "❌ Siz hali ro'yxatdan o'tmagansiz!",
                        "❌ You are not registered yet!"
                );
                return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
            }

            JobSeekerProfile profile = profileOpt.get();
            List<BankCard> bankCards = profile.getBankCards();
            if (bankCards == null || bankCards.isEmpty()) {
                String msg = getText(profileOpt, chatId,
                        "❌ У вас нет сохраненных карт.",
                        "❌ Sizda saqlangan karta mavjud emas.",
                        "❌ You have no saved cards."
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
                    "✅ Karta muvaffaqiyatli o'chirildi! (" + maskedCard + ")",
                    "✅ Card successfully deleted! (" + maskedCard + ")"
            );
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt, false));
        } catch (Exception e) {
            log.error("❌ Kartani o'chirishda xatolik: {}", e.getMessage(), e);
            String msg = getText(profileOpt, chatId,
                    "❌ Ошибка при удалении карты: " + e.getMessage(),
                    "❌ Kartani o'chirishda xatolik: " + e.getMessage(),
                    "❌ Error deleting card: " + e.getMessage()
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
                        "❌ Siz hali ro'yxatdan o'tmagansiz!",
                        "❌ You are not registered yet!"
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
                            "\n📌 Quyidagi tugmalardan foydalaning:",
                    "💳 **Wallet and payments:**\n\n" +
                            "💰 **Balance:** " + balance + " sum\n" +
                            "💳 **Card:** " + cardNumberDisplay + "\n" +
                            (cardHolderDisplay.isEmpty() ? "" : cardHolderDisplay + "\n") +
                            "\n📌 Use the buttons below:"
            );

            boolean hasCard = bankCards != null && !bankCards.isEmpty();
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt, hasCard));
        } catch (Exception e) {
            log.error("❌ Hamyonni ko'rsatishda xatolik: {}", e.getMessage(), e);
            String msg = getText(profileOpt, chatId,
                    "❌ Произошла ошибка!",
                    "❌ Xatolik yuz berdi!",
                    "❌ An error occurred!"
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
                    "📂 Arizalaringizni ko'rish uchun avval ro'yxatdan o'ting.",
                    "📂 To view your applications, please register first."
            );
            return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(profileOpt));
        }

        Long jobSeekerId = profileOpt.get().getId();
        List<JobApplication> myApps = jobApplicationRepository.findByJobSeekerId(jobSeekerId);

        if (myApps == null || myApps.isEmpty()) {
            String msg = getText(profileOpt, chatId,
                    "📂 Вы еще не подавали заявок на вакансии.",
                    "📂 Siz hali hech qanday vakansiyaga ariza topshirmagansiz.",
                    "📂 You have not submitted any applications yet."
            );
            return createMessage(chatId, msg, keyboardService.getSubBackKeyboard(profileOpt));
        }

        String title = getText(profileOpt, chatId,
                "📋 **Ваши заявки:**\n\n",
                "📋 **Siz yuborgan arizalar:**\n\n",
                "📋 **Your applications:**\n\n"
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
                    case PENDING -> "⏳ Ko'rib chiqilmoqda";
                    case ACCEPTED -> "✅ Qabul qilindi";
                    case REJECTED -> "❌ Rad etildi";
                    case CANCELLED -> "🚫 Bekor qilingan";
                };
            } else if (isEnglish(profileOpt)) {
                statusStr = switch (app.getStatus()) {
                    case PENDING -> "⏳ Pending";
                    case ACCEPTED -> "✅ Accepted";
                    case REJECTED -> "❌ Rejected";
                    case CANCELLED -> "🚫 Cancelled";
                };
            } else {
                statusStr = switch (app.getStatus()) {
                    case PENDING -> "⏳ Ko'rib chiqilmoqda";
                    case ACCEPTED -> "✅ Qabul qilindi";
                    case REJECTED -> "❌ Rad etildi";
                    case CANCELLED -> "🚫 Bekor qilingan";
                };
            }

            sb.append("📌 **Vakansiya:** ").append(jobTitle).append("\n")
                    .append("✍️ **Cover letter:** ").append(app.getCoverLetter() != null ? app.getCoverLetter() : "Yo'q").append("\n")
                    .append("📊 **Holat:** ").append(statusStr).append("\n")
                    .append("───────────────\n");
        }

        return createMessage(chatId, sb.toString(), keyboardService.getSubBackKeyboard(profileOpt));
    }

    // ============================================
    // SOZLAMALAR METODLARI
    // ============================================
    @Override
    public SendMessage handleSettingsMenu(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        JobSeekerState currentState = stateManager.getState(chatId);

        if (text.equals("🌐 Til") || text.equals("🌐 Язык") || text.equals("🌐 Language") ||
                text.contains("Til") || text.contains("Язык") || text.contains("Language")) {

            stateManager.setPreviousState(chatId, currentState);
            stateManager.setState(chatId, JobSeekerState.WAITING_FOR_LANGUAGE);
            String msg = getText(profileOpt, chatId,
                    "🌐 **Выберите язык / Choose language:**",
                    "🌐 **Tilni tanlang / Choose language:**",
                    "🌐 **Choose language / Tilni tanlang:**"
            );
            return createMessage(chatId, msg, keyboardService.getLanguageKeyboard());
        }

        if (text.equals("🔒 Maxfiylik") || text.equals("🔒 Конфиденциальность") || text.equals("🔒 Privacy") ||
                text.contains("Maxfiylik") || text.contains("Конфиденциальность") || text.contains("Privacy")) {
            String msg = getText(profileOpt, chatId,
                    "🔒 **Настройки конфиденциальности:**\n\nВаши данные надежно защищены.",
                    "🔒 **Maxfiylik sozlamalari:**\n\nSizning ma'lumotlaringiz xavfsiz saqlanadi.",
                    "🔒 **Privacy settings:**\n\nYour data is securely protected."
            );
            return createMessage(chatId, msg, keyboardService.getSettingsKeyboard(profileOpt));
        }

        if (text.equals("🔔 Bildirishnoma") || text.equals("🔔 Уведомления") || text.equals("🔔 Notifications") ||
                text.contains("Bildirishnoma") || text.contains("Уведомления") || text.contains("Notifications")) {
            String msg = getText(profileOpt, chatId,
                    "🔔 **Уведомления:** Включены ✅",
                    "🔔 **Bildirishnomalar:** Yoniq ✅",
                    "🔔 **Notifications:** Enabled ✅"
            );
            return createMessage(chatId, msg, keyboardService.getSettingsKeyboard(profileOpt));
        }

        if (text.equals("❓ Yordam") || text.equals("❓ Помощь") || text.equals("❓ Help") ||
                text.contains("Yordam") || text.contains("Помощь") || text.contains("Help")) {
            String msg = getText(profileOpt, chatId,
                    "❓ **Центр помощи:**\n\nВ случае возникновения проблем свяжитесь с администратором.",
                    "❓ **Yordam markazi:**\n\nMuammo yuzaga kelsa, admin bilan bog'laning.",
                    "❓ **Help center:**\n\nIf you have any problems, contact the administrator."
            );
            return createMessage(chatId, msg, keyboardService.getSettingsKeyboard(profileOpt));
        }

        if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("⬅️ Back")) {
            stateManager.setState(chatId, JobSeekerState.MAIN_MENU);
            return createMessage(chatId, keyboardService.getMainMenuText(profileOpt),
                    keyboardService.getMainMenuKeyboard(profileOpt));
        }

        String msg = getText(profileOpt, chatId,
                "⚙️ **Раздел настроек:**",
                "⚙️ **Sozlamalar bo'limi:**",
                "⚙️ **Settings section:**"
        );
        return createMessage(chatId, msg, keyboardService.getSettingsKeyboard(profileOpt));
    }

    // ============================================
    // TIL TANLASH
    // ============================================
    @Override
    public SendMessage handleLanguageSelection(Long chatId, String text, Optional<JobSeekerProfile> profileOpt) {
        String lang = null;
        if (text.equals("🇺🇿 O'zbek") || text.contains("O'zbek")) {
            lang = "uz";
        } else if (text.equals("🇷🇺 Русский") || text.contains("Русский")) {
            lang = "ru";
        } else if (text.equals("🇬🇧 English") || text.contains("English")) {
            lang = "en";
        } else {
            String msg = getText(profileOpt, chatId,
                    "❌ Пожалуйста, выберите язык:",
                    "❌ Iltimos, tilni tanlang:",
                    "❌ Please select a language:"
            );
            return createMessage(chatId, msg, keyboardService.getLanguageKeyboard());
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
            String msg;
            if ("ru".equals(lang)) {
                msg = "👤 **Регистрация в качестве соискателя:**\n\nВведите ваше имя и фамилию.\n💡 *Пример:* `Ali Valiyev`";
            } else if ("en".equals(lang)) {
                msg = "👤 **Registration as a job seeker:**\n\nPlease enter your first and last name.\n💡 *Example:* `Ali Valiyev`";
            } else {
                msg = "👤 **Ish izlovchi sifatida ro'yxatdan o'tish:**\n\nIltimos, ism va familiyangizni kiriting.\n💡 *Misol:* `Ali Valiyev`";
            }
            return createMessage(chatId, msg, (ReplyKeyboardMarkup) null);
        }

        JobSeekerState previous = stateManager.getPreviousState(chatId);
        stateManager.removePreviousState(chatId);

        if (previous != null) {
            stateManager.setState(chatId, previous);
            if (previous == JobSeekerState.SETTINGS_MENU) {
                String msg = getText(profileOpt, chatId,
                        "⚙️ **Раздел настроек:**",
                        "⚙️ **Sozlamalar bo'limi:**",
                        "⚙️ **Settings section:**"
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
                    "✅ Til O'zbek tiliga o'zgartirildi!",
                    "✅ Language changed to English!"
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
                    "❌ Karta raqami xato kiritildi! Iltimos, 16 xonali haqiqiy karta raqamini qaytadan kiriting:",
                    "❌ Invalid card number! Please re-enter a valid 16-digit card number:"
            );
            return createMessage(chatId, errorMsg, keyboardService.getCancelKeyboard(profileOpt));
        }

        stateManager.putData(chatId, "cardNumber", cardNumber);
        stateManager.setState(chatId, JobSeekerState.WAITING_FOR_CARD_HOLDER);

        String successMsg = getText(profileOpt, chatId,
                "👤 Введите имя владельца карты:\nНапример: ALI VALIYEV",
                "👤 Karta egasi ismini kiriting:\nMasalan: ALI VALIYEV",
                "👤 Enter the cardholder's name:\nE.g.: ALI VALIYEV"
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
                    "❌ Siz hali ro'yxatdan o'tmagansiz! Iltimos, /start bosing.",
                    "❌ You are not registered yet! Please press /start"
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
                    "✅ Karta muvaffaqiyatli qo'shildi!\n\n💳 Karta: " + maskedCard + "\n👤 Egasi: " + text.toUpperCase(),
                    "✅ Card successfully added!\n\n💳 Card: " + maskedCard + "\n👤 Holder: " + text.toUpperCase()
            );
            return createMessage(chatId, msg, keyboardService.getWalletKeyboard(profileOpt));
        } catch (Exception e) {
            log.error("❌ Karta qo'shishda xatolik: {}", e.getMessage(), e);
            stateManager.setState(chatId, JobSeekerState.WALLET_MENU);
            String msg = getText(profileOpt, chatId,
                    "❌ Ошибка при добавлении карты: " + e.getMessage(),
                    "❌ Karta qo'shishda xatolik: " + e.getMessage(),
                    "❌ Error adding card: " + e.getMessage()
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

        // ===== PAGINATION =====
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

        // ===== ORQAGA QAYTISH =====
        if ("back_to_categories".equals(callbackData)) {
            Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);
            String msg = getText(profileOpt, chatId,
                    "📂 **Выберите категорию для поиска:**",
                    "📂 **Kategoriyani tanlang:**",
                    "📂 **Select a category to search:**"
            );
            SendMessage response = new SendMessage();
            response.setChatId(chatId.toString());
            response.setText(msg);
            response.setParseMode("Markdown");
            response.setReplyMarkup(keyboardService.getCategoryInlineKeyboard(profileOpt));
            return response;
        }

        // ===== KATEGORIYA QIDIRISH =====
        if (callbackData != null && callbackData.startsWith("category_")) {
            String categoryKey = callbackData.substring("category_".length());
            Optional<JobSeekerProfile> profileOpt = jobSeekerProfileRepository.findByUserId(chatId);

            // PaginationService orqali qidirish
            SendMessage response = paginationService.handleVacancyPagination(chatId, categoryKey, 0, profileOpt);
            return response;
        }

        // ===== ARIZA TOPSHIRISH =====
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
                        "✅ Arizangiz muvaffaqiyatli topshirildi! Ish beruvchi javobini kuting.",
                        "✅ Your application has been submitted successfully! Please wait for the employer's response."
                );
                return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(seekerOpt));
            } else {
                String msg = getText(seekerOpt, chatId,
                        "❌ Вакансия или профиль не найдены!",
                        "❌ Vakansiya yoki profilingiz topilmadi!",
                        "❌ Vacancy or profile not found!"
                );
                return createMessage(chatId, msg, keyboardService.getMainMenuKeyboard(seekerOpt));
            }
        }

        return null;
    }

    @Override
    public SendMessage handleVacancyPagination(Long chatId, String categoryKey, int page, Optional<JobSeekerProfile> profileOpt) {
        return null;
    }

    @Override
    public ReplyKeyboard getCategoryInlineKeyboard(Optional<Object> empty) {
        return null;
    }

}