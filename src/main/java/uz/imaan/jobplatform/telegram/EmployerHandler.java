package uz.imaan.jobplatform.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;
import uz.imaan.jobplatform.employer.repository.EmployerRepository;
import uz.imaan.jobplatform.employer.state.EmployerState;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.entity.Subscription;
import uz.imaan.jobplatform.jobseeker.repository.JobApplicationRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.jobseeker.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EmployerHandler {

    private final EmployerRepository employerRepository;
    private final JobVacancyRepository jobVacancyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final Telegram telegram;

    private final Map<Long, EmployerState> states = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> draftData = new ConcurrentHashMap<>();

    // ============================================
    // VALIDATSIYA UCHUN REGEX
    // ============================================
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{7}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+998[0-9]{9}$");

    public EmployerHandler(
            EmployerRepository employerRepository,
            JobVacancyRepository jobVacancyRepository,
            SubscriptionRepository subscriptionRepository,
            JobSeekerProfileRepository jobSeekerProfileRepository,
            JobApplicationRepository jobApplicationRepository,
            @Lazy Telegram telegram) {
        this.employerRepository = employerRepository;
        this.jobVacancyRepository = jobVacancyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.telegram = telegram;
    }

    // ============================================
    // ASOSIY METOD
    // ============================================
    public SendMessage handleEmployer(Message message) {
        if (message == null) return null;

        Long chatId = message.getChatId();
        String text = message.hasText() ? message.getText().trim() : "";
        draftData.putIfAbsent(chatId, new ConcurrentHashMap<>());

        Optional<EmployerEntity> profileOpt = employerRepository.findByEmployerChatId(chatId);
        String lang = profileOpt.map(EmployerEntity::getLanguage).orElse("uz");

        // Ro'yxatdan o'tish zarurati
        boolean isRegistrationNeeded = profileOpt.isEmpty() ||
                profileOpt.get().getFullName() == null ||
                profileOpt.get().getFullName().isBlank() ||
                profileOpt.get().getFullName().toLowerCase().contains("employer");

        if (isRegistrationNeeded) {
            if (text.equals("🏠 Asosiy menyu") || text.equals("🏠 Главное меню") || text.equals("Asosiy menyu") || text.equals("/start")) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
            }
            return handleRegistration(chatId, text, message, lang, profileOpt);
        }

        // Global buyruqlar
        if (isGlobalButton(text)) {
            draftData.get(chatId).clear();
            states.put(chatId, EmployerState.MAIN_MENU);

            if (text.equals("🏠 Asosiy menyu") || text.equals("🏠 Главное меню") || text.equals("Asosiy menyu") || text.equals("/start")) {
                return createMessage(chatId,
                        getText(lang,
                                "🏠 **Xush kelibsiz!**\nIltimos, quyidagi bo'limlardan birini tanlang:",
                                "🏠 **Добро пожаловать!**\nПожалуйста, выберите один из разделов ниже:"),
                        getMainMenuKeyboard(lang));
            }

            if (text.equals("👤 Profilim") || text.equals("👤 Профиль")) {
                return showProfile(chatId, profileOpt, lang);
            }

            if (text.equals("✏️ Profilni tahrirlash") || text.equals("✏️ Редактировать профиль")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY);
                return createMessage(chatId,
                        getText(lang,
                                "🏢 **Kompaniya nomini kiriting:**\n\n(Agar yo'q bo'lsa 'Yo'q' deb yozing)",
                                "🏢 **Введите название компании:**\n\n(Если нет — напишите 'Нет')"),
                        getCancelKeyboard(lang));
            }

            if (text.equals("⚙️ Sozlamalar") || text.equals("⚙️ Настройки")) {
                states.put(chatId, EmployerState.SETTINGS_MENU);
                return createMessage(chatId,
                        getText(lang, "⚙️ **Sozlamalar bo'limi:**", "⚙️ **Раздел настроек:**"),
                        getSettingsKeyboard(lang));
            }

            if (text.equals("📢 Vakansiya yaratish") || text.equals("📢 Создать вакансию")) {
                states.put(chatId, EmployerState.WAITING_FOR_TITLE);
                return createMessage(chatId,
                        getText(lang,
                                "📝 **Ish sarlavhasini (lavozimni) kiriting:**\n\n*Misol:* Java Backend Dasturchi",
                                "📝 **Введите название вакансии (должность):**\n\n*Пример:* Java Backend Разработчик"),
                        getCancelKeyboard(lang));
            }

            if (text.equals("📋 Mening vakansiyalarim") || text.equals("📋 Мои вакансии")) {
                return showMyVacanciesWithButtons(chatId, lang);
            }

            if (text.equals("📩 Arizalar") || text.equals("📩 Заявки")) {
                states.put(chatId, EmployerState.VIEWING_APPLICATIONS);
                return showApplications(chatId, lang);
            }

            if (text.equals("📊 Statistika") || text.equals("📊 Статистика")) {
                states.put(chatId, EmployerState.VIEWING_STATISTICS);
                return showStatistics(chatId, lang);
            }

            if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("❌ Bekor qilish") || text.equals("❌ Отмена")) {
                return createMessage(chatId,
                        getText(lang, "📢 **Ish beruvchi menyusi:**", "📢 **Меню работодателя:**"),
                        getMainMenuKeyboard(lang));
            }
        }

        EmployerState state = states.getOrDefault(chatId, EmployerState.MAIN_MENU);

        // Sozlamalar
        if (state == EmployerState.SETTINGS_MENU) {
            if (text.equals("🌐 Til") || text.equals("🌐 Язык")) {
                states.put(chatId, EmployerState.WAITING_FOR_LANGUAGE);
                return createMessage(chatId, "🌐 **Tilni tanlang / Выберите язык:**", getLanguageKeyboard());
            }
            return createMessage(chatId,
                    getText(lang, "⚙️ **Sozlamalar bo'limi:**", "⚙️ **Раздел настроек:**"),
                    getSettingsKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_LANGUAGE) {
            if (text.equals("🇺🇿 O'zbek tili")) {
                saveLanguage(chatId, profileOpt, "uz");
                states.put(chatId, EmployerState.MAIN_MENU);
                return createMessage(chatId, "✅ Til muvaffaqiyatli O'zbek tiliga o'zgartirildi!", getMainMenuKeyboard("uz"));
            }
            if (text.equals("🇷🇺 Русский язык")) {
                saveLanguage(chatId, profileOpt, "ru");
                states.put(chatId, EmployerState.MAIN_MENU);
                return createMessage(chatId, "✅ Язык успешно изменен на Русский!", getMainMenuKeyboard("ru"));
            }
            states.put(chatId, EmployerState.SETTINGS_MENU);
            return createMessage(chatId, getText(lang, "⚙️ Sozlamalar", "⚙️ Настройки"), getSettingsKeyboard(lang));
        }

        // Kompaniya nomini tahrirlash
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY) {
            if (text == null || text.isBlank()) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Iltimos, kompaniya nomini kiriting:", "⚠️ Введите название компании:"),
                        getCancelKeyboard(lang));
            }
            if (profileOpt.isEmpty()) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId,
                        getText(lang, "❌ Profil topilmadi. Qaytadan ro'yxatdan o'ting.",
                                "❌ Профиль не найден. Зарегистрируйтесь снова."));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setCompanyName(
                    text.equalsIgnoreCase("Yo'q") || text.equalsIgnoreCase("Нет") ? null : text
            );
            employerRepository.save(entity);
            states.put(chatId, EmployerState.MAIN_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Kompaniya nomi yangilandi!", "✅ Название компании обновлено!"),
                    getMainMenuKeyboard(lang));
        }

        // Arizalarni ko'rish
        if (state == EmployerState.VIEWING_APPLICATIONS) {
            return showApplications(chatId, lang);
        }

        // Statistika
        if (state == EmployerState.VIEWING_STATISTICS) {
            return showStatistics(chatId, lang);
        }

        // Vakansiyani tahrirlash
        if (state == EmployerState.WAITING_FOR_EDIT_VACANCY_TITLE) {
            return handleEditVacancyField(chatId, text, "title", lang,
                    "Sarlavha 3-100 ta belgidan iborat bo'lishi kerak!",
                    "Заголовок должен содержать 3-100 символов!");
        }

        if (state == EmployerState.WAITING_FOR_EDIT_VACANCY_SALARY) {
            return handleEditVacancyField(chatId, text, "salary", lang,
                    "Maosh bo'sh bo'lmasligi kerak!",
                    "Зарплата не должна быть пустой!");
        }

        if (state == EmployerState.WAITING_FOR_EDIT_VACANCY_REQUIREMENTS) {
            return handleEditVacancyField(chatId, text, "requirements", lang,
                    "Talablar bo'sh bo'lmasligi kerak!",
                    "Требования не должны быть пустыми!");
        }

        if (state == EmployerState.WAITING_FOR_EDIT_VACANCY_WORK_HOURS) {
            return handleEditVacancyField(chatId, text, "workHours", lang,
                    "Ish vaqti bo'sh bo'lmasligi kerak!",
                    "Рабочие часы не должны быть пустыми!");
        }

        // Vakansiya yaratish
        if (state == EmployerState.WAITING_FOR_TITLE) {
            if (!isValidTitle(text)) {
                return createMessage(chatId,
                        getText(lang,
                                "⚠️ **Sarlavha noto'g'ri!** 3-100 ta belgi bo'lishi kerak. Qaytadan kiriting:",
                                "⚠️ **Неверный заголовок!** Должно быть 3-100 символов. Введите снова:"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("title", text);
            states.put(chatId, EmployerState.WAITING_FOR_CATEGORY);
            return createMessage(chatId,
                    getText(lang, "📁 **Kategoriyani tanlang:**", "📁 **Выберите категорию:**"),
                    getCategoryKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_CATEGORY) {
            if (!isValidCategory(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ **Kategoriya nomini tugma orqali tanlang:**", "⚠️ **Выберите категорию с помощью кнопок:**"),
                        getCategoryKeyboard(lang));
            }
            draftData.get(chatId).put("category", text);
            states.put(chatId, EmployerState.WAITING_FOR_JOB_TYPE);
            return createMessage(chatId,
                    getText(lang, "⏰ **Ish turini tanlang:**", "⏰ **Выберите тип работы:**"),
                    getTypeKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_CUSTOM_CATEGORY) {
            if (!isValidTitle(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ **Kategoriya juda qisqa, qaytadan kiriting:**", "⚠️ **Категория слишком короткая, введите снова:**"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("category", text);
            states.put(chatId, EmployerState.WAITING_FOR_JOB_TYPE);
            return createMessage(chatId,
                    getText(lang, "⏰ **Ish turini tanlang:**", "⏰ **Выберите тип работы:**"),
                    getTypeKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_JOB_TYPE) {
            if (!isValidJobType(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ **Tugmalardan birini tanlang:**", "⚠️ **Выберите одну из кнопок:**"),
                        getTypeKeyboard(lang));
            }
            draftData.get(chatId).put("type", text);
            states.put(chatId, EmployerState.WAITING_FOR_WORK_HOURS);
            return createMessage(chatId,
                    getText(lang, "⌛️ **Ish soatlarini tanlang (yoki kiriting):**", "⌛️ **Выберите часы работы (или введите):**"),
                    getWorkHoursKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_WORK_HOURS) {
            if (!isValidWorkHours(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ **Ish soati noto'g'ri! Iltimos qaytadan kiriting:**", "⚠️ **Неверные часы! Пожалуйста, введите снова:**"),
                        getWorkHoursKeyboard(lang));
            }
            draftData.get(chatId).put("workHours", text);
            states.put(chatId, EmployerState.WAITING_FOR_WORKER_COUNT);
            return createMessage(chatId,
                    getText(lang, "👥 **Nechta xodim kerak? (Raqam kiriting):**", "👥 **Сколько сотрудников нужно? (Введите число):**"),
                    getCancelKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_WORKER_COUNT) {
            if (!isValidWorkerCount(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ **Faqat musbat raqam kiriting (masalan, 1 yoki 5):**", "⚠️ **Введите только положительное число (например, 1 или 5):**"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("workerCount", text);
            states.put(chatId, EmployerState.WAITING_FOR_SALARY);
            return createMessage(chatId,
                    getText(lang, "💰 **Maoshni kiriting yoki \"Kelishiladi\" tugmasini bosing:**", "💰 **Введите зарплату или нажмите \"Договорная\":**"),
                    getSalaryQuickKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_SALARY) {
            if (!isValidSalary(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ **Maosh noto'g'ri. Qaytadan kiriting:**", "⚠️ **Неверная зарплата. Введите снова:**"),
                        getSalaryQuickKeyboard(lang));
            }
            draftData.get(chatId).put("salary", text);
            states.put(chatId, EmployerState.WAITING_FOR_REQUIREMENTS);
            return createMessage(chatId,
                    getText(lang, "📌 **Talablarni kiriting (yoki O'tkazib yuborish):**", "📌 **Введите требования (или Пропустить):**"),
                    getSkipKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_REQUIREMENTS) {
            String reqText = isSkip(text) ? (lang.equals("ru") ? "Не указано" : "Ko'rsatilmadi") : text;
            draftData.get(chatId).put("requirements", reqText);
            states.put(chatId, EmployerState.WAITING_FOR_PHONE);
            return createMessage(chatId,
                    getText(lang, "📞 **Telefon raqamingizni yuboring:**", "📞 **Отправьте номер телефона:**"),
                    getContactKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_PHONE) {
            String phoneText = message.hasContact() ? message.getContact().getPhoneNumber() : text;

            if (!isValidPhone(phoneText)) {
                return createMessage(chatId,
                        getText(lang,
                                "⚠️ **Telefon raqam noto'g'ri kiritildi!**\n\nMasalan: `+998901234567` formatida kiriting.",
                                "⚠️ **Некорректный номер телефона!**\n\nВведите в формате `+998901234567`."),
                        getContactKeyboard(lang));
            }

            Map<String, String> data = draftData.getOrDefault(chatId, Map.of());

            JobVacancy vacancy = new JobVacancy();
            vacancy.setEmployerChatId(chatId);
            vacancy.setTitle(data.getOrDefault("title", "Ko'rsatilmadi"));
            vacancy.setCategory(data.getOrDefault("category", "Boshqa"));
            vacancy.setType(data.getOrDefault("type", "To'liq bandlik"));
            vacancy.setSalary(data.getOrDefault("salary", "Kelishiladi"));
            vacancy.setWorkHours(data.getOrDefault("workHours", "8 soat"));

            String countStr = data.getOrDefault("workerCount", "1");
            vacancy.setWorkerCount(countStr.matches("\\d+") ? Integer.parseInt(countStr) : 1);

            vacancy.setRequirements(data.getOrDefault("requirements", "Ko'rsatilmadi"));
            vacancy.setPhoneNumber(phoneText);
            vacancy.setIsActive(true);
            vacancy.setCompanyName(profileOpt.map(EmployerEntity::getCompanyName).orElse(null));

            jobVacancyRepository.save(vacancy);

            try {
                String postedCategory = vacancy.getCategory();
                List<Subscription> subscribers = subscriptionRepository.findByCategory(postedCategory);

                for (Subscription sub : subscribers) {
                    Long seekerUserId = sub.getUserId();

                    String notificationText = String.format(
                            "🆕 **Yangi ish qo'shildi!**\n\n" +
                                    "📌 **Vakansiya:** %s\n" +
                                    "📂 **Kategoriya:** %s\n" +
                                    "⏰ **Ish turi:** %s\n" +
                                    "💰 **Maosh:** %s\n" +
                                    "📞 **Tel:** %s",
                            vacancy.getTitle(),
                            vacancy.getCategory() != null ? vacancy.getCategory() : "Ko'rsatilmagan",
                            vacancy.getType() != null ? vacancy.getType() : "To'liq kun",
                            vacancy.getSalary() != null ? vacancy.getSalary() : "Kelishiladi",
                            vacancy.getPhoneNumber() != null ? vacancy.getPhoneNumber() : "Ko'rsatilmagan"
                    );

                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    InlineKeyboardButton applyBtn = new InlineKeyboardButton("📩 Ariza topshirish");
                    applyBtn.setCallbackData("apply_" + vacancy.getId());
                    row.add(applyBtn);
                    rows.add(row);
                    markup.setKeyboard(rows);

                    SendMessage notificationMsg = new SendMessage(seekerUserId.toString(), notificationText);
                    notificationMsg.setParseMode("Markdown");
                    notificationMsg.setReplyMarkup(markup);

                    try {
                        telegram.execute(notificationMsg);
                    } catch (Exception e) {
                        log.error("❌ Xabar yuborishda xatolik: userId={}", seekerUserId);
                    }
                }
            } catch (Exception e) {
                log.error("❌ Obunachilarga xabar yuborishda umumiy xatolik", e);
            }

            states.put(chatId, EmployerState.MAIN_MENU);
            draftData.get(chatId).clear();

            String successMsg = lang.equals("ru") ?
                    String.format("🎉 **Ваша вакансия успешно опубликована!**\n\n📌 **Название:** %s\n📁 **Категория:** %s\n⏰ **Тип:** %s\n⌛️ **Часы:** %s\n👥 **Количество:** %s\n💰 **Зарплата:** %s\n📞 **Тел:** %s",
                            vacancy.getTitle(), vacancy.getCategory(), vacancy.getType(), vacancy.getWorkHours(), vacancy.getWorkerCount(), vacancy.getSalary(), vacancy.getPhoneNumber()) :
                    String.format("🎉 **E'loningiz muvaffaqiyatli chop etildi!**\n\n📌 **Nomi:** %s\n📁 **Kategoriya:** %s\n⏰ **Turi:** %s\n⌛️ **Vaqti:** %s\n👥 **Xodimlar:** %s ta\n💰 **Maosh:** %s\n📞 **Tel:** %s",
                            vacancy.getTitle(), vacancy.getCategory(), vacancy.getType(), vacancy.getWorkHours(), vacancy.getWorkerCount(), vacancy.getSalary(), vacancy.getPhoneNumber());

            return createMessage(chatId, successMsg, getMainMenuKeyboard(lang));
        }

        return createMessage(chatId,
                getText(lang,
                        "📢 **Ish beruvchi menyusi:**\nKerakli bo'limni tanlang:",
                        "📢 **Меню работодателя:**\nВыберите нужный раздел:"),
                getMainMenuKeyboard(lang));
    }

    // ============================================
    // CALLBACK QUERY HANDLER
    // ============================================
    public SendMessage handleCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getMessage() == null) return null;

        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String data = callbackQuery.getData();
        draftData.putIfAbsent(chatId, new ConcurrentHashMap<>());

        Optional<EmployerEntity> profileOpt = employerRepository.findByEmployerChatId(chatId);
        String lang = profileOpt.map(EmployerEntity::getLanguage).orElse("uz");

        EmployerState state = states.getOrDefault(chatId, EmployerState.MAIN_MENU);

        // Kategoriya tanlash
        if (state == EmployerState.WAITING_FOR_CATEGORY && data != null && data.startsWith("emp_cat_")) {
            String catValue = data.substring("emp_cat_".length());

            if (catValue.equals("OTHER")) {
                states.put(chatId, EmployerState.WAITING_FOR_CUSTOM_CATEGORY);
                return createMessage(chatId,
                        getText(lang, "📝 **Kategoriya nomini qo'lda kiriting:**", "📝 **Введите название категории вручную:**"),
                        getCancelKeyboard(lang));
            }

            if (catValue.equals("MENU")) {
                states.put(chatId, EmployerState.MAIN_MENU);
                return createMessage(chatId,
                        getText(lang, "📢 **Ish beruvchi menyusi:**", "📢 **Меню работодателя:**"),
                        getMainMenuKeyboard(lang));
            }

            draftData.get(chatId).put("category", catValue);
            states.put(chatId, EmployerState.WAITING_FOR_JOB_TYPE);
            return createMessage(chatId,
                    getText(lang, "⏰ **Ish turini tanlang:**", "⏰ **Выберите тип работы:**"),
                    getTypeKeyboard(lang));
        }

        // ===== YANGI: Vakansiyani arxivlash/faollashtirish =====
        if (data != null && data.startsWith("vac_toggle_")) {
            Long vacancyId = Long.parseLong(data.replace("vac_toggle_", ""));
            Optional<JobVacancy> vacOpt = jobVacancyRepository.findByIdAndEmployerChatId(vacancyId, chatId);
            if (vacOpt.isEmpty()) {
                sendAnswerCallback(callbackQuery.getId(),
                        getText(lang, "❌ Vakansiya topilmadi!", "❌ Вакансия не найдена!"), true);
                return null;
            }
            JobVacancy vac = vacOpt.get();
            boolean newStatus = vac.getIsActive() == null || !vac.getIsActive();
            vac.setIsActive(newStatus);
            jobVacancyRepository.save(vac);

            sendAnswerCallback(callbackQuery.getId(),
                    newStatus
                            ? getText(lang, "✅ Vakansiya faollashtirildi!", "✅ Вакансия активирована!")
                            : getText(lang, "🔴 Vakansiya arxivlandi!", "🔴 Вакансия архивирована!"),
                    false);

            // Xabarni yangilash
            SendMessage updated = showMyVacanciesWithButtons(chatId, lang);
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(updated.getText());
            edit.setParseMode("Markdown");
            edit.setReplyMarkup((InlineKeyboardMarkup) updated.getReplyMarkup());
            try {
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        // Vakansiyani tahrirlash
        if (data != null && data.startsWith("vac_edit_") && !data.startsWith("vac_edit_field_")) {
            Long vacancyId = Long.parseLong(data.replace("vac_edit_", ""));
            draftData.get(chatId).put("edit_vacancy_id", String.valueOf(vacancyId));
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang,
                    "✏️ **Vakansiyani tahrirlash:**\n\nQaysi maydonni o'zgartirmoqchisiz?",
                    "✏️ **Редактировать вакансию:**\n\nКакое поле хотите изменить?"));
            edit.setParseMode("Markdown");
            edit.setReplyMarkup(buildEditVacancyKeyboard(vacancyId, lang));
            try {
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        // Vakansiya maydonini tahrirlash
        if (data != null && data.startsWith("vac_edit_field_")) {
            String[] parts = data.replace("vac_edit_field_", "").split("_");
            String field = parts[0];
            Long vacancyId = Long.parseLong(parts[1]);
            draftData.get(chatId).put("edit_vacancy_id", String.valueOf(vacancyId));
            draftData.get(chatId).put("edit_field", field);

            String promptText;
            switch (field) {
                case "title" -> {
                    states.put(chatId, EmployerState.WAITING_FOR_EDIT_VACANCY_TITLE);
                    promptText = getText(lang, "📝 **Yangi sarlavhani kiriting:**", "📝 **Введите новый заголовок:**");
                }
                case "salary" -> {
                    states.put(chatId, EmployerState.WAITING_FOR_EDIT_VACANCY_SALARY);
                    promptText = getText(lang, "💰 **Yangi maoshni kiriting:**", "💰 **Введите новую зарплату:**");
                }
                case "requirements" -> {
                    states.put(chatId, EmployerState.WAITING_FOR_EDIT_VACANCY_REQUIREMENTS);
                    promptText = getText(lang, "📋 **Yangi talablarni kiriting:**", "📋 **Введите новые требования:**");
                }
                case "workHours" -> {
                    states.put(chatId, EmployerState.WAITING_FOR_EDIT_VACANCY_WORK_HOURS);
                    promptText = getText(lang, "⏰ **Yangi ish vaqtini kiriting:**", "⏰ **Введите новые рабочие часы:**");
                }
                default -> {
                    return null;
                }
            }
            return createMessage(chatId, promptText, getCancelKeyboard(lang));
        }

        // Vakansiyani o'chirish
        if (data != null && data.startsWith("vac_delete_") && !data.startsWith("vac_delete_confirm_") && !data.startsWith("vac_delete_cancel")) {
            Long vacancyId = Long.parseLong(data.replace("vac_delete_", ""));

            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang,
                    "🗑 **Vakansiyani o'chirishni tasdiqlaysizmi?**\n\nBu amalni qaytarib bo'lmaydi!",
                    "🗑 **Подтвердите удаление вакансии?**\n\nЭто действие нельзя отменить!"));
            edit.setParseMode("Markdown");
            edit.setReplyMarkup(buildDeleteConfirmKeyboard(vacancyId, lang));
            try {
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        if (data != null && data.startsWith("vac_delete_confirm_")) {
            Long vacancyId = Long.parseLong(data.replace("vac_delete_confirm_", ""));

            Optional<JobVacancy> vacOpt = jobVacancyRepository.findByIdAndEmployerChatId(vacancyId, chatId);
            if (vacOpt.isEmpty()) {
                sendAnswerCallback(callbackQuery.getId(), getText(lang, "❌ Vakansiya topilmadi!", "❌ Вакансия не найдена!"), true);
                return null;
            }

            jobVacancyRepository.delete(vacOpt.get());

            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang, "✅ **Vakansiya o'chirildi!**", "✅ **Вакансия удалена!**"));
            edit.setParseMode("Markdown");
            edit.setReplyMarkup(null);
            try {
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        if ("vac_delete_cancel".equals(data)) {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang, "❌ **O'chirish bekor qilindi.**", "❌ **Удаление отменено.**"));
            edit.setParseMode("Markdown");
            edit.setReplyMarkup(null);
            try {
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        // Ariza statusini o'zgartirish
        if (data != null && data.startsWith("emp_app_status_")) {
            String[] parts = data.replace("emp_app_status_", "").split("_");
            String action = parts[0];
            Long appId = Long.parseLong(parts[1]);

            Optional<JobApplication> appOpt = jobApplicationRepository.findById(appId);
            if (appOpt.isEmpty()) {
                sendAnswerCallback(callbackQuery.getId(),
                        getText(lang, "❌ Ariza topilmadi!", "❌ Заявка не найдена!"), true);
                return null;
            }

            JobApplication app = appOpt.get();

            boolean belongsToEmployer = jobVacancyRepository.findByIdAndEmployerChatId(app.getJobId(), chatId).isPresent();
            if (!belongsToEmployer) {
                sendAnswerCallback(callbackQuery.getId(),
                        getText(lang, "❌ Bu ariza sizga tegishli emas!", "❌ Эта заявка вам не принадлежит!"), true);
                return null;
            }

            JobApplication.ApplicationStatus newStatus = switch (action) {
                case "view" -> JobApplication.ApplicationStatus.VIEWED;
                case "interview" -> JobApplication.ApplicationStatus.INTERVIEW;
                case "accept" -> JobApplication.ApplicationStatus.ACCEPTED;
                case "reject" -> JobApplication.ApplicationStatus.REJECTED;
                default -> app.getStatus();
            };

            app.setStatus(newStatus);
            jobApplicationRepository.save(app);

            sendAnswerCallback(callbackQuery.getId(),
                    getText(lang,
                            "✅ Ariza holati yangilandi: " + newStatus.name(),
                            "✅ Статус заявки обновлён: " + newStatus.name()),
                    false);

            // Nomzodga xabar
            try {
                Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findById(app.getJobSeekerId());
                if (seekerOpt.isPresent()) {
                    Long seekerChatId = seekerOpt.get().getUserId();
                    String jobTitle = jobVacancyRepository.findById(app.getJobId())
                            .map(JobVacancy::getTitle).orElse("Vakansiya");

                    String notificationText;
                    switch (newStatus) {
                        case VIEWED -> notificationText = getText(lang,
                                "👁 Arizangiz ko'rib chiqildi: «" + jobTitle + "»",
                                "👁 Ваша заявка просмотрена: «" + jobTitle + "»");
                        case INTERVIEW -> notificationText = getText(lang,
                                "📅 Siz suhbatga chaqirildingiz: «" + jobTitle + "»",
                                "📅 Вас пригласили на собеседование: «" + jobTitle + "»");
                        case ACCEPTED -> notificationText = getText(lang,
                                "🎉 Tabriklaymiz! Siz ishga qabul qilindingiz: «" + jobTitle + "»",
                                "🎉 Поздравляем! Вы приняты на работу: «" + jobTitle + "»");
                        case REJECTED -> notificationText = getText(lang,
                                "😔 Afsuski, arizangiz rad etildi: «" + jobTitle + "»",
                                "😔 К сожалению, ваша заявка отклонена: «" + jobTitle + "»");
                        default -> notificationText = "";
                    }

                    if (!notificationText.isEmpty()) {
                        SendMessage notifyMsg = new SendMessage(seekerChatId.toString(), notificationText);
                        notifyMsg.setParseMode("Markdown");
                        telegram.execute(notifyMsg);
                    }
                }
            } catch (Exception e) {
                log.error("❌ Nomzodga xabar yuborishda xatolik", e);
            }

            // Xabarni yangilash
            try {
                SendMessage updated = showApplications(chatId, lang);
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                edit.setText(updated.getText());
                edit.setParseMode("Markdown");
                edit.setReplyMarkup((InlineKeyboardMarkup) updated.getReplyMarkup());
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Xabarni yangilashda xatolik", e);
            }

            return null;
        }

        // Eski accept/reject (bildirishnoma orqali)
        if (data != null && (data.startsWith("accept_app_") || data.startsWith("reject_app_"))) {
            boolean isAccept = data.startsWith("accept_app_");
            String[] parts = data.split("_");

            String originalText = "";
            if (callbackQuery.getMessage() instanceof org.telegram.telegrambots.meta.api.objects.Message msg) {
                originalText = msg.hasText() ? msg.getText() : "";
            }

            String statusMessage = isAccept ?
                    (lang.equals("ru") ? "✅ **Статус: Заявка принята**" : "✅ **Holat: Ariza qabul qilindi**") :
                    (lang.equals("ru") ? "❌ **Статус: Заявка отклонена**" : "❌ **Holat: Ariza rad etildi**");

            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(originalText + "\n\n" + statusMessage);
            edit.setParseMode("Markdown");
            edit.setReplyMarkup(null);
            try {
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Xabarni tahrirlashda xatolik", e);
            }

            if (parts.length >= 4) {
                try {
                    Long employeeChatId = Long.parseLong(parts[3]);
                    if (isAccept) {
                        Optional<EmployerEntity> employerOpt = employerRepository.findByEmployerChatId(chatId);
                        Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findByUserId(employeeChatId);

                        if (employerOpt.isPresent() && seekerOpt.isPresent()) {
                            EmployerEntity employer = employerOpt.get();
                            JobSeekerProfile seeker = seekerOpt.get();

                            String seekerInfo = lang.equals("ru") ?
                                    String.format("👤 **Анкета кандидата:**\n\n👤 **ФИО:** %s\n📞 **Телефон:** %s\n💼 **Профессия:** %s",
                                            seeker.getFullName(), seeker.getPhoneNumber(),
                                            (seeker.getProfession() != null ? seeker.getProfession() : "Не указано")) :
                                    String.format("👤 **Nomzod anketasi:**\n\n👤 **F.I.O:** %s\n📞 **Telefon:** %s\n💼 **Kasbi:** %s",
                                            seeker.getFullName(), seeker.getPhoneNumber(),
                                            (seeker.getProfession() != null ? seeker.getProfession() : "Ko'rsatilmagan"));

                            SendMessage employerInfoMsg = new SendMessage(chatId.toString(), seekerInfo);
                            employerInfoMsg.setParseMode("Markdown");
                            telegram.execute(employerInfoMsg);

                            String employerInfo = String.format("🎉 **Arizangiz qabul qilindi!**\n\n🏢 **Ish beruvchi:** %s\n📞 **Telefon:** %s",
                                    employer.getFullName(),
                                    employer.getPhoneNumber());

                            SendMessage seekerInfoMsg = new SendMessage(employeeChatId.toString(), employerInfo);
                            seekerInfoMsg.setParseMode("Markdown");
                            telegram.execute(seekerInfoMsg);
                        }
                    } else {
                        String seekerMsgText = (lang.equals("ru") ? "⚠️ К сожалению, ваша заявка была отклонена работодателем." : "⚠️ Afsuski, arizangiz ish beruvchi tomonidan rad etildi.");
                        SendMessage seekerMsg = new SendMessage(employeeChatId.toString(), seekerMsgText);
                        telegram.execute(seekerMsg);
                    }
                } catch (Exception e) {
                    log.error("❌ Nomzodga xabar yuborishda xatolik", e);
                }
            }
            return null;
        }

        return null;
    }

    // ============================================
    // STATISTIKA
    // ============================================
    private SendMessage showStatistics(Long chatId, String lang) {
        List<JobVacancy> myVacancies = jobVacancyRepository.findAllByEmployerChatId(chatId);
        List<JobApplication> allApps = jobApplicationRepository.findAllByEmployerChatId(chatId);

        // Umumiy statistika
        long totalVacancies = myVacancies.size();
        long activeVacancies = myVacancies.stream()
                .filter(v -> v.getIsActive() != null && v.getIsActive()).count();
        long archivedVacancies = totalVacancies - activeVacancies;
        long totalApplications = allApps.size();

        long pending = allApps.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.PENDING).count();
        long viewed = allApps.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.VIEWED).count();
        long interview = allApps.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.INTERVIEW).count();
        long accepted = allApps.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.ACCEPTED).count();
        long rejected = allApps.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.REJECTED).count();

        // Eng ko'p ariza kelgan TOP 3 vakansiya
        Map<Long, Long> appCountByVacancy = allApps.stream()
                .collect(Collectors.groupingBy(JobApplication::getJobId, Collectors.counting()));

        List<Map.Entry<Long, Long>> topVacancies = appCountByVacancy.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append(lang.equals("ru") ? "📊 **Статистика работодателя:**\n\n" : "📊 **Ish beruvchi statistikasi:**\n\n");

        sb.append(lang.equals("ru") ? "📋 **Вакансии:**\n" : "📋 **Vakansiyalar:**\n");
        sb.append("   📌 ").append(lang.equals("ru") ? "Всего" : "Jami").append(": ").append(totalVacancies).append("\n");
        sb.append("   🟢 ").append(lang.equals("ru") ? "Активных" : "Faol").append(": ").append(activeVacancies).append("\n");
        sb.append("   🔴 ").append(lang.equals("ru") ? "Архив" : "Arxiv").append(": ").append(archivedVacancies).append("\n\n");

        sb.append(lang.equals("ru") ? "📩 **Заявки:**\n" : "📩 **Arizalar:**\n");
        sb.append("   📊 ").append(lang.equals("ru") ? "Всего" : "Jami").append(": ").append(totalApplications).append("\n");
        sb.append("   ⏳ ").append(lang.equals("ru") ? "Ожидание" : "Kutilmoqda").append(": ").append(pending).append("\n");
        sb.append("   👁 ").append(lang.equals("ru") ? "Просмотрено" : "Ko'rib chiqilgan").append(": ").append(viewed).append("\n");
        sb.append("   📅 ").append(lang.equals("ru") ? "Собеседование" : "Suhbat").append(": ").append(interview).append("\n");
        sb.append("   ✅ ").append(lang.equals("ru") ? "Принято" : "Qabul qilingan").append(": ").append(accepted).append("\n");
        sb.append("   ❌ ").append(lang.equals("ru") ? "Отклонено" : "Rad etilgan").append(": ").append(rejected).append("\n\n");

        if (!topVacancies.isEmpty()) {
            sb.append(lang.equals("ru") ? "🏆 **ТОП-3 по заявкам:**\n" : "🏆 **Eng ko'p ariza (TOP-3):**\n");
            int rank = 1;
            for (Map.Entry<Long, Long> entry : topVacancies) {
                String title = jobVacancyRepository.findById(entry.getKey())
                        .map(JobVacancy::getTitle).orElse("Vakansiya #" + entry.getKey());
                sb.append("   ").append(rank++).append(". ").append(title)
                        .append(" — ").append(entry.getValue()).append(lang.equals("ru") ? " заявок" : " ta ariza").append("\n");
            }
        } else {
            sb.append(lang.equals("ru") ? "ℹ️ Пока нет заявок.\n" : "ℹ️ Hozircha arizalar yo'q.\n");
        }

        return createMessage(chatId, sb.toString(), getMainMenuKeyboard(lang));
    }

    // ============================================
    // ARIZALARNI KO'RSATISH
    // ============================================
    private SendMessage showApplications(Long chatId, String lang) {
        List<JobApplication> applications = jobApplicationRepository.findAllByEmployerChatId(chatId);

        if (applications == null || applications.isEmpty()) {
            String emptyMsg = lang.equals("ru")
                    ? "📭 У вас пока нет полученных заявок."
                    : "📭 Sizda hozircha kelgan arizalar yo'q.";
            return createMessage(chatId, emptyMsg, getMainMenuKeyboard(lang));
        }

        StringBuilder sb = new StringBuilder(lang.equals("ru")
                ? "📩 **Ваши заявки:**\n\n"
                : "📩 **Sizning arizalaringiz:**\n\n");

        long pending = applications.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.PENDING).count();
        long viewed = applications.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.VIEWED).count();
        long interview = applications.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.INTERVIEW).count();
        long accepted = applications.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.ACCEPTED).count();
        long rejected = applications.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.REJECTED).count();

        sb.append(lang.equals("ru") ? "📊 **Статистика:**\n" : "📊 **Statistika:**\n");
        sb.append("⏳ ").append(lang.equals("ru") ? "Ожидание" : "Kutilmoqda").append(": ").append(pending).append("\n");
        sb.append("👁 ").append(lang.equals("ru") ? "Просмотрено" : "Ko'rib chiqilgan").append(": ").append(viewed).append("\n");
        sb.append("📅 ").append(lang.equals("ru") ? "Собеседование" : "Suhbat").append(": ").append(interview).append("\n");
        sb.append("✅ ").append(lang.equals("ru") ? "Принято" : "Qabul qilingan").append(": ").append(accepted).append("\n");
        sb.append("❌ ").append(lang.equals("ru") ? "Отклонено" : "Rad etilgan").append(": ").append(rejected).append("\n\n");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < applications.size(); i++) {
            JobApplication app = applications.get(i);

            String jobTitle = jobVacancyRepository.findById(app.getJobId())
                    .map(JobVacancy::getTitle).orElse("Vakansiya #" + app.getJobId());

            Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findById(app.getJobSeekerId());
            String candidateName = seekerOpt.map(JobSeekerProfile::getFullName).orElse("Nomzod");

            sb.append("\n").append(i + 1).append(". 📌 **").append(jobTitle).append("**\n");
            sb.append("   👤 ").append(candidateName).append("\n");
            sb.append("   📊 ").append(getStatusText(app.getStatus(), lang)).append("\n");

            List<InlineKeyboardButton> statusRow = new ArrayList<>();

            InlineKeyboardButton viewBtn = new InlineKeyboardButton();
            viewBtn.setText("👁");
            viewBtn.setCallbackData("emp_app_status_view_" + app.getId());
            statusRow.add(viewBtn);

            InlineKeyboardButton interviewBtn = new InlineKeyboardButton();
            interviewBtn.setText("📅");
            interviewBtn.setCallbackData("emp_app_status_interview_" + app.getId());
            statusRow.add(interviewBtn);

            InlineKeyboardButton acceptBtn = new InlineKeyboardButton();
            acceptBtn.setText("✅");
            acceptBtn.setCallbackData("emp_app_status_accept_" + app.getId());
            statusRow.add(acceptBtn);

            InlineKeyboardButton rejectBtn = new InlineKeyboardButton();
            rejectBtn.setText("❌");
            rejectBtn.setCallbackData("emp_app_status_reject_" + app.getId());
            statusRow.add(rejectBtn);

            rows.add(statusRow);
        }

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        return message;
    }

    private String getStatusText(JobApplication.ApplicationStatus status, String lang) {
        if (lang.equals("ru")) {
            return switch (status) {
                case PENDING -> "⏳ Ожидание";
                case VIEWED -> "👁 Просмотрено";
                case INTERVIEW -> "📅 Собеседование";
                case ACCEPTED -> "✅ Принято";
                case REJECTED -> "❌ Отклонено";
                case CANCELLED -> "🚫 Отменено";
            };
        }
        return switch (status) {
            case PENDING -> "⏳ Kutilmoqda";
            case VIEWED -> "👁 Ko'rib chiqilgan";
            case INTERVIEW -> "📅 Suhbat";
            case ACCEPTED -> "✅ Qabul qilingan";
            case REJECTED -> "❌ Rad etilgan";
            case CANCELLED -> "🚫 Bekor qilingan";
        };
    }

    // ============================================
    // RO'YXATDAN O'TISH (KOMPANIYA BILAN)
    // ============================================
    private SendMessage handleRegistration(Long chatId, String text, Message message, String lang, Optional<EmployerEntity> profileOpt) {
        EmployerState regState = states.getOrDefault(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);

        if (regState == EmployerState.WAITING_FOR_REG_FULL_NAME) {
            if (text.isEmpty() || text.startsWith("/") || text.equals("🏠 Asosiy menyu") || text.equals("🏠 Главное меню")
                    || text.toLowerCase().contains("ish beruvchi") || text.toLowerCase().contains("employer")) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId,
                        getText(lang,
                                "👤 **Ish beruvchi sifatida ro'yxatdan o'tish:**\n\nIltimos, ism va familiyangizni kiriting.\n💡 *Misol:* Shoxrux Sultanbayev",
                                "👤 **Регистрация в качестве работодателя:**\n\nПожалуйста, введите ваше имя и фамилию.\n💡 *Пример:* Шохрух Султанбаев"));
            }
            draftData.get(chatId).put("reg_fio", text);
            states.put(chatId, EmployerState.WAITING_FOR_REG_AGE);
            return createMessage(chatId,
                    getText(lang, "🎂 **Yoshingizni kiriting (18 dan katta):**\n\n💡 *Misol:* 25", "🎂 **Введите ваш возраст (не менее 18):**\n\n💡 *Пример:* 25"),
                    getCancelKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_AGE) {
            if (!isValidAge(text)) {
                return createMessage(chatId,
                        getText(lang,
                                "⚠️ **Yoshingiz 18 yoshdan katta bo'lishi kerak!**\n\nQaytadan kiriting:",
                                "⚠️ **Ваш возраст должен быть не менее 18 лет!**\n\nВведите снова:"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("reg_age", text);
            states.put(chatId, EmployerState.WAITING_FOR_REG_PASSPORT);
            return createMessage(chatId,
                    getText(lang,
                            "🪪 **Pasport seriya va raqamingizni kiriting (masalan: AA1234567):**\n\n⚠️ 2 ta katta harf va 7 ta raqam bo'lishi shart.",
                            "🪪 **Введите серию и номер паспорта (например: AA1234567):**\n\n⚠️ 2 заглавные буквы и 7 цифр."),
                    getCancelKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_PASSPORT) {
            if (!PASSPORT_PATTERN.matcher(text.trim()).matches()) {
                return createMessage(chatId,
                        getText(lang,
                                "⚠️ **Pasport formati noto'g'ri!**\n\n2 ta katta harf va 7 ta raqam bo'lishi kerak (masalan: AA1234567).",
                                "⚠️ **Неверный формат паспорта!**\n\n2 заглавные буквы и 7 цифр (например: AA1234567)."),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("reg_passport", text);
            states.put(chatId, EmployerState.WAITING_FOR_REG_PHONE);
            return createMessage(chatId,
                    getText(lang,
                            "📞 **Telefon raqamingizni yuboring (format +998901234567):**",
                            "📞 **Отправьте номер телефона (формат +998901234567):**"),
                    getContactKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_PHONE) {
            String phone = message.hasContact() ? message.getContact().getPhoneNumber() : text;
            if (!isValidPhone(phone)) {
                return createMessage(chatId,
                        getText(lang,
                                "⚠️ **Telefon raqam noto'g'ri!**\n\n+998 bilan boshlanib, 9 ta raqam bo'lishi kerak (masalan: +998901234567).",
                                "⚠️ **Неверный номер телефона!**\n\nДолжен начинаться с +998 и содержать 9 цифр (например: +998901234567)."),
                        getContactKeyboard(lang));
            }
            draftData.get(chatId).put("reg_phone", phone);
            states.put(chatId, EmployerState.WAITING_FOR_REG_COMPANY);  // YANGI
            return createMessage(chatId,
                    getText(lang,
                            "🏢 **Kompaniya nomini kiriting:**\n\n(Agar sizda kompaniya yo'q bo'lsa, 'Yo'q' deb yozing)\n💡 *Misol:* IT Company MChJ",
                            "🏢 **Введите название компании:**\n\n(Если у вас нет компании, напишите 'Нет')\n💡 *Пример:* IT Company MChJ"),
                    getCancelKeyboard(lang));
        }

        // ===== YANGI: Kompaniya nomi =====
        if (regState == EmployerState.WAITING_FOR_REG_COMPANY) {
            if (text == null || text.isBlank()) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Iltimos, kompaniya nomini kiriting (yoki 'Yo'q' deb yozing):",
                                "⚠️ Введите название компании (или напишите 'Нет'):"),
                        getCancelKeyboard(lang));
            }

            Map<String, String> data = draftData.get(chatId);
            EmployerEntity entity = profileOpt.orElseGet(() -> EmployerEntity.builder()
                    .employerChatId(chatId)
                    .language(lang)
                    .createdAt(LocalDateTime.now())
                    .build());

            entity.setFullName(data.get("reg_fio"));
            try {
                entity.setAge(Integer.parseInt(data.get("reg_age")));
            } catch (Exception e) {
                entity.setAge(null);
            }
            entity.setPassportSeriesNumber(data.get("reg_passport"));
            entity.setPhoneNumber(data.get("reg_phone"));

            String companyName = text.equalsIgnoreCase("Yo'q") || text.equalsIgnoreCase("Нет") || text.equalsIgnoreCase("No")
                    ? null : text;
            entity.setCompanyName(companyName);

            if (entity.getLanguage() == null) entity.setLanguage(lang);
            if (entity.getCreatedAt() == null) entity.setCreatedAt(LocalDateTime.now());

            employerRepository.save(entity);
            states.put(chatId, EmployerState.MAIN_MENU);
            draftData.get(chatId).clear();

            return createMessage(chatId,
                    getText(lang,
                            "✅ **Ro'yxatdan muvaffaqiyatli o'tdingiz!**" +
                                    (companyName != null ? "\n\n🏢 Kompaniya: **" + companyName + "**" : ""),
                            "✅ **Вы успешно зарегистрировались!**" +
                                    (companyName != null ? "\n\n🏢 Компания: **" + companyName + "**" : "")),
                    getMainMenuKeyboard(lang));
        }

        states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
        return createMessage(chatId,
                getText(lang, "👤 Ism va familiyangizni kiriting:", "👤 Введите ваше имя и фамилию:"));
    }

    // ============================================
    // VALIDATSIYA
    // ============================================
    private boolean isValidAge(String text) {
        if (text == null || !text.matches("^[1-9]\\d{1,2}$")) return false;
        int age = Integer.parseInt(text);
        return age >= 18 && age <= 99;
    }

    private boolean isValidPhone(String text) {
        if (text == null) return false;
        String clean = text.replaceAll("\\s", "");
        return PHONE_PATTERN.matcher(clean).matches();
    }

    private boolean isValidTitle(String text) {
        return text != null && text.length() >= 3 && text.length() <= 100;
    }

    private boolean isValidCategory(String text) {
        return text != null && !text.isBlank() && text.length() <= 50;
    }

    private boolean isValidJobType(String text) {
        return text != null && text.length() > 2;
    }

    private boolean isValidWorkHours(String text) {
        return text != null && !text.isBlank() && text.length() <= 50;
    }

    private boolean isValidWorkerCount(String text) {
        return text != null && text.trim().matches("^[1-9]\\d*$");
    }

    private boolean isValidSalary(String text) {
        return text != null && !text.isBlank() && text.length() <= 100;
    }

    // ============================================
    // YORDAMCHI METODLAR
    // ============================================
    private boolean isGlobalButton(String text) {
        return text != null && (
                text.equals("/start") ||
                        text.equals("👤 Profilim") || text.equals("👤 Профиль") ||
                        text.equals("✏️ Profilni tahrirlash") || text.equals("✏️ Редактировать профиль") ||
                        text.equals("⚙️ Sozlamalar") || text.equals("⚙️ Настройки") ||
                        text.equals("📢 Vakansiya yaratish") || text.equals("📢 Создать вакансию") ||
                        text.equals("📋 Mening vakansiyalarim") || text.equals("📋 Мои вакансии") ||
                        text.equals("📩 Arizalar") || text.equals("📩 Заявки") ||
                        text.equals("📊 Statistika") || text.equals("📊 Статистика") ||
                        text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") ||
                        text.equals("❌ Bekor qilish") || text.equals("❌ Отмена") ||
                        text.equals("🏠 Asosiy menyu") || text.equals("🏠 Главное меню") || text.equals("Asosiy menyu")
        );
    }

    private boolean isSkip(String text) {
        return text.equals("⏭ O'tkazib yuborish") || text.equals("⏭ Пропустить");
    }

    private void saveLanguage(Long chatId, Optional<EmployerEntity> profileOpt, String lang) {
        if (profileOpt.isPresent()) {
            EmployerEntity profile = profileOpt.get();
            profile.setLanguage(lang);
            employerRepository.save(profile);
        } else {
            EmployerEntity newProfile = EmployerEntity.builder()
                    .employerChatId(chatId)
                    .language(lang)
                    .createdAt(LocalDateTime.now())
                    .build();
            employerRepository.save(newProfile);
        }
    }

    private String getText(String lang, String uz, String ru) {
        return lang.equals("ru") ? ru : uz;
    }

    private SendMessage createMessage(Long chatId, String text) {
        return createMessage(chatId, text, null);
    }

    private SendMessage createMessage(Long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");
        if (keyboard != null) {
            message.setReplyMarkup(keyboard);
        }
        return message;
    }

    // ============================================
    // KLAVIATURA METODLARI
    // ============================================
    private ReplyKeyboardMarkup getMainMenuKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "📢 Vakansiya yaratish", "📢 Создать вакансию")),
                List.of(getText(lang, "📋 Mening vakansiyalarim", "📋 Мои вакансии")),
                List.of(getText(lang, "📩 Arizalar", "📩 Заявки")),
                List.of(getText(lang, "📊 Statistika", "📊 Статистика")),
                List.of(getText(lang, "👤 Profilim", "👤 Профиль"), getText(lang, "⚙️ Sozlamalar", "⚙️ Настройки")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    private ReplyKeyboardMarkup getProfileKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "✏️ Profilni tahrirlash", "✏️ Редактировать профиль")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    private ReplyKeyboardMarkup getSettingsKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "🌐 Til", "🌐 Язык")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    private ReplyKeyboardMarkup getLanguageKeyboard() {
        return createReplyKeyboard(List.of(
                List.of("🇺🇿 O'zbek tili", "🇷🇺 Русский язык"),
                List.of("🏠 Asosiy menyu")
        ));
    }

    private ReplyKeyboardMarkup getCancelKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "❌ Bekor qilish", "❌ Отмена")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    private ReplyKeyboardMarkup getContactKeyboard(String lang) {
        KeyboardButton btn = new KeyboardButton(getText(lang, "📞 Raqamni yuborish", "📞 Отправить номер"));
        btn.setRequestContact(true);
        KeyboardButton cancel = new KeyboardButton(getText(lang, "❌ Bekor qilish", "❌ Отмена"));
        KeyboardButton mainMenu = new KeyboardButton(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"));

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        KeyboardRow row1 = new KeyboardRow(); row1.add(btn);
        KeyboardRow row2 = new KeyboardRow(); row2.add(cancel);
        KeyboardRow row3 = new KeyboardRow(); row3.add(mainMenu);
        keyboardMarkup.setKeyboard(List.of(row1, row2, row3));
        keyboardMarkup.setResizeKeyboard(true);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getCategoryKeyboard(String lang) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<String> categories = List.of(
                "💻 IT & Dasturlash", "🎨 Dizayn",
                "🏗️ Qurilish", "🚗 Haydovchi / Kuryer",
                "📚 Ta'lim / Repetitor", "🛒 Savdo / Sotuvchi",
                "🧹 Farrosh / Tozalash", "👨‍🍳 Pazanda / Oshpaz",
                "🔒 Qorovul / Xavfsizlik", "📦 Kuryer / Yetkazib berish"
        );

        for (int i = 0; i < categories.size(); i += 2) {
            InlineKeyboardButton btn1 = new InlineKeyboardButton();
            btn1.setText(categories.get(i));
            btn1.setCallbackData("emp_cat_" + categories.get(i));

            InlineKeyboardButton btn2 = new InlineKeyboardButton();
            btn2.setText(categories.get(i + 1));
            btn2.setCallbackData("emp_cat_" + categories.get(i + 1));

            rows.add(List.of(btn1, btn2));
        }

        InlineKeyboardButton otherBtn = new InlineKeyboardButton();
        otherBtn.setText(lang.equals("ru") ? "➕ Другое (Ввести)" : "➕ Boshqa (Kiritish)");
        otherBtn.setCallbackData("emp_cat_OTHER");
        rows.add(List.of(otherBtn));

        InlineKeyboardButton menuBtn = new InlineKeyboardButton();
        menuBtn.setText(lang.equals("ru") ? "🏠 Главное меню" : "🏠 Asosiy menyu");
        menuBtn.setCallbackData("emp_cat_MENU");
        rows.add(List.of(menuBtn));

        markup.setKeyboard(rows);
        return markup;
    }

    private ReplyKeyboardMarkup getTypeKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "To'liq bandlik (Full-time)", "Полная занятость (Full-time)")),
                List.of(getText(lang, "Qisman bandlik (Part-time)", "Частичная занятость (Part-time)")),
                List.of(getText(lang, "Masofaviy (Remote)", "Удаленная работа (Remote)")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    private ReplyKeyboardMarkup getWorkHoursKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of("09:00 - 18:00", "08:00 - 17:00"),
                List.of(getText(lang, "Moslashuvchan", "Гибкий график")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    private ReplyKeyboardMarkup getSalaryQuickKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "Kelishiladi", "Договорная")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    private ReplyKeyboardMarkup getSkipKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "⏭ O'tkazib yuborish", "⏭ Пропустить")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    private ReplyKeyboardMarkup createReplyKeyboard(List<List<String>> buttons) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = buttons.stream().map(rowStr -> {
            KeyboardRow row = new KeyboardRow();
            rowStr.forEach(row::add);
            return row;
        }).toList();
        markup.setKeyboard(keyboard);
        return markup;
    }

    // ============================================
    // VAKANSIYA INLINE TUGMALARI (ARXIVLASH BILAN)
    // ============================================
    private SendMessage showMyVacanciesWithButtons(Long chatId, String lang) {
        List<JobVacancy> myJobs = jobVacancyRepository.findAllByEmployerChatId(chatId);
        if (myJobs.isEmpty()) {
            return createMessage(chatId,
                    lang.equals("ru") ? "📭 У вас пока нет вакансий." : "📭 Sizda hozircha vakansiyalar yo'q.",
                    getMainMenuKeyboard(lang));
        }

        StringBuilder sb = new StringBuilder(lang.equals("ru")
                ? "📋 **Ваши вакансии:**\n\nHar bir vakansiya uchun tugmalardan foydalaning:"
                : "📋 **Sizning vakansiyalaringiz:**\n\nHar bir vakansiya uchun tugmalardan foydalaning:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < myJobs.size(); i++) {
            JobVacancy job = myJobs.get(i);
            boolean isActive = job.getIsActive() == null || job.getIsActive();

            sb.append("\n\n").append(i + 1).append(". ")
                    .append(isActive ? "🟢" : "🔴")
                    .append(" **").append(job.getTitle() != null ? job.getTitle() : "Nomsiz")
                    .append("**\n   💰 ").append(job.getSalary() != null ? job.getSalary() : "Kelishiladi")
                    .append("\n   📁 ").append(job.getCategory() != null ? job.getCategory() : "Ko'rsatilmagan");

            List<InlineKeyboardButton> row1 = new ArrayList<>();

            InlineKeyboardButton editBtn = new InlineKeyboardButton();
            editBtn.setText(lang.equals("ru") ? "✏️ Изменить" : "✏️ Tahrirlash");
            editBtn.setCallbackData("vac_edit_" + job.getId());
            row1.add(editBtn);

            InlineKeyboardButton toggleBtn = new InlineKeyboardButton();
            toggleBtn.setText(isActive
                    ? (lang.equals("ru") ? "🔴 Архив" : "🔴 Arxivlash")
                    : (lang.equals("ru") ? "🟢 Активировать" : "🟢 Faollashtirish"));
            toggleBtn.setCallbackData("vac_toggle_" + job.getId());
            row1.add(toggleBtn);

            InlineKeyboardButton deleteBtn = new InlineKeyboardButton();
            deleteBtn.setText(lang.equals("ru") ? "🗑 Удалить" : "🗑 O'chirish");
            deleteBtn.setCallbackData("vac_delete_" + job.getId());
            row1.add(deleteBtn);

            rows.add(row1);
        }

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        return message;
    }

    private InlineKeyboardMarkup buildEditVacancyKeyboard(Long vacancyId, String lang) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton titleBtn = new InlineKeyboardButton();
        titleBtn.setText(lang.equals("ru") ? "📝 Название" : "📝 Sarlavha");
        titleBtn.setCallbackData("vac_edit_field_title_" + vacancyId);
        rows.add(List.of(titleBtn));

        InlineKeyboardButton salaryBtn = new InlineKeyboardButton();
        salaryBtn.setText(lang.equals("ru") ? "💰 Зарплата" : "💰 Maosh");
        salaryBtn.setCallbackData("vac_edit_field_salary_" + vacancyId);
        rows.add(List.of(salaryBtn));

        InlineKeyboardButton reqBtn = new InlineKeyboardButton();
        reqBtn.setText(lang.equals("ru") ? "📋 Требования" : "📋 Talablar");
        reqBtn.setCallbackData("vac_edit_field_requirements_" + vacancyId);
        rows.add(List.of(reqBtn));

        InlineKeyboardButton hoursBtn = new InlineKeyboardButton();
        hoursBtn.setText(lang.equals("ru") ? "⏰ Рабочие часы" : "⏰ Ish vaqti");
        hoursBtn.setCallbackData("vac_edit_field_workHours_" + vacancyId);
        rows.add(List.of(hoursBtn));

        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup buildDeleteConfirmKeyboard(Long vacancyId, String lang) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText(lang.equals("ru") ? "✅ Да, удалить" : "✅ Ha, o'chirish");
        confirmBtn.setCallbackData("vac_delete_confirm_" + vacancyId);

        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText(lang.equals("ru") ? "❌ Отмена" : "❌ Bekor qilish");
        cancelBtn.setCallbackData("vac_delete_cancel");

        rows.add(List.of(confirmBtn, cancelBtn));
        markup.setKeyboard(rows);
        return markup;
    }

    // ============================================
    // VAKANSIYA TAHRIRLASH HANDLER
    // ============================================
    private SendMessage handleEditVacancyField(Long chatId, String text, String field, String lang,
                                               String uzError, String ruError) {
        if (text == null || text.isBlank()) {
            return createMessage(chatId,
                    getText(lang, "⚠️ " + uzError, "⚠️ " + ruError),
                    getCancelKeyboard(lang));
        }

        String vacancyIdStr = draftData.get(chatId).get("edit_vacancy_id");
        if (vacancyIdStr == null) {
            states.put(chatId, EmployerState.MAIN_MENU);
            return createMessage(chatId,
                    getText(lang, "❌ Xatolik! Qaytadan urinib ko'ring.", "❌ Ошибка! Попробуйте снова."),
                    getMainMenuKeyboard(lang));
        }

        Long vacancyId = Long.parseLong(vacancyIdStr);
        Optional<JobVacancy> vacOpt = jobVacancyRepository.findByIdAndEmployerChatId(vacancyId, chatId);
        if (vacOpt.isEmpty()) {
            states.put(chatId, EmployerState.MAIN_MENU);
            return createMessage(chatId,
                    getText(lang, "❌ Vakansiya topilmadi!", "❌ Вакансия не найдена!"),
                    getMainMenuKeyboard(lang));
        }

        JobVacancy vacancy = vacOpt.get();
        switch (field) {
            case "title" -> vacancy.setTitle(text);
            case "salary" -> vacancy.setSalary(text);
            case "requirements" -> vacancy.setRequirements(text);
            case "workHours" -> vacancy.setWorkHours(text);
        }
        jobVacancyRepository.save(vacancy);

        states.put(chatId, EmployerState.MAIN_MENU);
        draftData.get(chatId).clear();

        return createMessage(chatId,
                getText(lang, "✅ Vakansiya yangilandi!", "✅ Вакансия обновлена!"),
                getMainMenuKeyboard(lang));
    }

    // ============================================
    // PROFIL
    // ============================================
    private SendMessage showProfile(Long chatId, Optional<EmployerEntity> profileOpt, String lang) {
        if (profileOpt.isEmpty() || profileOpt.get().getFullName() == null || profileOpt.get().getFullName().toLowerCase().contains("employer")) {
            states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
            return createMessage(chatId,
                    getText(lang, "Iltimos, ism va familiyangizni kiriting:", "Пожалуйста, введите ваше имя и фамилию:"),
                    getCancelKeyboard(lang));
        }

        EmployerEntity employer = profileOpt.get();
        String rating = "5.0 ⭐";
        String company = employer.getCompanyName() != null && !employer.getCompanyName().isBlank()
                ? employer.getCompanyName()
                : (lang.equals("ru") ? "Не указано" : "Ko'rsatilmagan");
        String ageStr = employer.getAge() != null ? String.valueOf(employer.getAge()) : "-";
        String passport = employer.getPassportSeriesNumber() != null ? employer.getPassportSeriesNumber() : "-";

        String msg;
        if (lang.equals("ru")) {
            msg = String.format("""
                    👤 **Ваш профиль:**
                    
                    🆔 **Telegram ID:** %d
                    👤 **ФИО:** %s
                    🎂 **Возраст:** %s
                    🪪 **Паспорт:** %s
                    🏢 **Компания:** %s
                    📞 **Телефон:** %s
                    🌐 **Язык:** %s
                    ⭐ **Рейтинг:** %s
                    📝 **Статус:** Активный работодатель
                    """,
                    chatId, employer.getFullName(), ageStr, passport, company,
                    employer.getPhoneNumber() != null ? employer.getPhoneNumber() : "Не указано",
                    employer.getLanguage() != null ? employer.getLanguage() : "uz", rating);
        } else {
            msg = String.format("""
                    👤 **Sizning profilingiz:**
                    
                    🆔 **Telegram ID:** %d
                    👤 **F.I.O:** %s
                    🎂 **Yosh:** %s
                    🪪 **Pasport:** %s
                    🏢 **Kompaniya:** %s
                    📞 **Telefon:** %s
                    🌐 **Til:** %s
                    ⭐ **Reyting:** %s
                    📝 **Holat:** Faol ish beruvchi
                    """,
                    chatId, employer.getFullName(), ageStr, passport, company,
                    employer.getPhoneNumber() != null ? employer.getPhoneNumber() : "Ko'rsatilmagan",
                    employer.getLanguage() != null ? employer.getLanguage() : "uz", rating);
        }

        return createMessage(chatId, msg, getProfileKeyboard(lang));
    }

    // ============================================
    // ARIZA BILDIRISHNOMASI
    // ============================================
    public SendMessage buildApplicationNotification(
            Long employerChatId,
            Long applicationId,
            Long employeeChatId,
            String vacancyTitle,
            String candidateName,
            String candidatePhone,
            String lang) {

        String text = lang.equals("ru") ?
                String.format("🔔 **Новый отклик на вакансию!**\n\n📌 **Вакансия:** %s\n👤 **Кандидат:** %s\n📞 **Контакты:** %s\n\nВыберите действие ниже:",
                        vacancyTitle, candidateName, candidatePhone) :
                String.format("🔔 **Vakansiyaga yangi ariza tushdi!**\n\n📌 **Vakansiya:** %s\n👤 **Nomzod:** %s\n📞 **Aloqa:** %s\n\nQuyidagilardan birini tanlang:",
                        vacancyTitle, candidateName, candidatePhone);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton acceptBtn = new InlineKeyboardButton();
        acceptBtn.setText(lang.equals("ru") ? "✅ Принять" : "✅ Qabul qilish");
        acceptBtn.setCallbackData("accept_app_" + applicationId + "_" + employeeChatId);

        InlineKeyboardButton rejectBtn = new InlineKeyboardButton();
        rejectBtn.setText(lang.equals("ru") ? "❌ Отклонить" : "❌ Rad etish");
        rejectBtn.setCallbackData("reject_app_" + applicationId + "_" + employeeChatId);

        rows.add(List.of(acceptBtn, rejectBtn));
        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(employerChatId.toString(), text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);

        return message;
    }

    // ============================================
    // YORDAMCHI: ANSWER CALLBACK
    // ============================================
    private void sendAnswerCallback(String callbackQueryId, String text, boolean showAlert) {
        try {
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer =
                    new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQueryId);
            if (text != null) {
                answer.setText(text);
                answer.setShowAlert(showAlert);
            }
            telegram.execute(answer);
        } catch (Exception e) {
            log.error("❌ AnswerCallback error", e);
        }
    }
}