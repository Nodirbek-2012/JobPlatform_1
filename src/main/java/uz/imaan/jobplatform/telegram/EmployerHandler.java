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
import uz.imaan.jobplatform.employer.chat.entity.ChatMessage;
import uz.imaan.jobplatform.employer.chat.repository.ChatMessageRepository;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;
import uz.imaan.jobplatform.employer.entity.EmployerReview;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;
import uz.imaan.jobplatform.employer.repository.EmployerRepository;
import uz.imaan.jobplatform.employer.repository.EmployerReviewRepository;
import uz.imaan.jobplatform.employer.state.EmployerState;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.entity.Subscription;
import uz.imaan.jobplatform.jobseeker.repository.JobApplicationRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.jobseeker.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final ChatMessageRepository chatMessageRepository;
    private final EmployerReviewRepository employerReviewRepository;
    private final Telegram telegram;

    private final Map<Long, EmployerState> states = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> draftData = new ConcurrentHashMap<>();

    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{7}$");

    private static final List<String> CANCEL_WORDS = List.of(
            "❌ Bekor qilish", "❌ Отмена", "❌ Cancel",
            "Bekor qilish", "Отмена", "Cancel"
    );

    public EmployerHandler(
            EmployerRepository employerRepository,
            JobVacancyRepository jobVacancyRepository,
            SubscriptionRepository subscriptionRepository,
            JobSeekerProfileRepository jobSeekerProfileRepository,
            JobApplicationRepository jobApplicationRepository,
            ChatMessageRepository chatMessageRepository,
            EmployerReviewRepository employerReviewRepository,
            @Lazy Telegram telegram) {
        this.employerRepository = employerRepository;
        this.jobVacancyRepository = jobVacancyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.employerReviewRepository = employerReviewRepository;
        this.telegram = telegram;
    }

    // ============================================
    // ГЛАВНЫЙ ОБРАБОТЧИК ТЕКСТА
    // ============================================
    public SendMessage handleEmployer(Message message) {
        if (message == null) return null;

        Long chatId = message.getChatId();
        String text = message.hasText() ? message.getText().trim() : "";
        draftData.putIfAbsent(chatId, new ConcurrentHashMap<>());

        Optional<EmployerEntity> profileOpt = employerRepository.findByEmployerChatId(chatId);
        String lang = profileOpt.map(EmployerEntity::getLanguage).orElse("uz");
        EmployerState currentState = states.getOrDefault(chatId, EmployerState.MAIN_MENU);

        // ===== РЕГИСТРАЦИЯ: ВЫБОР ЯЗЫКА СНАЧАЛА (через INLINE) =====
        boolean notRegistered = profileOpt.isEmpty()
                || profileOpt.get().getFullName() == null
                || profileOpt.get().getFullName().isBlank();

        if (notRegistered) {
            // /start или пустое — показать выбор языка через INLINE
            if (text.equals("/start") || text.equals("/menu")
                    || text.equals("🏠 Asosiy menyu") || text.equals("🏠 Главное меню")
                    || text.equals("🏠 Main menu") || text.isBlank()) {
                states.put(chatId, EmployerState.WAITING_FOR_LANGUAGE);
                return createInlineLanguageMessage(chatId,
                        "🌐 **Tilni tanlang / Выберите язык / Choose language:**");
            }

            // Если ждём язык и пришёл текст (не callback)
            if (currentState == EmployerState.WAITING_FOR_LANGUAGE) {
                return createInlineLanguageMessage(chatId,
                        "⚠️ Iltimos, tugmalardan birini tanlang / Выберите кнопку / Please choose a button:");
            }

            // Идём по шагам регистрации
            return handleRegistration(chatId, text, message, lang, profileOpt);
        }

        // ===== ЛОГОТИП =====
        if (currentState == EmployerState.WAITING_FOR_COMPANY_LOGO) {
            if (message.hasPhoto() || (message.hasDocument()
                    && message.getDocument().getMimeType() != null
                    && message.getDocument().getMimeType().startsWith("image/"))) {
                String fileId = message.hasPhoto()
                        ? message.getPhoto().get(message.getPhoto().size() - 1).getFileId()
                        : message.getDocument().getFileId();
                EmployerEntity entity = profileOpt.get();
                entity.setCompanyLogoFileId(fileId);
                employerRepository.save(entity);
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return createMessage(chatId,
                        getText(lang, "✅ Logotip saqlandi!", "✅ Логотип сохранён!", "✅ Logo saved!"),
                        getCompanyInfoKeyboard(lang));
            }
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            return createMessage(chatId,
                    getText(lang, "⚠️ Rasm yuboring yoki bekor qiling:",
                            "⚠️ Отправьте изображение или отмените:",
                            "⚠️ Send image or cancel:"),
                    getCancelKeyboard(lang));
        }

        // ===== ЧАТ =====
        if (currentState == EmployerState.WAITING_FOR_CHAT_MESSAGE) {
            String partnerIdStr = draftData.get(chatId).get("chat_partner_id");
            if (partnerIdStr == null) {
                states.put(chatId, EmployerState.CHAT_MENU);
                return showChatMenu(chatId, lang);
            }
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.CHAT_MENU);
                return showChatMenu(chatId, lang);
            }
            Long partnerId = Long.parseLong(partnerIdStr);
            if (text == null || text.isBlank() || text.length() > 4000) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Xabar 1-4000 belgi:",
                                "⚠️ Сообщение 1-4000:",
                                "⚠️ Message 1-4000:"),
                        getChatKeyboard(lang));
            }
            ChatMessage msg = ChatMessage.builder()
                    .senderChatId(chatId)
                    .receiverChatId(partnerId)
                    .messageText(text)
                    .isRead(false)
                    .build();
            chatMessageRepository.save(msg);
            try {
                SendMessage notifyMsg = new SendMessage(partnerId.toString(),
                        getText(lang, "💬 **Yangi xabar:**\n\n" + text,
                                "💬 **Новое сообщение:**\n\n" + text,
                                "💬 **New message:**\n\n" + text));
                notifyMsg.setParseMode("Markdown");
                telegram.execute(notifyMsg);
            } catch (Exception e) {
                log.error("❌ Chat error", e);
            }
            return showChatMessages(chatId, partnerId, lang);
        }

        // ===== ГЛОБАЛЬНЫЕ КНОПКИ =====
        if (isGlobalButton(text)) {
            draftData.get(chatId).clear();
            states.put(chatId, EmployerState.MAIN_MENU);

            if (text.equals("/start") || text.equals("🏠 Asosiy menyu")
                    || text.equals("🏠 Главное меню") || text.equals("🏠 Main menu")) {
                return createMessage(chatId,
                        getText(lang, "🏠 **Xush kelibsiz!**\n\nKerakli bo'limni tanlang:",
                                "🏠 **Добро пожаловать!**\n\nВыберите раздел:",
                                "🏠 **Welcome!**\n\nChoose section:"),
                        getMainMenuKeyboard(lang));
            }
            if (text.equals("📊 Dashboard") || text.equals("📊 Дашборд")) {
                states.put(chatId, EmployerState.DASHBOARD);
                return showDashboard(chatId, lang);
            }
            if (text.equals("👤 Profilim") || text.equals("👤 Профиль") || text.equals("👤 Profile")) {
                return showProfile(chatId, profileOpt, lang);
            }
            if (text.equals("🏢 Kompaniya") || text.equals("🏢 Компания") || text.equals("🏢 Company")) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            if (text.equals("✏️ Profilni tahrirlash") || text.equals("✏️ Редактировать профиль") || text.equals("✏️ Edit profile")) {
                states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
                return createMessage(chatId,
                        getText(lang, "✏️ **Tahrirlash:**\n\nNimani?",
                                "✏️ **Редактировать:**\n\nЧто?",
                                "✏️ **Edit:**\n\nWhat?"),
                        getEditProfileKeyboard(lang));
            }
            if (text.equals("⚙️ Sozlamalar") || text.equals("⚙️ Настройки") || text.equals("⚙️ Settings")) {
                states.put(chatId, EmployerState.SETTINGS_MENU);
                return createMessage(chatId,
                        getText(lang, "⚙️ **Sozlamalar:**", "⚙️ **Настройки:**", "⚙️ **Settings:**"),
                        getSettingsKeyboard(lang));
            }
            if (text.equals("📢 Vakansiya yaratish") || text.equals("📢 Создать вакансию") || text.equals("📢 Create vacancy")) {
                states.put(chatId, EmployerState.WAITING_FOR_TITLE);
                return createMessage(chatId,
                        getText(lang, "📝 **Ish sarlavhasini kiriting:**",
                                "📝 **Введите название вакансии:**",
                                "📝 **Enter vacancy title:**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("📋 Mening vakansiyalarim") || text.equals("📋 Мои вакансии") || text.equals("📋 My vacancies")) {
                return showMyVacanciesWithButtons(chatId, lang);
            }
            if (text.equals("📩 Arizalar") || text.equals("📩 Заявки") || text.equals("📩 Applications")) {
                states.put(chatId, EmployerState.VIEWING_APPLICATIONS);
                return showApplications(chatId, lang);
            }
            if (text.equals("📊 Statistika") || text.equals("📊 Статистика") || text.equals("📊 Statistics")) {
                states.put(chatId, EmployerState.DASHBOARD);
                return showDashboard(chatId, lang);
            }
            if (text.equals("🔍 Nomzod izlash") || text.equals("🔍 Поиск кандидатов") || text.equals("🔍 Search candidates")) {
                states.put(chatId, EmployerState.SEARCH_CANDIDATES);
                return showCandidateSearch(chatId, lang);
            }
            if (text.equals("💬 Chatlar") || text.equals("💬 Чаты") || text.equals("💬 Chats")) {
                states.put(chatId, EmployerState.CHAT_MENU);
                return showChatMenu(chatId, lang);
            }
            if (text.equals("⭐ Reytingim") || text.equals("⭐ Мой рейтинг") || text.equals("⭐ My rating")) {
                states.put(chatId, EmployerState.RATING_MENU);
                return showEmployerRating(chatId, lang);
            }
            if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("⬅️ Back")
                    || text.equals("❌ Bekor qilish") || text.equals("❌ Отмена") || text.equals("❌ Cancel")) {
                return createMessage(chatId,
                        getText(lang, "📢 **Ish beruvchi menyusi:**",
                                "📢 **Меню работодателя:**",
                                "📢 **Employer menu:**"),
                        getMainMenuKeyboard(lang));
            }
        }

        EmployerState state = states.getOrDefault(chatId, EmployerState.MAIN_MENU);

        // ===== DASHBOARD =====
        if (state == EmployerState.DASHBOARD) return showDashboard(chatId, lang);

        // ===== НОМЗОДЫ =====
        if (state == EmployerState.SEARCH_CANDIDATES) {
            if (isCancelText(text) || text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("⬅️ Back")) {
                states.put(chatId, EmployerState.MAIN_MENU);
                return createMessage(chatId,
                        getText(lang, "📢 **Ish beruvchi menyusi:**",
                                "📢 **Меню работодателя:**",
                                "📢 **Employer menu:**"),
                        getMainMenuKeyboard(lang));
            }
            return showCandidateSearch(chatId, lang);
        }

        // ===== CHAT MENU =====
        if (state == EmployerState.CHAT_MENU) {
            if (isCancelText(text) || text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("⬅️ Back")) {
                states.put(chatId, EmployerState.MAIN_MENU);
                return createMessage(chatId,
                        getText(lang, "📢 **Ish beruvchi menyusi:**",
                                "📢 **Меню работодателя:**",
                                "📢 **Employer menu:**"),
                        getMainMenuKeyboard(lang));
            }
            return showChatMenu(chatId, lang);
        }

        // ===== РЕЙТИНГ =====
        if (state == EmployerState.RATING_MENU) return showEmployerRating(chatId, lang);

        // ===== КОМПАНИЯ =====
        if (state == EmployerState.COMPANY_INFO_MENU) {
            if (isCancelText(text) || text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("⬅️ Back")) {
                states.put(chatId, EmployerState.MAIN_MENU);
                return showProfile(chatId, profileOpt, lang);
            }
            if (text.equals("📝 Kompaniya tavsifi") || text.equals("📝 Описание компании") || text.equals("📝 Description")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY_DESCRIPTION);
                return createMessage(chatId,
                        getText(lang, "📝 **Tavsif kiriting (max 500):**",
                                "📝 **Описание (макс 500):**",
                                "📝 **Description (max 500):**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("📍 Manzil") || text.equals("📍 Адрес") || text.equals("📍 Address")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY_ADDRESS);
                return createMessage(chatId,
                        getText(lang, "📍 **Manzilni kiriting:**",
                                "📍 **Введите адрес:**",
                                "📍 **Enter address:**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("🌐 Veb-sayt") || text.equals("🌐 Веб-сайт") || text.equals("🌐 Website")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY_WEBSITE);
                return createMessage(chatId,
                        getText(lang, "🌐 **Veb-sayt yoki Telegram kanali:**",
                                "🌐 **Веб-сайт или Telegram-канал:**",
                                "🌐 **Website or Telegram channel:**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("👥 Xodimlar soni") || text.equals("👥 Количество сотрудников") || text.equals("👥 Employees")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY_SIZE);
                return createMessage(chatId,
                        getText(lang, "👥 **Xodimlar soni:**",
                                "👥 **Количество сотрудников:**",
                                "👥 **Employees count:**"),
                        getCompanySizeKeyboard(lang));
            }
            if (text.equals("🖼 Logotip") || text.equals("🖼 Логотип") || text.equals("🖼 Logo")) {
                states.put(chatId, EmployerState.WAITING_FOR_COMPANY_LOGO);
                return createMessage(chatId,
                        getText(lang, "🖼 **Logotip yuboring:**",
                                "🖼 **Отправьте логотип:**",
                                "🖼 **Send logo:**"),
                        getCancelKeyboard(lang));
            }
            return showCompanyInfo(chatId, profileOpt, lang);
        }

        // ===== КОМПАНИЯ ТАВСИФ =====
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY_DESCRIPTION) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            if (text.isBlank() || text.length() > 500) {
                return createMessage(chatId,
                        getText(lang, "⚠️ 1-500 belgi:", "⚠️ 1-500:", "⚠️ 1-500:"),
                        getCancelKeyboard(lang));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setCompanyDescription(text);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.COMPANY_INFO_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Saqlandi!", "✅ Сохранено!", "✅ Saved!"),
                    getCompanyInfoKeyboard(lang));
        }

        // ===== КОМПАНИЯ АДРЕС =====
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY_ADDRESS) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            if (text.isBlank() || text.length() > 200) {
                return createMessage(chatId,
                        getText(lang, "⚠️ 1-200:", "⚠️ 1-200:", "⚠️ 1-200:"),
                        getCancelKeyboard(lang));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setCompanyAddress(text);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.COMPANY_INFO_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Saqlandi!", "✅ Сохранено!", "✅ Saved!"),
                    getCompanyInfoKeyboard(lang));
        }

        // ===== КОМПАНИЯ ВЕБ =====
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY_WEBSITE) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            if (text.isBlank() || text.length() > 200) {
                return createMessage(chatId,
                        getText(lang, "⚠️ 1-200:", "⚠️ 1-200:", "⚠️ 1-200:"),
                        getCancelKeyboard(lang));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setCompanyWebsite(text);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.COMPANY_INFO_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Saqlandi!", "✅ Сохранено!", "✅ Saved!"),
                    getCompanyInfoKeyboard(lang));
        }

        // ===== КОМПАНИЯ РАЗМЕР =====
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY_SIZE) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            if (text.isBlank() || text.length() > 50) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Tugma tanlang:", "⚠️ Кнопку:", "⚠️ Button:"),
                        getCompanySizeKeyboard(lang));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setCompanySize(text);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.COMPANY_INFO_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Saqlandi!", "✅ Сохранено!", "✅ Saved!"),
                    getCompanyInfoKeyboard(lang));
        }

        // ===== БЛОКИРОВКА МЕНЮ =====
        if (state == EmployerState.BLOCKED_CANDIDATES_MENU) {
            if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("⬅️ Back") || isCancelText(text)) {
                states.put(chatId, EmployerState.SETTINGS_MENU);
                return createMessage(chatId,
                        getText(lang, "⚙️ **Sozlamalar:**", "⚙️ **Настройки:**", "⚙️ **Settings:**"),
                        getSettingsKeyboard(lang));
            }
            if (text.equals("➕ Nomzodni bloklash") || text.equals("➕ Заблокировать") || text.equals("➕ Block")) {
                states.put(chatId, EmployerState.WAITING_FOR_BLOCK_CANDIDATE_ID);
                return createMessage(chatId,
                        getText(lang, "🚫 **Telegram ID kiriting:**",
                                "🚫 **Введите Telegram ID:**",
                                "🚫 **Enter Telegram ID:**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("✅ Blokdan chiqarish") || text.equals("✅ Разблокировать") || text.equals("✅ Unblock")) {
                states.put(chatId, EmployerState.WAITING_FOR_BLOCK_CANDIDATE_ID);
                return createMessage(chatId,
                        getText(lang, "✅ **ID kiriting:**", "✅ **Введите ID:**", "✅ **Enter ID:**"),
                        getCancelKeyboard(lang));
            }
            return showBlockedCandidates(chatId, profileOpt, lang);
        }

        // ===== БЛОК ID =====
        if (state == EmployerState.WAITING_FOR_BLOCK_CANDIDATE_ID) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.BLOCKED_CANDIDATES_MENU);
                return showBlockedCandidates(chatId, profileOpt, lang);
            }
            if (text == null || !text.matches("^\\d+$")) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Faqat raqam:", "⚠️ Только цифры:", "⚠️ Digits:"),
                        getCancelKeyboard(lang));
            }
            Long candidateId = Long.parseLong(text);
            if (candidateId.equals(chatId)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ O'zingizni bloklay olmaysiz!",
                                "⚠️ Себя нельзя!",
                                "⚠️ Cannot block yourself!"),
                        getCancelKeyboard(lang));
            }
            EmployerEntity entity = profileOpt.get();
            String blocked = entity.getBlockedCandidateIds();
            List<String> list = (blocked == null || blocked.isBlank())
                    ? new ArrayList<>()
                    : new ArrayList<>(List.of(blocked.split(",")));

            if (list.contains(String.valueOf(candidateId))) {
                list.remove(String.valueOf(candidateId));
                entity.setBlockedCandidateIds(list.isEmpty() ? null : String.join(",", list));
                employerRepository.save(entity);
                states.put(chatId, EmployerState.BLOCKED_CANDIDATES_MENU);
                return createMessage(chatId,
                        getText(lang, "✅ Chiqarildi! ID: " + candidateId,
                                "✅ Разблокирован! ID: " + candidateId,
                                "✅ Unblocked! ID: " + candidateId),
                        getBlockedCandidatesKeyboard(lang));
            } else {
                list.add(String.valueOf(candidateId));
                entity.setBlockedCandidateIds(String.join(",", list));
                employerRepository.save(entity);
                states.put(chatId, EmployerState.BLOCKED_CANDIDATES_MENU);
                return createMessage(chatId,
                        getText(lang, "✅ Bloklandi! ID: " + candidateId,
                                "✅ Заблокирован! ID: " + candidateId,
                                "✅ Blocked! ID: " + candidateId),
                        getBlockedCandidatesKeyboard(lang));
            }
        }

        // ===== РЕДАКТ ПРОФИЛЯ =====
        if (state == EmployerState.EDIT_PROFILE_MENU) {
            if (text.equals("👤 Ismni tahrirlash") || text.equals("👤 Изменить имя") || text.equals("👤 Change name")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_NAME);
                return createMessage(chatId,
                        getText(lang, "👤 **Yangi ism-familiya:**",
                                "👤 **Новое имя:**", "👤 **New name:**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("🎂 Yoshni tahrirlash") || text.equals("🎂 Изменить возраст") || text.equals("🎂 Change age")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_AGE);
                return createMessage(chatId,
                        getText(lang, "🎂 **Yosh (18+):**",
                                "🎂 **Возраст (18+):**", "🎂 **Age (18+):**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("🏢 Kompaniya nomini tahrirlash") || text.equals("🏢 Изменить название компании") || text.equals("🏢 Change company")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY);
                return createMessage(chatId,
                        getText(lang, "🏢 **Kompaniya nomi:**",
                                "🏢 **Название компании:**", "🏢 **Company name:**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("⬅️ Back")
                    || text.equals("❌ Bekor qilish") || text.equals("❌ Отмена") || text.equals("❌ Cancel")) {
                states.put(chatId, EmployerState.MAIN_MENU);
                return showProfile(chatId, profileOpt, lang);
            }
            return createMessage(chatId,
                    getText(lang, "✏️ **Tahrirlash:**", "✏️ **Редактировать:**", "✏️ **Edit:**"),
                    getEditProfileKeyboard(lang));
        }

        // ===== ИМЯ =====
        if (state == EmployerState.WAITING_FOR_EDIT_NAME) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
                return createMessage(chatId,
                        getText(lang, "❌ Bekor.", "❌ Отменено.", "❌ Cancelled."),
                        getEditProfileKeyboard(lang));
            }
            if (text.isBlank() || text.length() < 3) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Min 3 belgi:", "⚠️ Мин 3:", "⚠️ Min 3:"),
                        getCancelKeyboard(lang));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setFullName(text);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Yangilandi!", "✅ Обновлено!", "✅ Updated!"),
                    getEditProfileKeyboard(lang));
        }

        // ===== ВОЗРАСТ =====
        if (state == EmployerState.WAITING_FOR_EDIT_AGE) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
                return createMessage(chatId,
                        getText(lang, "❌ Bekor.", "❌ Отменено.", "❌ Cancelled."),
                        getEditProfileKeyboard(lang));
            }
            if (!isValidAge(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ 18-99:", "⚠️ 18-99:", "⚠️ 18-99:"),
                        getCancelKeyboard(lang));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setAge(Integer.parseInt(text));
            employerRepository.save(entity);
            states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Yangilandi!", "✅ Обновлено!", "✅ Updated!"),
                    getEditProfileKeyboard(lang));
        }

        // ===== КОМПАНИЯ ИМЯ =====
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
                return createMessage(chatId,
                        getText(lang, "❌ Bekor.", "❌ Отменено.", "❌ Cancelled."),
                        getEditProfileKeyboard(lang));
            }
            if (text == null || text.isBlank()) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Kiriting yoki \"Yo'q\":",
                                "⚠️ Введите или \"Нет\":", "⚠️ Enter or \"No\":"),
                        getCancelKeyboard(lang));
            }
            String companyName = isNoCompanyText(text) ? null : text;
            EmployerEntity entity = profileOpt.get();
            entity.setCompanyName(companyName);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Yangilandi!", "✅ Обновлено!", "✅ Updated!"),
                    getEditProfileKeyboard(lang));
        }

        // ===== НАСТРОЙКИ =====
        if (state == EmployerState.SETTINGS_MENU) {
            if (text.equals("🌐 Til") || text.equals("🌐 Язык") || text.equals("🌐 Language")) {
                states.put(chatId, EmployerState.WAITING_FOR_LANGUAGE);
                return createInlineLanguageMessage(chatId,
                        "🌐 **Tilni tanlang / Выберите язык / Choose language:**");
            }
            if (text.equals("🚫 Bloklangan nomzodlar") || text.equals("🚫 Заблокированные") || text.equals("🚫 Blocked")) {
                states.put(chatId, EmployerState.BLOCKED_CANDIDATES_MENU);
                return showBlockedCandidates(chatId, profileOpt, lang);
            }
            return createMessage(chatId,
                    getText(lang, "⚙️ **Sozlamalar:**", "⚙️ **Настройки:**", "⚙️ **Settings:**"),
                    getSettingsKeyboard(lang));
        }

        // ===== ЖДЁМ ВЫБОР ЯЗЫКА (через inline callback) =====
        if (state == EmployerState.WAITING_FOR_LANGUAGE) {
            return createInlineLanguageMessage(chatId,
                    "🌐 **Tilni tanlang / Выберите язык / Choose language:**");
        }

        // ===== ЗАЯВКИ =====
        if (state == EmployerState.VIEWING_APPLICATIONS) return showApplications(chatId, lang);
        if (state == EmployerState.VIEWING_STATISTICS) return showDashboard(chatId, lang);

        // ===== РЕДАКТ ВАКАНСИИ =====
        if (state == EmployerState.WAITING_FOR_EDIT_VACANCY_TITLE) {
            return handleEditVacancyField(chatId, text, "title", lang,
                    "Sarlavha 3-100!", "Заголовок 3-100!", "Title 3-100!");
        }
        if (state == EmployerState.WAITING_FOR_EDIT_VACANCY_SALARY) {
            return handleEditVacancyField(chatId, text, "salary", lang,
                    "Maosh bo'sh!", "Зарплата пустая!", "Salary empty!");
        }
        if (state == EmployerState.WAITING_FOR_EDIT_VACANCY_REQUIREMENTS) {
            return handleEditVacancyField(chatId, text, "requirements", lang,
                    "Talablar bo'sh!", "Требования пустые!", "Requirements empty!");
        }
        if (state == EmployerState.WAITING_FOR_EDIT_VACANCY_WORK_HOURS) {
            return handleEditVacancyField(chatId, text, "workHours", lang,
                    "Ish vaqti bo'sh!", "Часы пустые!", "Hours empty!");
        }

        // ===== СОЗДАНИЕ ВАКАНСИИ =====
        if (state == EmployerState.WAITING_FOR_TITLE) {
            if (!isValidTitle(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Sarlavha 3-100:", "⚠️ Заголовок 3-100:", "⚠️ Title 3-100:"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("title", text);
            states.put(chatId, EmployerState.WAITING_FOR_CATEGORY);
            return createMessage(chatId,
                    getText(lang, "📁 **Kategoriya:**", "📁 **Категория:**", "📁 **Category:**"),
                    getCategoryKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_CATEGORY) {
            if (!isValidCategory(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Tugma:", "⚠️ Кнопка:", "⚠️ Button:"),
                        getCategoryKeyboard(lang));
            }
            draftData.get(chatId).put("category", text);
            states.put(chatId, EmployerState.WAITING_FOR_JOB_TYPE);
            return createMessage(chatId,
                    getText(lang, "⏰ **Ish turi:**", "⏰ **Тип работы:**", "⏰ **Job type:**"),
                    getTypeKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_CUSTOM_CATEGORY) {
            if (!isValidTitle(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Kategoriya qisqa:", "⚠️ Категория коротка:", "⚠️ Category short:"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("category", text);
            states.put(chatId, EmployerState.WAITING_FOR_JOB_TYPE);
            return createMessage(chatId,
                    getText(lang, "⏰ **Ish turi:**", "⏰ **Тип работы:**", "⏰ **Job type:**"),
                    getTypeKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_JOB_TYPE) {
            if (!isValidJobType(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Tugma:", "⚠️ Кнопка:", "⚠️ Button:"),
                        getTypeKeyboard(lang));
            }
            draftData.get(chatId).put("type", text);
            states.put(chatId, EmployerState.WAITING_FOR_WORK_HOURS);
            return createMessage(chatId,
                    getText(lang, "⌛️ **Ish soatlari:**", "⌛️ **Часы:**", "⌛️ **Hours:**"),
                    getWorkHoursKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_WORK_HOURS) {
            if (!isValidWorkHours(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Noto'g'ri!", "⚠️ Неверно!", "⚠️ Invalid!"),
                        getWorkHoursKeyboard(lang));
            }
            draftData.get(chatId).put("workHours", text);
            states.put(chatId, EmployerState.WAITING_FOR_WORKER_COUNT);
            return createMessage(chatId,
                    getText(lang, "👥 **Nechta xodim?**", "👥 **Сколько сотрудников?**", "👥 **How many workers?**"),
                    getCancelKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_WORKER_COUNT) {
            if (!isValidWorkerCount(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Musbat raqam:", "⚠️ Положительное число:", "⚠️ Positive number:"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("workerCount", text);
            states.put(chatId, EmployerState.WAITING_FOR_SALARY);
            return createMessage(chatId,
                    getText(lang, "💰 **Maosh:**", "💰 **Зарплата:**", "💰 **Salary:**"),
                    getSalaryQuickKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_SALARY) {
            if (!isValidSalary(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Noto'g'ri:", "⚠️ Неверно:", "⚠️ Invalid:"),
                        getSalaryQuickKeyboard(lang));
            }
            draftData.get(chatId).put("salary", text);
            states.put(chatId, EmployerState.WAITING_FOR_REQUIREMENTS);
            return createMessage(chatId,
                    getText(lang, "📌 **Talablar:**", "📌 **Требования:**", "📌 **Requirements:**"),
                    getSkipKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_REQUIREMENTS) {
            String reqText = isSkip(text)
                    ? getText(lang, "Ko'rsatilmadi", "Не указано", "Not specified")
                    : text;
            draftData.get(chatId).put("requirements", reqText);

            String phoneFromProfile = profileOpt.map(EmployerEntity::getPhoneNumber).orElse(null);
            if (phoneFromProfile == null || phoneFromProfile.isBlank()) {
                states.put(chatId, EmployerState.WAITING_FOR_PHONE);
                return createMessage(chatId,
                        getText(lang, "📞 **Telefon:**", "📞 **Телефон:**", "📞 **Phone:**"),
                        getContactKeyboard(lang));
            }
            return createVacancyAndNotify(chatId, phoneFromProfile, profileOpt, lang);
        }

        if (state == EmployerState.WAITING_FOR_PHONE) {
            String phoneText = message.hasContact() ? message.getContact().getPhoneNumber() : text;
            if (!isValidPhone(phoneText)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Format: +998901234567",
                                "⚠️ Формат: +998901234567", "⚠️ Format: +998901234567"),
                        getContactKeyboard(lang));
            }
            return createVacancyAndNotify(chatId, normalizePhone(phoneText), profileOpt, lang);
        }

        return createMessage(chatId,
                getText(lang, "📢 **Ish beruvchi menyusi:**",
                        "📢 **Меню работодателя:**",
                        "📢 **Employer menu:**"),
                getMainMenuKeyboard(lang));
    }

    // ============================================
    // CALLBACK HANDLER
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

        if (data == null) return null;

        // ===== ВЫБОР ЯЗЫКА (INLINE callback) =====
        if (data.startsWith("lang_")) {
            String newLang = data.replace("lang_", "");

            boolean isRegistration = profileOpt.isEmpty()
                    || profileOpt.get().getFullName() == null
                    || profileOpt.get().getFullName().isBlank();

            EmployerEntity profile = profileOpt.orElseGet(() -> EmployerEntity.builder()
                    .employerChatId(chatId)
                    .createdAt(LocalDateTime.now())
                    .build());
            profile.setLanguage(newLang);
            employerRepository.save(profile);

            sendAnswerCallback(callbackQuery.getId(), null, false);

            if (isRegistration) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId,
                        getText(newLang,
                                "👤 **Ish beruvchi sifatida ro'yxatdan o'tish**\n\nIsm va familiyangizni kiriting:",
                                "👤 **Регистрация работодателя**\n\nВведите имя и фамилию:",
                                "👤 **Employer registration**\n\nEnter your first and last name:"),
                        getCancelKeyboard(newLang));
            } else {
                states.put(chatId, EmployerState.MAIN_MENU);
                return createMessage(chatId,
                        getText(newLang, "✅ Til O'zbek!", "✅ Язык Русский!", "✅ Language English!"),
                        getMainMenuKeyboard(newLang));
            }
        }

        // ===== КАНДИДАТЫ =====
        if (data.startsWith("cand_cat_")) {
            String cat = data.replace("cand_cat_", "");
            if (cat.equals("BACK")) {
                states.put(chatId, EmployerState.SEARCH_CANDIDATES);
                try {
                    EditMessageText edit = new EditMessageText();
                    edit.setChatId(chatId.toString());
                    edit.setMessageId(messageId);
                    SendMessage resp = showCandidateSearch(chatId, lang);
                    edit.setText(resp.getText());
                    edit.setParseMode("Markdown");
                    edit.setReplyMarkup((InlineKeyboardMarkup) resp.getReplyMarkup());
                    telegram.execute(edit);
                } catch (Exception e) { log.error("Edit error", e); }
                return null;
            }
            try {
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                SendMessage resp = showCandidatesByCategory(chatId, cat, lang);
                edit.setText(resp.getText());
                edit.setParseMode("Markdown");
                if (resp.getReplyMarkup() instanceof InlineKeyboardMarkup) {
                    edit.setReplyMarkup((InlineKeyboardMarkup) resp.getReplyMarkup());
                }
                telegram.execute(edit);
            } catch (Exception e) { log.error("Edit error", e); }
            return null;
        }

        if (data.startsWith("cand_view_")) {
            Long profileId = Long.parseLong(data.replace("cand_view_", ""));
            try {
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                SendMessage resp = showCandidateProfile(chatId, profileId, lang);
                edit.setText(resp.getText());
                edit.setParseMode("Markdown");
                if (resp.getReplyMarkup() instanceof InlineKeyboardMarkup) {
                    edit.setReplyMarkup((InlineKeyboardMarkup) resp.getReplyMarkup());
                }
                telegram.execute(edit);
            } catch (Exception e) { log.error("Edit error", e); }
            return null;
        }

        if (data.startsWith("cand_chat_")) {
            Long partnerId = Long.parseLong(data.replace("cand_chat_", ""));
            states.put(chatId, EmployerState.WAITING_FOR_CHAT_MESSAGE);
            draftData.get(chatId).put("chat_partner_id", String.valueOf(partnerId));
            Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findByUserId(partnerId);
            String name = seekerOpt.map(JobSeekerProfile::getFullName).orElse("User");
            return createMessage(chatId,
                    getText(lang, "✍️ **" + name + "ga yozing:**",
                            "✍️ **Напишите " + name + ":**",
                            "✍️ **Write to " + name + ":**"),
                    getChatKeyboard(lang));
        }

        if (data.startsWith("chat_open_")) {
            Long partnerId = Long.parseLong(data.replace("chat_open_", ""));
            try {
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                SendMessage resp = showChatMessages(chatId, partnerId, lang);
                edit.setText(resp.getText());
                edit.setParseMode("Markdown");
                telegram.execute(edit);
            } catch (Exception e) { log.error("Edit error", e); }
            return null;
        }

        // ===== КАТЕГОРИЯ ВАКАНСИИ =====
        if (state == EmployerState.WAITING_FOR_CATEGORY && data.startsWith("emp_cat_")) {
            String catValue = data.substring("emp_cat_".length());
            if (catValue.equals("OTHER")) {
                states.put(chatId, EmployerState.WAITING_FOR_CUSTOM_CATEGORY);
                return createMessage(chatId,
                        getText(lang, "📝 **Kategoriya nomi:**",
                                "📝 **Название категории:**", "📝 **Category name:**"),
                        getCancelKeyboard(lang));
            }
            if (catValue.equals("MENU")) {
                states.put(chatId, EmployerState.MAIN_MENU);
                return createMessage(chatId,
                        getText(lang, "📢 **Menyu:**", "📢 **Меню:**", "📢 **Menu:**"),
                        getMainMenuKeyboard(lang));
            }
            draftData.get(chatId).put("category", catValue);
            states.put(chatId, EmployerState.WAITING_FOR_JOB_TYPE);
            return createMessage(chatId,
                    getText(lang, "⏰ **Ish turi:**", "⏰ **Тип работы:**", "⏰ **Job type:**"),
                    getTypeKeyboard(lang));
        }

        // ===== АРХИВ ВАКАНСИИ =====
        if (data.startsWith("vac_toggle_")) {
            Long vacancyId = Long.parseLong(data.replace("vac_toggle_", ""));
            Optional<JobVacancy> vacOpt = jobVacancyRepository.findByIdAndEmployerChatId(vacancyId, chatId);
            if (vacOpt.isEmpty()) {
                sendAnswerCallback(callbackQuery.getId(),
                        getText(lang, "❌ Topilmadi!", "❌ Не найдено!", "❌ Not found!"), true);
                return null;
            }
            JobVacancy vac = vacOpt.get();
            boolean newStatus = vac.getIsActive() == null || !vac.getIsActive();
            vac.setIsActive(newStatus);
            if (!newStatus) vac.setVacancyStatus(null);
            jobVacancyRepository.save(vac);

            sendAnswerCallback(callbackQuery.getId(),
                    newStatus
                            ? getText(lang, "✅ Faollashtirildi!", "✅ Активирована!", "✅ Activated!")
                            : getText(lang, "🔴 Arxivlandi!", "🔴 Архивирована!", "🔴 Archived!"),
                    false);

            try {
                List<JobVacancy> allJobs = jobVacancyRepository.findAllByEmployerChatId(chatId);
                int index = 1;
                for (JobVacancy j : allJobs) {
                    if (j.getId().equals(vacancyId)) break;
                    index++;
                }
                SendMessage updatedCard = buildVacancyCard(chatId, vac, index, lang);
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                edit.setText(updatedCard.getText());
                edit.setParseMode("Markdown");
                edit.setReplyMarkup((InlineKeyboardMarkup) updatedCard.getReplyMarkup());
                telegram.execute(edit);
            } catch (Exception e) { log.error("Edit error", e); }
            return null;
        }

        // ===== TO'LDIRILDI =====
        if (data.startsWith("vac_filled_")) {
            Long vacancyId = Long.parseLong(data.replace("vac_filled_", ""));
            Optional<JobVacancy> vacOpt = jobVacancyRepository.findByIdAndEmployerChatId(vacancyId, chatId);
            if (vacOpt.isEmpty()) {
                sendAnswerCallback(callbackQuery.getId(),
                        getText(lang, "❌ Topilmadi!", "❌ Не найдено!", "❌ Not found!"), true);
                return null;
            }
            JobVacancy vac = vacOpt.get();
            boolean isFilled = "FILLED".equals(vac.getVacancyStatus());
            if (isFilled) {
                vac.setVacancyStatus(null);
                vac.setIsActive(true);
            } else {
                vac.setVacancyStatus("FILLED");
                vac.setIsActive(false);
            }
            jobVacancyRepository.save(vac);

            sendAnswerCallback(callbackQuery.getId(),
                    isFilled
                            ? getText(lang, "🟢 Faolga!", "🟢 Возвращено!", "🟢 Restored!")
                            : getText(lang, "✅ To'ldirildi!", "✅ Заполнено!", "✅ Filled!"),
                    false);

            try {
                List<JobVacancy> allJobs = jobVacancyRepository.findAllByEmployerChatId(chatId);
                int index = 1;
                for (JobVacancy j : allJobs) {
                    if (j.getId().equals(vacancyId)) break;
                    index++;
                }
                SendMessage updatedCard = buildVacancyCard(chatId, vac, index, lang);
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                edit.setText(updatedCard.getText());
                edit.setParseMode("Markdown");
                edit.setReplyMarkup((InlineKeyboardMarkup) updatedCard.getReplyMarkup());
                telegram.execute(edit);
            } catch (Exception e) { log.error("Edit error", e); }
            return null;
        }

        // ===== РЕДАКТ ВАКАНСИИ =====
        if (data.startsWith("vac_edit_") && !data.startsWith("vac_edit_field_")) {
            Long vacancyId = Long.parseLong(data.replace("vac_edit_", ""));
            draftData.get(chatId).put("edit_vacancy_id", String.valueOf(vacancyId));
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang,
                    "✏️ **Tahrirlash:**\n\nQaysi maydon?",
                    "✏️ **Редактировать:**\n\nКакое поле?",
                    "✏️ **Edit:**\n\nWhich field?"));
            edit.setParseMode("Markdown");
            edit.setReplyMarkup(buildEditVacancyKeyboard(vacancyId, lang));
            try { telegram.execute(edit); } catch (Exception e) { log.error("Edit error", e); }
            return null;
        }

        if (data.startsWith("vac_edit_field_")) {
            String[] parts = data.replace("vac_edit_field_", "").split("_");
            String field = parts[0];
            Long vacancyId = Long.parseLong(parts[1]);
            draftData.get(chatId).put("edit_vacancy_id", String.valueOf(vacancyId));
            draftData.get(chatId).put("edit_field", field);

            String promptText;
            switch (field) {
                case "title" -> {
                    states.put(chatId, EmployerState.WAITING_FOR_EDIT_VACANCY_TITLE);
                    promptText = getText(lang, "📝 **Yangi sarlavha:**", "📝 **Новый заголовок:**", "📝 **New title:**");
                }
                case "salary" -> {
                    states.put(chatId, EmployerState.WAITING_FOR_EDIT_VACANCY_SALARY);
                    promptText = getText(lang, "💰 **Yangi maosh:**", "💰 **Новая зарплата:**", "💰 **New salary:**");
                }
                case "requirements" -> {
                    states.put(chatId, EmployerState.WAITING_FOR_EDIT_VACANCY_REQUIREMENTS);
                    promptText = getText(lang, "📋 **Yangi talablar:**", "📋 **Новые требования:**", "📋 **New requirements:**");
                }
                case "workHours" -> {
                    states.put(chatId, EmployerState.WAITING_FOR_EDIT_VACANCY_WORK_HOURS);
                    promptText = getText(lang, "⏰ **Yangi ish vaqti:**", "⏰ **Новые часы:**", "⏰ **New hours:**");
                }
                default -> { return null; }
            }
            return createMessage(chatId, promptText, getCancelKeyboard(lang));
        }

        // ===== УДАЛЕНИЕ =====
        if (data.startsWith("vac_delete_")
                && !data.startsWith("vac_delete_confirm_")
                && !data.startsWith("vac_delete_cancel")) {
            Long vacancyId = Long.parseLong(data.replace("vac_delete_", ""));
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang, "🗑 **O'chirishni tasdiqlaysizmi?**",
                    "🗑 **Подтвердите удаление?**", "🗑 **Confirm deletion?**"));
            edit.setParseMode("Markdown");
            edit.setReplyMarkup(buildDeleteConfirmKeyboard(vacancyId, lang));
            try { telegram.execute(edit); } catch (Exception e) { log.error("Edit error", e); }
            return null;
        }

        if (data.startsWith("vac_delete_confirm_")) {
            Long vacancyId = Long.parseLong(data.replace("vac_delete_confirm_", ""));
            Optional<JobVacancy> vacOpt = jobVacancyRepository.findByIdAndEmployerChatId(vacancyId, chatId);
            if (vacOpt.isEmpty()) {
                sendAnswerCallback(callbackQuery.getId(),
                        getText(lang, "❌ Topilmadi!", "❌ Не найдено!", "❌ Not found!"), true);
                return null;
            }
            String deletedTitle = vacOpt.get().getTitle();
            jobVacancyRepository.delete(vacOpt.get());

            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang, "🗑 **O'chirildi:** " + deletedTitle,
                    "🗑 **Удалено:** " + deletedTitle, "🗑 **Deleted:** " + deletedTitle));
            edit.setParseMode("Markdown");
            try { telegram.execute(edit); } catch (Exception e) { log.error("Edit error", e); }
            return null;
        }

        if ("vac_delete_cancel".equals(data)) {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang, "❌ **Bekor qilindi.**", "❌ **Отменено.**", "❌ **Cancelled.**"));
            edit.setParseMode("Markdown");
            try { telegram.execute(edit); } catch (Exception e) { log.error("Edit error", e); }
            return null;
        }

        // ===== СТАТУС ЗАЯВКИ =====
        if (data.startsWith("emp_app_status_")) {
            String[] parts = data.replace("emp_app_status_", "").split("_");
            String action = parts[0];
            Long appId = Long.parseLong(parts[1]);

            Optional<JobApplication> appOpt = jobApplicationRepository.findById(appId);
            if (appOpt.isEmpty()) {
                sendAnswerCallback(callbackQuery.getId(),
                        getText(lang, "❌ Ariza topilmadi!", "❌ Заявка не найдена!", "❌ Not found!"), true);
                return null;
            }

            JobApplication app = appOpt.get();
            boolean belongsToEmployer = jobVacancyRepository
                    .findByIdAndEmployerChatId(app.getJobId(), chatId).isPresent();
            if (!belongsToEmployer) {
                sendAnswerCallback(callbackQuery.getId(),
                        getText(lang, "❌ Ruxsat yo'q!", "❌ Нет доступа!", "❌ No access!"), true);
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
                    getText(lang, "✅ Yangilandi!", "✅ Обновлено!", "✅ Updated!"),
                    false);

            try {
                Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findById(app.getJobSeekerId());
                if (seekerOpt.isPresent()) {
                    Long seekerChatId = seekerOpt.get().getUserId();
                    String jobTitle = jobVacancyRepository.findById(app.getJobId())
                            .map(JobVacancy::getTitle).orElse("Vakansiya");

                    String notificationText;
                    switch (newStatus) {
                        case VIEWED -> notificationText = getText(lang,
                                "👁 Arizangiz ko'rildi: «" + jobTitle + "»",
                                "👁 Ваша заявка просмотрена: «" + jobTitle + "»",
                                "👁 Application viewed: «" + jobTitle + "»");
                        case INTERVIEW -> notificationText = getText(lang,
                                "📅 Suhbatga chaqirildingiz: «" + jobTitle + "»",
                                "📅 Вас пригласили на собеседование: «" + jobTitle + "»",
                                "📅 Interview: «" + jobTitle + "»");
                        case ACCEPTED -> notificationText = getText(lang,
                                "🎉 Tabriklaymiz! «" + jobTitle + "»",
                                "🎉 Поздравляем! «" + jobTitle + "»",
                                "🎉 Congratulations! «" + jobTitle + "»");
                        case REJECTED -> notificationText = getText(lang,
                                "😔 Afsuski, rad etildi: «" + jobTitle + "»",
                                "😔 К сожалению, отклонено: «" + jobTitle + "»",
                                "😔 Rejected: «" + jobTitle + "»");
                        default -> notificationText = "";
                    }

                    if (!notificationText.isEmpty()) {
                        SendMessage notifyMsg = new SendMessage(seekerChatId.toString(), notificationText);
                        notifyMsg.setParseMode("Markdown");
                        telegram.execute(notifyMsg);
                    }
                }
            } catch (Exception e) { log.error("Notify error", e); }

            try {
                SendMessage updated = showApplications(chatId, lang);
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                edit.setText(updated.getText());
                edit.setParseMode("Markdown");
                if (updated.getReplyMarkup() instanceof InlineKeyboardMarkup) {
                    edit.setReplyMarkup((InlineKeyboardMarkup) updated.getReplyMarkup());
                }
                telegram.execute(edit);
            } catch (Exception e) { log.error("Edit error", e); }
            return null;
        }

        sendAnswerCallback(callbackQuery.getId(), null, false);
        return null;
    }

    // ============================================
    // DASHBOARD
    // ============================================
    private SendMessage showDashboard(Long chatId, String lang) {
        List<JobVacancy> myVacancies = jobVacancyRepository.findAllByEmployerChatId(chatId);
        List<JobApplication> allApps = jobApplicationRepository.findAllByEmployerChatId(chatId);

        long totalVacancies = myVacancies.size();
        long activeVacancies = myVacancies.stream()
                .filter(v -> v.getIsActive() != null && v.getIsActive()
                        && !"FILLED".equals(v.getVacancyStatus())).count();
        long filledVacancies = myVacancies.stream()
                .filter(v -> "FILLED".equals(v.getVacancyStatus())).count();
        long archivedVacancies = totalVacancies - activeVacancies - filledVacancies;

        long totalApps = allApps.size();
        long pendingApps = allApps.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.PENDING).count();

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long todayApps = allApps.stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(todayStart)).count();

        StringBuilder sb = new StringBuilder();
        sb.append(getText(lang,
                "╔══════════════════════════════╗\n║   📊 ISH BERUVCHI PANELI    ║\n╚══════════════════════════════╝\n\n",
                "╔══════════════════════════════╗\n║  📊 ПАНЕЛЬ РАБОТОДАТЕЛЯ     ║\n╚══════════════════════════════╝\n\n",
                "╔══════════════════════════════╗\n║   📊 EMPLOYER DASHBOARD    ║\n╚══════════════════════════════╝\n\n"));

        sb.append(getText(lang, "📋 VAKANSIYALAR:\n", "📋 ВАКАНСИИ:\n", "📋 VACANCIES:\n"));
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(getText(lang, "   📌 Jami: ", "   📌 Всего: ", "   📌 Total: ")).append(totalVacancies).append("\n");
        sb.append(getText(lang, "   🟢 Faol: ", "   🟢 Активных: ", "   🟢 Active: ")).append(activeVacancies).append("\n");
        sb.append(getText(lang, "   🟣 To'ldirildi: ", "   🟣 Заполнено: ", "   🟣 Filled: ")).append(filledVacancies).append("\n");
        sb.append(getText(lang, "   🔴 Arxiv: ", "   🔴 Архив: ", "   🔴 Archived: ")).append(archivedVacancies).append("\n\n");

        sb.append(getText(lang, "📩 ARIZALAR:\n", "📩 ЗАЯВКИ:\n", "📩 APPLICATIONS:\n"));
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(getText(lang, "   📊 Jami: ", "   📊 Всего: ", "   📊 Total: ")).append(totalApps).append("\n");
        sb.append(getText(lang, "   ⏳ Yangi: ", "   ⏳ Новых: ", "   ⏳ New: ")).append(pendingApps).append("\n");
        sb.append(getText(lang, "   🌞 Bugun: ", "   🌞 Сегодня: ", "   🌞 Today: ")).append(todayApps).append("\n");

        return createMessage(chatId, sb.toString(), getMainMenuKeyboard(lang));
    }

    // ============================================
    // КАНДИДАТЫ
    // ============================================
    private SendMessage showCandidateSearch(Long chatId, String lang) {
        String text = getText(lang,
                "🔍 **Nomzod izlash:**\n\nKategoriyani tanlang:",
                "🔍 **Поиск кандидатов:**\n\nВыберите категорию:",
                "🔍 **Search candidates:**\n\nChoose category:");

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
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn1 = new InlineKeyboardButton();
            btn1.setText(categories.get(i));
            btn1.setCallbackData("cand_cat_" + categories.get(i));
            row.add(btn1);
            if (i + 1 < categories.size()) {
                InlineKeyboardButton btn2 = new InlineKeyboardButton();
                btn2.setText(categories.get(i + 1));
                btn2.setCallbackData("cand_cat_" + categories.get(i + 1));
                row.add(btn2);
            }
            rows.add(row);
        }

        InlineKeyboardButton allBtn = new InlineKeyboardButton();
        allBtn.setText(getText(lang, "🌐 Barcha nomzodlar", "🌐 Все кандидаты", "🌐 All candidates"));
        allBtn.setCallbackData("cand_cat_ALL");
        rows.add(List.of(allBtn));

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        return message;
    }

    private SendMessage showCandidatesByCategory(Long chatId, String category, String lang) {
        List<JobSeekerProfile> allCandidates = jobSeekerProfileRepository.findAll();
        List<JobSeekerProfile> filtered = "ALL".equals(category)
                ? allCandidates
                : allCandidates.stream()
                .filter(p -> p.getCategory() != null && p.getCategory().equalsIgnoreCase(category))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return createMessage(chatId,
                    getText(lang, "📭 Topilmadi.", "📭 Не найдено.", "📭 Not found."),
                    getMainMenuKeyboard(lang));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(getText(lang,
                "👥 Topildi: " + filtered.size() + "\n\n",
                "👥 Найдено: " + filtered.size() + "\n\n",
                "👥 Found: " + filtered.size() + "\n\n"));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < Math.min(filtered.size(), 10); i++) {
            JobSeekerProfile p = filtered.get(i);
            String name = p.getFullName() != null ? p.getFullName() : "Nomzod";
            String prof = p.getProfession() != null ? p.getProfession() : "-";
            String rating = p.getRating() != null ? String.format("%.1f", p.getRating()) : "0.0";

            sb.append(i + 1).append(". 👤 **").append(name).append("**\n")
                    .append("   💼 ").append(prof).append("\n")
                    .append("   ⭐ ").append(rating).append("\n\n");

            InlineKeyboardButton viewBtn = new InlineKeyboardButton();
            viewBtn.setText((i + 1) + "️⃣ " + name);
            viewBtn.setCallbackData("cand_view_" + p.getId());
            rows.add(List.of(viewBtn));
        }

        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText(getText(lang, "⬅️ Orqaga", "⬅️ Назад", "⬅️ Back"));
        backBtn.setCallbackData("cand_cat_BACK");
        rows.add(List.of(backBtn));

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        return message;
    }

    private SendMessage showCandidateProfile(Long chatId, Long candidateId, String lang) {
        Optional<JobSeekerProfile> candOpt = jobSeekerProfileRepository.findById(candidateId);
        if (candOpt.isEmpty()) {
            return createMessage(chatId,
                    getText(lang, "❌ Topilmadi!", "❌ Не найден!", "❌ Not found!"),
                    getMainMenuKeyboard(lang));
        }

        JobSeekerProfile c = candOpt.get();
        String name = c.getFullName() != null ? c.getFullName() : "Nomzod";
        String phone = c.getPhoneNumber() != null ? c.getPhoneNumber() : "-";
        String prof = c.getProfession() != null ? c.getProfession() : "-";
        String rating = c.getRating() != null ? String.format("%.1f ⭐", c.getRating()) : "0.0 ⭐";

        StringBuilder sb = new StringBuilder();
        sb.append("👤 **").append(name).append("**\n");
        sb.append("📞 ").append(phone).append("\n");
        sb.append("💼 ").append(prof).append("\n");
        sb.append("⭐ ").append(rating).append("\n");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton chatBtn = new InlineKeyboardButton();
        chatBtn.setText(getText(lang, "💬 Xabar yozish", "💬 Написать", "💬 Message"));
        chatBtn.setCallbackData("cand_chat_" + c.getUserId());
        rows.add(List.of(chatBtn));

        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText(getText(lang, "⬅️ Orqaga", "⬅️ Назад", "⬅️ Back"));
        backBtn.setCallbackData("cand_cat_ALL");
        rows.add(List.of(backBtn));

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        return message;
    }

    // ============================================
    // ЧАТ
    // ============================================
    private SendMessage showChatMenu(Long chatId, String lang) {
        List<Long> partners = chatMessageRepository.findAllChatPartners(chatId);

        if (partners.isEmpty()) {
            return createMessage(chatId,
                    getText(lang, "💬 Chatlar yo'q.", "💬 Чатов нет.", "💬 No chats."),
                    getMainMenuKeyboard(lang));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(getText(lang, "💬 **Chatlar:**\n\n", "💬 **Чаты:**\n\n", "💬 **Chats:**\n\n"));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Long partnerId : partners) {
            Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findByUserId(partnerId);
            String name = seekerOpt.map(JobSeekerProfile::getFullName).orElse("User");
            long unread = chatMessageRepository
                    .countByReceiverChatIdAndSenderChatIdAndIsReadFalse(chatId, partnerId);
            String badge = unread > 0 ? " 🔴 (" + unread + ")" : "";
            sb.append("👤 ").append(name).append(badge).append("\n");

            InlineKeyboardButton chatBtn = new InlineKeyboardButton();
            chatBtn.setText("💬 " + name + badge);
            chatBtn.setCallbackData("chat_open_" + partnerId);
            rows.add(List.of(chatBtn));
        }

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        return message;
    }

    private SendMessage showChatMessages(Long chatId, Long partnerId, String lang) {
        List<ChatMessage> messages = chatMessageRepository.findConversation(chatId, partnerId);

        for (ChatMessage m : messages) {
            if (m.getReceiverChatId().equals(chatId) && (m.getIsRead() == null || !m.getIsRead())) {
                m.setIsRead(true);
                chatMessageRepository.save(m);
            }
        }

        Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findByUserId(partnerId);
        String partnerName = seekerOpt.map(JobSeekerProfile::getFullName).orElse("User");

        StringBuilder sb = new StringBuilder();
        sb.append(getText(lang, "💬 Chat: " + partnerName + "\n\n",
                "💬 Чат: " + partnerName + "\n\n", "💬 Chat: " + partnerName + "\n\n"));

        if (messages.isEmpty()) {
            sb.append(getText(lang, "(Bo'sh)\n", "(Пусто)\n", "(Empty)\n"));
        } else {
            int start = Math.max(0, messages.size() - 20);
            for (int i = start; i < messages.size(); i++) {
                ChatMessage m = messages.get(i);
                boolean isMine = m.getSenderChatId().equals(chatId);
                sb.append(isMine ? "👤 Siz: " : "👥 " + partnerName + ": ")
                        .append(m.getMessageText()).append("\n\n");
            }
        }
        sb.append(getText(lang, "\n✍️ Yozing:", "\n✍️ Напишите:", "\n✍️ Write:"));

        states.put(chatId, EmployerState.WAITING_FOR_CHAT_MESSAGE);
        draftData.get(chatId).put("chat_partner_id", String.valueOf(partnerId));

        return createMessage(chatId, sb.toString(), getChatKeyboard(lang));
    }

    private ReplyKeyboardMarkup getChatKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "❌ Bekor", "❌ Отмена", "❌ Cancel")),
                List.of(getText(lang, "🏠 Menyu", "🏠 Меню", "🏠 Menu"))
        ));
    }

    // ============================================
    // РЕЙТИНГ
    // ============================================
    private SendMessage showEmployerRating(Long chatId, String lang) {
        Double avgRating = employerReviewRepository.getAverageRating(chatId);
        long reviewCount = employerReviewRepository.countByEmployerChatId(chatId);
        List<EmployerReview> reviews = employerReviewRepository.findByEmployerChatIdOrderByCreatedAtDesc(chatId);

        double rating = avgRating != null ? avgRating : 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append(getText(lang, "⭐ **Reyting:**\n\n", "⭐ **Рейтинг:**\n\n", "⭐ **Rating:**\n\n"));
        sb.append("⭐ **").append(String.format("%.1f", rating)).append(" / 5.0**\n");
        sb.append(getText(lang, "📊 Baholar: ", "📊 Оценок: ", "📊 Reviews: ")).append(reviewCount).append("\n\n");

        if (!reviews.isEmpty()) {
            int count = 0;
            for (EmployerReview r : reviews) {
                if (count >= 5) break;
                String stars = "⭐".repeat(r.getRating());
                String comment = r.getComment() != null && !r.getComment().isBlank()
                        ? r.getComment() : "-";
                sb.append(stars).append(" — ").append(comment).append("\n");
                count++;
            }
        } else {
            sb.append(getText(lang, "📭 Baholar yo'q.", "📭 Отзывов нет.", "📭 No reviews."));
        }

        return createMessage(chatId, sb.toString(), getMainMenuKeyboard(lang));
    }

    // ============================================
    // ЗАЯВКИ
    // ============================================
    private SendMessage showApplications(Long chatId, String lang) {
        List<JobApplication> applications = jobApplicationRepository.findAllByEmployerChatId(chatId);

        if (applications == null || applications.isEmpty()) {
            return createMessage(chatId,
                    getText(lang, "📭 Arizalar yo'q.", "📭 Заявок нет.", "📭 No applications."),
                    getMainMenuKeyboard(lang));
        }

        StringBuilder sb = new StringBuilder(getText(lang,
                "📩 **Arizalar:**\n\n", "📩 **Заявки:**\n\n", "📩 **Applications:**\n\n"));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < applications.size(); i++) {
            JobApplication app = applications.get(i);
            String jobTitle = jobVacancyRepository.findById(app.getJobId())
                    .map(JobVacancy::getTitle).orElse("Vakansiya #" + app.getJobId());
            Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findById(app.getJobSeekerId());
            String candidateName = seekerOpt.map(JobSeekerProfile::getFullName).orElse("Nomzod");

            sb.append(i + 1).append(". 📌 ").append(jobTitle).append("\n");
            sb.append("   👤 ").append(candidateName).append("\n");
            sb.append("   📊 ").append(getStatusText(app.getStatus(), lang)).append("\n\n");

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
        if (lang.equals("en")) {
            return switch (status) {
                case PENDING -> "⏳ Pending";
                case VIEWED -> "👁 Viewed";
                case INTERVIEW -> "📅 Interview";
                case ACCEPTED -> "✅ Accepted";
                case REJECTED -> "❌ Rejected";
                case CANCELLED -> "🚫 Cancelled";
            };
        }
        return switch (status) {
            case PENDING -> "⏳ Kutilmoqda";
            case VIEWED -> "👁 Ko'rilgan";
            case INTERVIEW -> "📅 Suhbat";
            case ACCEPTED -> "✅ Qabul";
            case REJECTED -> "❌ Rad etilgan";
            case CANCELLED -> "🚫 Bekor";
        };
    }

    // ============================================
    // РЕГИСТРАЦИЯ
    // ============================================
    private SendMessage handleRegistration(Long chatId, String text, Message message, String lang, Optional<EmployerEntity> profileOpt) {
        EmployerState regState = states.getOrDefault(chatId, EmployerState.WAITING_FOR_LANGUAGE);

        if (regState == EmployerState.WAITING_FOR_LANGUAGE) {
            return createInlineLanguageMessage(chatId,
                    "🌐 **Tilni tanlang / Выберите язык / Choose language:**");
        }

        if (regState == EmployerState.WAITING_FOR_REG_FULL_NAME) {
            if (text.isEmpty() || text.startsWith("/")) {
                return createMessage(chatId,
                        getText(lang, "👤 **Ism-familiya:**", "👤 **Имя и фамилия:**", "👤 **Name:**"),
                        getCancelKeyboard(lang));
            }
            if (text.length() < 3) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Min 3 belgi:", "⚠️ Мин 3:", "⚠️ Min 3:"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("reg_fio", text);
            states.put(chatId, EmployerState.WAITING_FOR_REG_AGE);
            return createMessage(chatId,
                    getText(lang, "🎂 **Yosh (18+):**", "🎂 **Возраст (18+):**", "🎂 **Age (18+):**"),
                    getCancelKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_AGE) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId,
                        getText(lang, "👤 Ism-familiya:", "👤 Имя:", "👤 Name:"),
                        getCancelKeyboard(lang));
            }
            if (!isValidAge(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ 18-99:", "⚠️ 18-99:", "⚠️ 18-99:"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("reg_age", text);
            states.put(chatId, EmployerState.WAITING_FOR_REG_PASSPORT);
            return createMessage(chatId,
                    getText(lang, "🪪 **Pasport (AA1234567):**",
                            "🪪 **Паспорт (AA1234567):**",
                            "🪪 **Passport (AA1234567):**"),
                    getCancelKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_PASSPORT) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                draftData.get(chatId).clear();
                return createMessage(chatId,
                        getText(lang, "👤 Ism-familiya:", "👤 Имя:", "👤 Name:"),
                        getCancelKeyboard(lang));
            }
            if (!PASSPORT_PATTERN.matcher(text.trim()).matches()) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Format AA1234567:",
                                "⚠️ Формат AA1234567:",
                                "⚠️ Format AA1234567:"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("reg_passport", text.trim().toUpperCase());
            states.put(chatId, EmployerState.WAITING_FOR_REG_PHONE);
            return createMessage(chatId,
                    getText(lang, "📞 **Telefon:**", "📞 **Телефон:**", "📞 **Phone:**"),
                    getContactKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_PHONE) {
            String phone = message.hasContact() ? message.getContact().getPhoneNumber() : text;
            if (!isValidPhone(phone)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ +998901234567", "⚠️ +998901234567", "⚠️ +998901234567"),
                        getContactKeyboard(lang));
            }
            draftData.get(chatId).put("reg_phone", normalizePhone(phone));
            states.put(chatId, EmployerState.WAITING_FOR_REG_COMPANY);
            return createMessage(chatId,
                    getText(lang, "🏢 **Kompaniya nomi:**\n(Yo'q bo'lsa \"Yo'q\")",
                            "🏢 **Название компании:**\n(Если нет, \"Нет\")",
                            "🏢 **Company name:**\n(If none, \"No\")"),
                    getCancelKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_COMPANY) {
            if (text == null || text.isBlank()) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Kiriting yoki \"Yo'q\":",
                                "⚠️ Введите или \"Нет\":",
                                "⚠️ Enter or \"No\":"),
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

            String companyName = isNoCompanyText(text) ? null : text;
            entity.setCompanyName(companyName);
            if (entity.getLanguage() == null) entity.setLanguage(lang);
            if (entity.getCreatedAt() == null) entity.setCreatedAt(LocalDateTime.now());

            employerRepository.save(entity);
            states.put(chatId, EmployerState.MAIN_MENU);
            draftData.get(chatId).clear();

            return createMessage(chatId,
                    getText(lang,
                            "✅ **Ro'yxatdan o'tdingiz!**" + (companyName != null ? "\n🏢 " + companyName : ""),
                            "✅ **Регистрация завершена!**" + (companyName != null ? "\n🏢 " + companyName : ""),
                            "✅ **Registration complete!**" + (companyName != null ? "\n🏢 " + companyName : "")),
                    getMainMenuKeyboard(lang));
        }

        states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
        return createMessage(chatId,
                getText(lang, "👤 Ism-familiya:", "👤 Имя:", "👤 Name:"),
                getCancelKeyboard(lang));
    }

    // ============================================
    // КОМПАНИЯ
    // ============================================
    private SendMessage showCompanyInfo(Long chatId, Optional<EmployerEntity> profileOpt, String lang) {
        if (profileOpt.isEmpty()) {
            states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
            return createMessage(chatId,
                    getText(lang, "❌ Topilmadi!", "❌ Не найден!", "❌ Not found!"));
        }

        EmployerEntity e = profileOpt.get();
        String ns = getText(lang, "Ko'rsatilmagan", "Не указано", "N/A");

        String company = e.getCompanyName() != null && !e.getCompanyName().isBlank() ? e.getCompanyName() : ns;
        String desc = e.getCompanyDescription() != null && !e.getCompanyDescription().isBlank() ? e.getCompanyDescription() : ns;
        String address = e.getCompanyAddress() != null && !e.getCompanyAddress().isBlank() ? e.getCompanyAddress() : ns;
        String website = e.getCompanyWebsite() != null && !e.getCompanyWebsite().isBlank() ? e.getCompanyWebsite() : ns;
        String size = e.getCompanySize() != null && !e.getCompanySize().isBlank() ? e.getCompanySize() : ns;
        String logo = e.getCompanyLogoFileId() != null ? "✅" : "❌";

        String msg = getText(lang,
                String.format("🏢 **Kompaniya:**\n\n🏷 %s\n📝 %s\n📍 %s\n🌐 %s\n👥 %s\n🖼 %s",
                        company, desc, address, website, size, logo),
                String.format("🏢 **Компания:**\n\n🏷 %s\n📝 %s\n📍 %s\n🌐 %s\n👥 %s\n🖼 %s",
                        company, desc, address, website, size, logo),
                String.format("🏢 **Company:**\n\n🏷 %s\n📝 %s\n📍 %s\n🌐 %s\n👥 %s\n🖼 %s",
                        company, desc, address, website, size, logo));

        return createMessage(chatId, msg, getCompanyInfoKeyboard(lang));
    }

    // ============================================
    // БЛОКИРОВКА
    // ============================================
    private SendMessage showBlockedCandidates(Long chatId, Optional<EmployerEntity> profileOpt, String lang) {
        if (profileOpt.isEmpty()) {
            states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
            return createMessage(chatId,
                    getText(lang, "❌ Topilmadi!", "❌ Не найден!", "❌ Not found!"));
        }

        String blocked = profileOpt.get().getBlockedCandidateIds();
        String list;

        if (blocked == null || blocked.isBlank()) {
            list = getText(lang, "📭 Bo'sh.", "📭 Пусто.", "📭 Empty.");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(getText(lang, "🚫 **Bloklangan:**\n\n",
                    "🚫 **Заблокированные:**\n\n", "🚫 **Blocked:**\n\n"));
            String[] ids = blocked.split(",");
            for (int i = 0; i < ids.length; i++) {
                sb.append(i + 1).append(". <code>").append(ids[i]).append("</code>\n");
            }
            list = sb.toString();
        }

        SendMessage msg = new SendMessage(chatId.toString(), list);
        msg.setParseMode("HTML");
        msg.setReplyMarkup(getBlockedCandidatesKeyboard(lang));
        return msg;
    }

    // ============================================
    // РЕДАКТ ВАКАНСИИ
    // ============================================
    private SendMessage handleEditVacancyField(Long chatId, String text, String field, String lang,
                                               String uzError, String ruError, String enError) {
        if (text == null || text.isBlank()) {
            return createMessage(chatId,
                    getText(lang, "⚠️ " + uzError, "⚠️ " + ruError, "⚠️ " + enError),
                    getCancelKeyboard(lang));
        }

        String vacancyIdStr = draftData.get(chatId).get("edit_vacancy_id");
        if (vacancyIdStr == null) {
            states.put(chatId, EmployerState.MAIN_MENU);
            return createMessage(chatId,
                    getText(lang, "❌ Xatolik!", "❌ Ошибка!", "❌ Error!"),
                    getMainMenuKeyboard(lang));
        }

        Long vacancyId = Long.parseLong(vacancyIdStr);
        Optional<JobVacancy> vacOpt = jobVacancyRepository.findByIdAndEmployerChatId(vacancyId, chatId);
        if (vacOpt.isEmpty()) {
            states.put(chatId, EmployerState.MAIN_MENU);
            return createMessage(chatId,
                    getText(lang, "❌ Topilmadi!", "❌ Не найдено!", "❌ Not found!"),
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
                getText(lang, "✅ Yangilandi!", "✅ Обновлено!", "✅ Updated!"),
                getMainMenuKeyboard(lang));
    }

    // ============================================
    // СОЗДАНИЕ ВАКАНСИИ (без запроса телефона)
    // ============================================
    private SendMessage createVacancyAndNotify(Long chatId, String phoneText,
                                               Optional<EmployerEntity> profileOpt, String lang) {
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
        vacancy.setVacancyStatus(null);
        vacancy.setCompanyName(profileOpt.map(EmployerEntity::getCompanyName).orElse(null));

        jobVacancyRepository.save(vacancy);

        try {
            List<Subscription> subscribers = subscriptionRepository.findByCategory(vacancy.getCategory());
            for (Subscription sub : subscribers) {
                Long seekerUserId = sub.getUserId();
                String notificationText = getText(lang,
                        String.format("🆕 **Yangi ish!**\n\n📌 %s\n📂 %s\n💰 %s",
                                vacancy.getTitle(), vacancy.getCategory(), vacancy.getSalary()),
                        String.format("🆕 **Новая работа!**\n\n📌 %s\n📂 %s\n💰 %s",
                                vacancy.getTitle(), vacancy.getCategory(), vacancy.getSalary()),
                        String.format("🆕 **New job!**\n\n📌 %s\n📂 %s\n💰 %s",
                                vacancy.getTitle(), vacancy.getCategory(), vacancy.getSalary()));

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                InlineKeyboardButton applyBtn = new InlineKeyboardButton("📩 Ariza topshirish");
                applyBtn.setCallbackData("apply_" + vacancy.getId());
                markup.setKeyboard(List.of(List.of(applyBtn)));

                SendMessage notificationMsg = new SendMessage(seekerUserId.toString(), notificationText);
                notificationMsg.setParseMode("Markdown");
                notificationMsg.setReplyMarkup(markup);
                try { telegram.execute(notificationMsg); } catch (Exception ignored) {}
            }
        } catch (Exception e) { log.error("Notify error", e); }

        states.put(chatId, EmployerState.MAIN_MENU);
        draftData.get(chatId).clear();

        String successMsg = getText(lang,
                String.format("🎉 **E'lon chop etildi!**\n\n📌 %s\n📁 %s",
                        vacancy.getTitle(), vacancy.getCategory()),
                String.format("🎉 **Вакансия опубликована!**\n\n📌 %s\n📁 %s",
                        vacancy.getTitle(), vacancy.getCategory()),
                String.format("🎉 **Published!**\n\n📌 %s\n📁 %s",
                        vacancy.getTitle(), vacancy.getCategory()));

        return createMessage(chatId, successMsg, getMainMenuKeyboard(lang));
    }

    // ============================================
    // ПРОФИЛЬ
    // ============================================
    private SendMessage showProfile(Long chatId, Optional<EmployerEntity> profileOpt, String lang) {
        if (profileOpt.isEmpty() || profileOpt.get().getFullName() == null) {
            states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
            return createMessage(chatId,
                    getText(lang, "👤 Ism-familiya:", "👤 Имя:", "👤 Name:"),
                    getCancelKeyboard(lang));
        }

        EmployerEntity emp = profileOpt.get();
        String ns = getText(lang, "Ko'rsatilmagan", "Не указано", "N/A");
        String company = emp.getCompanyName() != null && !emp.getCompanyName().isBlank() ? emp.getCompanyName() : ns;
        String ageStr = emp.getAge() != null ? String.valueOf(emp.getAge()) : "-";
        String passport = emp.getPassportSeriesNumber() != null ? emp.getPassportSeriesNumber() : "-";

        String msg = getText(lang,
                String.format("👤 **Profil:**\n\n🆔 %d\n👤 %s\n🎂 %s\n🪪 %s\n🏢 %s\n📞 %s\n🌐 %s",
                        chatId, emp.getFullName(), ageStr, passport, company,
                        emp.getPhoneNumber() != null ? emp.getPhoneNumber() : ns,
                        emp.getLanguage() != null ? emp.getLanguage() : "uz"),
                String.format("👤 **Профиль:**\n\n🆔 %d\n👤 %s\n🎂 %s\n🪪 %s\n🏢 %s\n📞 %s\n🌐 %s",
                        chatId, emp.getFullName(), ageStr, passport, company,
                        emp.getPhoneNumber() != null ? emp.getPhoneNumber() : ns,
                        emp.getLanguage() != null ? emp.getLanguage() : "uz"),
                String.format("👤 **Profile:**\n\n🆔 %d\n👤 %s\n🎂 %s\n🪪 %s\n🏢 %s\n📞 %s\n🌐 %s",
                        chatId, emp.getFullName(), ageStr, passport, company,
                        emp.getPhoneNumber() != null ? emp.getPhoneNumber() : ns,
                        emp.getLanguage() != null ? emp.getLanguage() : "uz"));

        return createMessage(chatId, msg, getProfileKeyboard(lang));
    }

    // ============================================
    // УВЕДОМЛЕНИЕ (для TelegramCallbackService)
    // ============================================
    public SendMessage buildApplicationNotification(
            Long employerChatId, Long applicationId, Long employeeChatId,
            String vacancyTitle, String candidateName, String candidatePhone, String lang) {

        String text = getText(lang,
                String.format("🔔 **Yangi ariza!**\n\n📌 %s\n👤 %s\n📞 %s",
                        vacancyTitle, candidateName, candidatePhone),
                String.format("🔔 **Новый отклик!**\n\n📌 %s\n👤 %s\n📞 %s",
                        vacancyTitle, candidateName, candidatePhone),
                String.format("🔔 **New application!**\n\n📌 %s\n👤 %s\n📞 %s",
                        vacancyTitle, candidateName, candidatePhone));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton acceptBtn = new InlineKeyboardButton();
        acceptBtn.setText(getText(lang, "✅ Qabul", "✅ Принять", "✅ Accept"));
        acceptBtn.setCallbackData("accept_app_" + applicationId + "_" + employeeChatId);

        InlineKeyboardButton rejectBtn = new InlineKeyboardButton();
        rejectBtn.setText(getText(lang, "❌ Rad", "❌ Отклонить", "❌ Reject"));
        rejectBtn.setCallbackData("reject_app_" + applicationId + "_" + employeeChatId);

        markup.setKeyboard(List.of(List.of(acceptBtn, rejectBtn)));

        SendMessage message = new SendMessage(employerChatId.toString(), text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        return message;
    }

    // ============================================
    // ХЕЛПЕРЫ
    // ============================================
    private boolean isCancelText(String text) {
        if (text == null) return false;
        String t = text.trim();
        return CANCEL_WORDS.stream().anyMatch(c -> c.equalsIgnoreCase(t));
    }

    private boolean isNoCompanyText(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("yo'q") || t.equals("йўқ") || t.equals("нет")
                || t.equals("no") || t.equals("компания йўқ")
                || t.equals("компания нет") || t.equals("kompaniya yo'q");
    }

    private boolean isValidAge(String text) {
        if (text == null || !text.matches("^[1-9]\\d{1,2}$")) return false;
        int age = Integer.parseInt(text);
        return age >= 18 && age <= 99;
    }

    private boolean isValidPhone(String text) {
        if (text == null) return false;
        String clean = text.replaceAll("[\\s\\-()]", "");
        if (clean.matches("^[0-9]{9}$")) clean = "+998" + clean;
        if (clean.matches("^998[0-9]{9}$")) clean = "+" + clean;
        return clean.matches("^\\+998[0-9]{9}$");
    }

    private String normalizePhone(String text) {
        if (text == null) return null;
        String clean = text.replaceAll("[\\s\\-()]", "");
        if (clean.matches("^[0-9]{9}$")) return "+998" + clean;
        if (clean.matches("^998[0-9]{9}$")) return "+" + clean;
        return clean;
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

    private boolean isGlobalButton(String text) {
        return text != null && (
                text.equals("/start") || text.equals("/menu") ||
                        text.equals("👤 Profilim") || text.equals("👤 Профиль") || text.equals("👤 Profile") ||
                        text.equals("✏️ Profilni tahrirlash") || text.equals("✏️ Редактировать профиль") || text.equals("✏️ Edit profile") ||
                        text.equals("🏢 Kompaniya") || text.equals("🏢 Компания") || text.equals("🏢 Company") ||
                        text.equals("⚙️ Sozlamalar") || text.equals("⚙️ Настройки") || text.equals("⚙️ Settings") ||
                        text.equals("📢 Vakansiya yaratish") || text.equals("📢 Создать вакансию") || text.equals("📢 Create vacancy") ||
                        text.equals("📋 Mening vakansiyalarim") || text.equals("📋 Мои вакансии") || text.equals("📋 My vacancies") ||
                        text.equals("📩 Arizalar") || text.equals("📩 Заявки") || text.equals("📩 Applications") ||
                        text.equals("📊 Dashboard") || text.equals("📊 Дашборд") ||
                        text.equals("💬 Chatlar") || text.equals("💬 Чаты") || text.equals("💬 Chats") ||
                        text.equals("🔍 Nomzod izlash") || text.equals("🔍 Поиск кандидатов") || text.equals("🔍 Search candidates") ||
                        text.equals("⭐ Reytingim") || text.equals("⭐ Мой рейтинг") || text.equals("⭐ My rating") ||
                        text.equals("📊 Statistika") || text.equals("📊 Статистика") || text.equals("📊 Statistics") ||
                        text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("⬅️ Back") ||
                        text.equals("❌ Bekor qilish") || text.equals("❌ Отмена") || text.equals("❌ Cancel") ||
                        text.equals("🏠 Asosiy menyu") || text.equals("🏠 Главное меню") || text.equals("🏠 Main menu")
        );
    }

    private boolean isSkip(String text) {
        return text.equals("⏭ O'tkazib yuborish") || text.equals("⏭ Пропустить") || text.equals("⏭ Skip");
    }

    private String getText(String lang, String uz, String ru, String en) {
        switch (lang) {
            case "ru": return ru;
            case "en": return en;
            default: return uz;
        }
    }

    private SendMessage createMessage(Long chatId, String text) {
        return createMessage(chatId, text, null);
    }

    private SendMessage createMessage(Long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");
        if (keyboard != null) message.setReplyMarkup(keyboard);
        return message;
    }

    private SendMessage createInlineLanguageMessage(Long chatId, String prompt) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(prompt);
        msg.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder().text("🇺🇿 O'zbek").callbackData("lang_uz").build());
        row.add(InlineKeyboardButton.builder().text("🇷🇺 Русский").callbackData("lang_ru").build());
        row.add(InlineKeyboardButton.builder().text("🇬🇧 English").callbackData("lang_en").build());

        markup.setKeyboard(List.of(row));
        msg.setReplyMarkup(markup);
        return msg;
    }

    private void sendAnswerCallback(String id, String text, boolean alert) {
        try {
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery ans =
                    new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            ans.setCallbackQueryId(id);
            if (text != null) {
                ans.setText(text);
                ans.setShowAlert(alert);
            }
            telegram.execute(ans);
        } catch (Exception e) {
            log.error("AnswerCallback error", e);
        }
    }

    // ============================================
    // КЛАВИАТУРЫ
    // ============================================
    private ReplyKeyboardMarkup getMainMenuKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "📊 Dashboard", "📊 Дашборд", "📊 Dashboard")),
                List.of(getText(lang, "📢 Vakansiya yaratish", "📢 Создать вакансию", "📢 Create vacancy")),
                List.of(getText(lang, "📋 Mening vakansiyalarim", "📋 Мои вакансии", "📋 My vacancies")),
                List.of(getText(lang, "📩 Arizalar", "📩 Заявки", "📩 Applications"),
                        getText(lang, "💬 Chatlar", "💬 Чаты", "💬 Chats")),
                List.of(getText(lang, "🔍 Nomzod izlash", "🔍 Поиск кандидатов", "🔍 Search candidates")),
                List.of(getText(lang, "👤 Profilim", "👤 Профиль", "👤 Profile"),
                        getText(lang, "🏢 Kompaniya", "🏢 Компания", "🏢 Company")),
                List.of(getText(lang, "⭐ Reytingim", "⭐ Мой рейтинг", "⭐ My rating"),
                        getText(lang, "⚙️ Sozlamalar", "⚙️ Настройки", "⚙️ Settings")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню", "🏠 Main menu"))
        ));
    }

    private ReplyKeyboardMarkup getProfileKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "✏️ Profilni tahrirlash", "✏️ Редактировать профиль", "✏️ Edit profile")),
                List.of(getText(lang, "🏢 Kompaniya", "🏢 Компания", "🏢 Company")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню", "🏠 Main menu"))
        ));
    }

    private ReplyKeyboardMarkup getEditProfileKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "👤 Ismni tahrirlash", "👤 Изменить имя", "👤 Change name")),
                List.of(getText(lang, "🎂 Yoshni tahrirlash", "🎂 Изменить возраст", "🎂 Change age")),
                List.of(getText(lang, "🏢 Kompaniya nomini tahrirlash", "🏢 Изменить компанию", "🏢 Change company")),
                List.of(getText(lang, "⬅️ Orqaga", "⬅️ Назад", "⬅️ Back"))
        ));
    }

    private ReplyKeyboardMarkup getCompanyInfoKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "📝 Kompaniya tavsifi", "📝 Описание компании", "📝 Description")),
                List.of(getText(lang, "📍 Manzil", "📍 Адрес", "📍 Address")),
                List.of(getText(lang, "🌐 Veb-sayt", "🌐 Веб-сайт", "🌐 Website")),
                List.of(getText(lang, "👥 Xodimlar soni", "👥 Сотрудники", "👥 Employees")),
                List.of(getText(lang, "🖼 Logotip", "🖼 Логотип", "🖼 Logo")),
                List.of(getText(lang, "⬅️ Orqaga", "⬅️ Назад", "⬅️ Back"))
        ));
    }

    private ReplyKeyboardMarkup getCompanySizeKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of("1-10", "11-50"),
                List.of("51-200", "200+"),
                List.of(getText(lang, "❌ Bekor qilish", "❌ Отмена", "❌ Cancel"))
        ));
    }

    private ReplyKeyboardMarkup getBlockedCandidatesKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "➕ Nomzodni bloklash", "➕ Заблокировать", "➕ Block")),
                List.of(getText(lang, "✅ Blokdan chiqarish", "✅ Разблокировать", "✅ Unblock")),
                List.of(getText(lang, "⬅️ Orqaga", "⬅️ Назад", "⬅️ Back"))
        ));
    }

    private ReplyKeyboardMarkup getSettingsKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "🌐 Til", "🌐 Язык", "🌐 Language")),
                List.of(getText(lang, "🚫 Bloklangan nomzodlar", "🚫 Заблокированные", "🚫 Blocked")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню", "🏠 Main menu"))
        ));
    }

    private ReplyKeyboardMarkup getCancelKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "❌ Bekor qilish", "❌ Отмена", "❌ Cancel")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню", "🏠 Main menu"))
        ));
    }

    private ReplyKeyboardMarkup getContactKeyboard(String lang) {
        KeyboardButton btn = new KeyboardButton(getText(lang, "📞 Raqamni yuborish", "📞 Отправить номер", "📞 Send phone"));
        btn.setRequestContact(true);
        KeyboardButton cancel = new KeyboardButton(getText(lang, "❌ Bekor qilish", "❌ Отмена", "❌ Cancel"));
        KeyboardButton mainMenu = new KeyboardButton(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню", "🏠 Main menu"));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        KeyboardRow row1 = new KeyboardRow(); row1.add(btn);
        KeyboardRow row2 = new KeyboardRow(); row2.add(cancel);
        KeyboardRow row3 = new KeyboardRow(); row3.add(mainMenu);
        markup.setKeyboard(List.of(row1, row2, row3));
        markup.setResizeKeyboard(true);
        return markup;
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
        otherBtn.setText(getText(lang, "➕ Boshqa", "➕ Другое", "➕ Other"));
        otherBtn.setCallbackData("emp_cat_OTHER");
        rows.add(List.of(otherBtn));

        InlineKeyboardButton menuBtn = new InlineKeyboardButton();
        menuBtn.setText(getText(lang, "🏠 Menyu", "🏠 Меню", "🏠 Menu"));
        menuBtn.setCallbackData("emp_cat_MENU");
        rows.add(List.of(menuBtn));

        markup.setKeyboard(rows);
        return markup;
    }

    private ReplyKeyboardMarkup getTypeKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "To'liq bandlik", "Полная занятость", "Full-time")),
                List.of(getText(lang, "Qisman bandlik", "Частичная занятость", "Part-time")),
                List.of(getText(lang, "Masofaviy", "Удаленная", "Remote")),
                List.of(getText(lang, "🏠 Menyu", "🏠 Меню", "🏠 Menu"))
        ));
    }

    private ReplyKeyboardMarkup getWorkHoursKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of("09:00 - 18:00", "08:00 - 17:00"),
                List.of(getText(lang, "Moslashuvchan", "Гибкий", "Flexible")),
                List.of(getText(lang, "🏠 Menyu", "🏠 Меню", "🏠 Menu"))
        ));
    }

    private ReplyKeyboardMarkup getSalaryQuickKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "Kelishiladi", "Договорная", "Negotiable")),
                List.of(getText(lang, "🏠 Menyu", "🏠 Меню", "🏠 Menu"))
        ));
    }

    private ReplyKeyboardMarkup getSkipKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "⏭ O'tkazib yuborish", "⏭ Пропустить", "⏭ Skip")),
                List.of(getText(lang, "🏠 Menyu", "🏠 Меню", "🏠 Menu"))
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
    // МОИ ВАКАНСИИ
    // ============================================
    private SendMessage showMyVacanciesWithButtons(Long chatId, String lang) {
        List<JobVacancy> myJobs = jobVacancyRepository.findAllByEmployerChatId(chatId);

        if (myJobs.isEmpty()) {
            return createMessage(chatId,
                    getText(lang, "📭 Vakansiyalar yo'q.", "📭 Вакансий нет.", "📭 No vacancies."),
                    getMainMenuKeyboard(lang));
        }

        SendMessage header = new SendMessage();
        header.setChatId(chatId.toString());
        header.setParseMode("Markdown");
        header.setText(getText(lang,
                "📋 **Vakansiyalar:** Jami " + myJobs.size(),
                "📋 **Вакансии:** Всего " + myJobs.size(),
                "📋 **Vacancies:** Total " + myJobs.size()));
        try { telegram.execute(header); } catch (Exception e) { log.error("Header error", e); }

        for (int i = 0; i < myJobs.size(); i++) {
            SendMessage card = buildVacancyCard(chatId, myJobs.get(i), i + 1, lang);
            try { telegram.execute(card); } catch (Exception e) { log.error("Card error", e); }
        }
        return null;
    }

    private SendMessage buildVacancyCard(Long chatId, JobVacancy job, int index, String lang) {
        boolean isActive = job.getIsActive() == null || job.getIsActive();
        boolean isFilled = "FILLED".equals(job.getVacancyStatus());

        String emoji, statusText;
        if (isFilled) {
            emoji = "🟣";
            statusText = getText(lang, "To'ldirildi", "Заполнено", "Filled");
        } else if (isActive) {
            emoji = "🟢";
            statusText = getText(lang, "Faol", "Активна", "Active");
        } else {
            emoji = "🔴";
            statusText = getText(lang, "Arxiv", "Архив", "Archived");
        }

        String jobTitle = job.getTitle() != null ? job.getTitle() : "Nomsiz";
        String salary = job.getSalary() != null ? job.getSalary() : "Kelishiladi";

        StringBuilder sb = new StringBuilder();
        sb.append(index).append("️⃣ **").append(jobTitle).append("**\n");
        sb.append("💰 ").append(salary).append("\n");
        sb.append(emoji).append(" ").append(statusText).append("\n");
        if (job.getCategory() != null) sb.append("📁 ").append(job.getCategory()).append("\n");

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton editBtn = new InlineKeyboardButton();
        editBtn.setText(getText(lang, "✏️ Tahrirlash", "✏️ Изменить", "✏️ Edit"));
        editBtn.setCallbackData("vac_edit_" + job.getId());
        row1.add(editBtn);

        InlineKeyboardButton toggleBtn = new InlineKeyboardButton();
        toggleBtn.setText(isActive
                ? getText(lang, "🔴 Arxiv", "🔴 Архив", "🔴 Archive")
                : getText(lang, "🟢 Faol", "🟢 Активировать", "🟢 Activate"));
        toggleBtn.setCallbackData("vac_toggle_" + job.getId());
        row1.add(toggleBtn);
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton filledBtn = new InlineKeyboardButton();
        filledBtn.setText(isFilled
                ? getText(lang, "🟢 Faol", "🟢 Вернуть", "🟢 Restore")
                : getText(lang, "✅ To'ldirildi", "✅ Заполнено", "✅ Filled"));
        filledBtn.setCallbackData("vac_filled_" + job.getId());
        row2.add(filledBtn);

        InlineKeyboardButton deleteBtn = new InlineKeyboardButton();
        deleteBtn.setText(getText(lang, "🗑 O'chirish", "🗑 Удалить", "🗑 Delete"));
        deleteBtn.setCallbackData("vac_delete_" + job.getId());
        row2.add(deleteBtn);
        rows.add(row2);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        return message;
    }

    private InlineKeyboardMarkup buildEditVacancyKeyboard(Long vacancyId, String lang) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton titleBtn = new InlineKeyboardButton();
        titleBtn.setText(getText(lang, "📝 Sarlavha", "📝 Название", "📝 Title"));
        titleBtn.setCallbackData("vac_edit_field_title_" + vacancyId);
        rows.add(List.of(titleBtn));

        InlineKeyboardButton salaryBtn = new InlineKeyboardButton();
        salaryBtn.setText(getText(lang, "💰 Maosh", "💰 Зарплата", "💰 Salary"));
        salaryBtn.setCallbackData("vac_edit_field_salary_" + vacancyId);
        rows.add(List.of(salaryBtn));

        InlineKeyboardButton reqBtn = new InlineKeyboardButton();
        reqBtn.setText(getText(lang, "📋 Talablar", "📋 Требования", "📋 Requirements"));
        reqBtn.setCallbackData("vac_edit_field_requirements_" + vacancyId);
        rows.add(List.of(reqBtn));

        InlineKeyboardButton hoursBtn = new InlineKeyboardButton();
        hoursBtn.setText(getText(lang, "⏰ Ish vaqti", "⏰ Часы", "⏰ Hours"));
        hoursBtn.setCallbackData("vac_edit_field_workHours_" + vacancyId);
        rows.add(List.of(hoursBtn));

        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup buildDeleteConfirmKeyboard(Long vacancyId, String lang) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText(getText(lang, "✅ Ha", "✅ Да", "✅ Yes"));
        confirmBtn.setCallbackData("vac_delete_confirm_" + vacancyId);

        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText(getText(lang, "❌ Bekor", "❌ Отмена", "❌ Cancel"));
        cancelBtn.setCallbackData("vac_delete_cancel");

        markup.setKeyboard(List.of(List.of(confirmBtn, cancelBtn)));
        return markup;
    }
}