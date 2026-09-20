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
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+998[0-9]{9}$");

    private static final List<String> CANCEL_WORDS = List.of(
            "❌ Bekor qilish", "❌ Отмена", "Bekor qilish", "Отмена",
            "Yo'q", "Йўқ", "Нет", "Нет компании", "Kompaniya yo'q"
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
    // ASOSIY METOD
    // ============================================
    public SendMessage handleEmployer(Message message) {
        if (message == null) return null;

        Long chatId = message.getChatId();
        String text = message.hasText() ? message.getText().trim() : "";
        draftData.putIfAbsent(chatId, new ConcurrentHashMap<>());

        Optional<EmployerEntity> profileOpt = employerRepository.findByEmployerChatId(chatId);
        String lang = profileOpt.map(EmployerEntity::getLanguage).orElse("uz");

        // ===== LOGOTIP YUKLASH =====
        EmployerState currentState = states.getOrDefault(chatId, EmployerState.MAIN_MENU);
        if (currentState == EmployerState.WAITING_FOR_COMPANY_LOGO) {
            if (message.hasPhoto() || (message.hasDocument() &&
                    message.getDocument().getMimeType() != null &&
                    message.getDocument().getMimeType().startsWith("image/"))) {

                String fileId;
                if (message.hasPhoto()) {
                    fileId = message.getPhoto().get(message.getPhoto().size() - 1).getFileId();
                } else {
                    fileId = message.getDocument().getFileId();
                }

                if (profileOpt.isEmpty()) {
                    states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                    return createMessage(chatId, getText(lang, "❌ Profil topilmadi.", "❌ Профиль не найден."));
                }
                EmployerEntity entity = profileOpt.get();
                entity.setCompanyLogoFileId(fileId);
                employerRepository.save(entity);
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return createMessage(chatId,
                        getText(lang, "✅ Logotip saqlandi!", "✅ Логотип сохранён!"),
                        getCompanyInfoKeyboard(lang));
            }
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            return createMessage(chatId,
                    getText(lang, "⚠️ Iltimos, rasm yuboring yoki bekor qiling:",
                            "⚠️ Отправьте изображение или отмените:"),
                    getCancelKeyboard(lang));
        }

        // ===== CHAT XABAR YUBORISH =====
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
                        getText(lang, "⚠️ Xabar 1-4000 belgi orasida bo'lishi kerak:",
                                "⚠️ Сообщение от 1 до 4000 символов:"),
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
                                "💬 **Новое сообщение:**\n\n" + text));
                notifyMsg.setParseMode("Markdown");
                telegram.execute(notifyMsg);
            } catch (Exception e) {
                log.error("❌ Chat xabar yuborishda xatolik", e);
            }

            return showChatMessages(chatId, partnerId, lang);
        }

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
                                "🏠 **Xush kelibsiz!**\n\nKerakli bo'limni tanlang:",
                                "🏠 **Добро пожаловать!**\n\nВыберите нужный раздел:"),
                        getMainMenuKeyboard(lang));
            }

            if (text.equals("📊 Dashboard") || text.equals("📊 Дашборд")) {
                states.put(chatId, EmployerState.DASHBOARD);
                return showDashboard(chatId, lang);
            }

            if (text.equals("👤 Profilim") || text.equals("👤 Профиль")) {
                return showProfile(chatId, profileOpt, lang);
            }

            if (text.equals("🏢 Kompaniya") || text.equals("🏢 Компания")) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }

            if (text.equals("✏️ Profilni tahrirlash") || text.equals("✏️ Редактировать профиль")) {
                states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
                return createMessage(chatId,
                        getText(lang,
                                "✏️ **Profilni tahrirlash:**\n\nNimani o'zgartirmoqchisiz?",
                                "✏️ **Редактировать профиль:**\n\nЧто хотите изменить?"),
                        getEditProfileKeyboard(lang));
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
                                "📝 **Ish sarlavhasini kiriting:**",
                                "📝 **Введите название вакансии:**"),
                        getCancelKeyboard(lang));
            }

            if (text.equals("📋 Mening vakansiyalarim") || text.equals("📋 Мои вакансии")) {
                return showMyVacanciesWithButtons(chatId, lang);
            }

            if (text.equals("📩 Arizalar") || text.equals("📩 Заявки")) {
                states.put(chatId, EmployerState.VIEWING_APPLICATIONS);
                return showApplications(chatId, lang);
            }

            // ===== 1. DASHBOARD (дублирующая кнопка, оставляю на всякий) =====
            if (text.equals("📊 Statistika") || text.equals("📊 Статистика")) {
                states.put(chatId, EmployerState.DASHBOARD);
                return showDashboard(chatId, lang);
            }

            // ===== 2. NOMZOD IZLASH =====
            if (text.equals("🔍 Nomzod izlash") || text.equals("🔍 Поиск кандидатов")) {
                states.put(chatId, EmployerState.SEARCH_CANDIDATES);
                return showCandidateSearch(chatId, lang);
            }

            // ===== 3. CHATLAR =====
            if (text.equals("💬 Chatlar") || text.equals("💬 Чаты")) {
                states.put(chatId, EmployerState.CHAT_MENU);
                return showChatMenu(chatId, lang);
            }

            // ===== 4. REYTING =====
            if (text.equals("⭐ Reytingim") || text.equals("⭐ Мой рейтинг")) {
                states.put(chatId, EmployerState.RATING_MENU);
                return showEmployerRating(chatId, lang);
            }

            if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("❌ Bekor qilish") || text.equals("❌ Отмена")) {
                return createMessage(chatId,
                        getText(lang, "📢 **Ish beruvchi menyusi:**", "📢 **Меню работодателя:**"),
                        getMainMenuKeyboard(lang));
            }
        }

        EmployerState state = states.getOrDefault(chatId, EmployerState.MAIN_MENU);

        // ===== DASHBOARD =====
        if (state == EmployerState.DASHBOARD) {
            return showDashboard(chatId, lang);
        }

        // ===== NOMZODLAR BAZASI =====
        if (state == EmployerState.SEARCH_CANDIDATES) {
            if (isCancelText(text) || text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад")) {
                states.put(chatId, EmployerState.MAIN_MENU);
                return createMessage(chatId,
                        getText(lang, "📢 **Ish beruvchi menyusi:**", "📢 **Меню работодателя:**"),
                        getMainMenuKeyboard(lang));
            }
            return showCandidateSearch(chatId, lang);
        }

        // ===== CHAT MENU =====
        if (state == EmployerState.CHAT_MENU) {
            if (isCancelText(text) || text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад")) {
                states.put(chatId, EmployerState.MAIN_MENU);
                return createMessage(chatId,
                        getText(lang, "📢 **Ish beruvchi menyusi:**", "📢 **Меню работодателя:**"),
                        getMainMenuKeyboard(lang));
            }
            return showChatMenu(chatId, lang);
        }

        // ===== REYTING =====
        if (state == EmployerState.RATING_MENU) {
            return showEmployerRating(chatId, lang);
        }

        // ===== KOMPANIYA MA'LUMOTLARI =====
        if (state == EmployerState.COMPANY_INFO_MENU) {
            if (isCancelText(text) || text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад")) {
                states.put(chatId, EmployerState.MAIN_MENU);
                return showProfile(chatId, profileOpt, lang);
            }
            if (text.equals("📝 Kompaniya tavsifi") || text.equals("📝 Описание компании")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY_DESCRIPTION);
                return createMessage(chatId,
                        getText(lang, "📝 **Kompaniya haqida ma'lumot kiriting:**\n\n(Max 500 belgi)",
                                "📝 **Введите описание компании:**\n\n(Макс 500 символов)"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("📍 Manzil") || text.equals("📍 Адрес")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY_ADDRESS);
                return createMessage(chatId,
                        getText(lang, "📍 **Kompaniya manzilini kiriting:**",
                                "📍 **Введите адрес компании:**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("🌐 Veb-sayt") || text.equals("🌐 Веб-сайт")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY_WEBSITE);
                return createMessage(chatId,
                        getText(lang, "🌐 **Kompaniya veb-sayti yoki Telegram kanali:**\n\n(Masalan: https://t.me/kanal)",
                                "🌐 **Веб-сайт или Telegram-канал компании:**\n\n(Например: https://t.me/kanal)"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("👥 Xodimlar soni") || text.equals("👥 Количество сотрудников")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY_SIZE);
                return createMessage(chatId,
                        getText(lang, "👥 **Xodimlar soni:**\n\n(Masalan: 1-10, 11-50, 51-200, 200+)",
                                "👥 **Количество сотрудников:**\n\n(Например: 1-10, 11-50, 51-200, 200+)"),
                        getCompanySizeKeyboard(lang));
            }
            if (text.equals("🖼 Logotip") || text.equals("🖼 Логотип")) {
                states.put(chatId, EmployerState.WAITING_FOR_COMPANY_LOGO);
                return createMessage(chatId,
                        getText(lang, "🖼 **Kompaniya logotipini yuboring (rasm):**",
                                "🖼 **Отправьте логотип компании (изображение):**"),
                        getCancelKeyboard(lang));
            }
            return showCompanyInfo(chatId, profileOpt, lang);
        }

        // ===== KOMPANIYA TAVSIFI =====
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY_DESCRIPTION) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            if (text.isBlank() || text.length() > 500) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Matn 1-500 belgi orasida bo'lishi kerak:",
                                "⚠️ Текст должен быть от 1 до 500 символов:"),
                        getCancelKeyboard(lang));
            }
            if (profileOpt.isEmpty()) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId, getText(lang, "❌ Profil topilmadi.", "❌ Профиль не найден."));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setCompanyDescription(text);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.COMPANY_INFO_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Kompaniya tavsifi saqlandi!", "✅ Описание компании сохранено!"),
                    getCompanyInfoKeyboard(lang));
        }

        // ===== KOMPANIYA MANZILI =====
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY_ADDRESS) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            if (text.isBlank() || text.length() > 200) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Manzil 1-200 belgi orasida:", "⚠️ Адрес от 1 до 200 символов:"),
                        getCancelKeyboard(lang));
            }
            if (profileOpt.isEmpty()) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId, getText(lang, "❌ Profil topilmadi.", "❌ Профиль не найден."));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setCompanyAddress(text);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.COMPANY_INFO_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Manzil saqlandi!", "✅ Адрес сохранён!"),
                    getCompanyInfoKeyboard(lang));
        }

        // ===== KOMPANIYA VEB-SAYTI =====
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY_WEBSITE) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            if (text.isBlank() || text.length() > 200) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Havola 1-200 belgi orasida:", "⚠️ Ссылка от 1 до 200 символов:"),
                        getCancelKeyboard(lang));
            }
            if (profileOpt.isEmpty()) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId, getText(lang, "❌ Profil topilmadi.", "❌ Профиль не найден."));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setCompanyWebsite(text);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.COMPANY_INFO_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Veb-sayt saqlandi!", "✅ Веб-сайт сохранён!"),
                    getCompanyInfoKeyboard(lang));
        }

        // ===== KOMPANIYA HAJMI =====
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY_SIZE) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.COMPANY_INFO_MENU);
                return showCompanyInfo(chatId, profileOpt, lang);
            }
            if (text.isBlank() || text.length() > 50) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Iltimos, tugmalardan birini tanlang:", "⚠️ Выберите одну из кнопок:"),
                        getCompanySizeKeyboard(lang));
            }
            if (profileOpt.isEmpty()) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId, getText(lang, "❌ Profil topilmadi.", "❌ Профиль не найден."));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setCompanySize(text);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.COMPANY_INFO_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Xodimlar soni saqlandi!", "✅ Количество сотрудников сохранено!"),
                    getCompanyInfoKeyboard(lang));
        }

        // ===== BLOKLANGAN NOMZODLAR MENYUSI =====
        if (state == EmployerState.BLOCKED_CANDIDATES_MENU) {
            if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || isCancelText(text)) {
                states.put(chatId, EmployerState.SETTINGS_MENU);
                return createMessage(chatId,
                        getText(lang, "⚙️ **Sozlamalar bo'limi:**", "⚙️ **Раздел настроек:**"),
                        getSettingsKeyboard(lang));
            }
            if (text.equals("➕ Nomzodni bloklash") || text.equals("➕ Заблокировать кандидата")) {
                states.put(chatId, EmployerState.WAITING_FOR_BLOCK_CANDIDATE_ID);
                return createMessage(chatId,
                        getText(lang, "🚫 **Bloklash uchun nomzodning Telegram ID raqamini kiriting:**\n\n(Masalan: 123456789)",
                                "🚫 **Введите Telegram ID кандидата для блокировки:**\n\n(Например: 123456789)"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("✅ Blokdan chiqarish") || text.equals("✅ Разблокировать")) {
                states.put(chatId, EmployerState.WAITING_FOR_BLOCK_CANDIDATE_ID);
                return createMessage(chatId,
                        getText(lang, "✅ **Blokdan chiqarish uchun nomzodning Telegram ID raqamini kiriting:**",
                                "✅ **Введите Telegram ID кандидата для разблокировки:**"),
                        getCancelKeyboard(lang));
            }
            return showBlockedCandidates(chatId, profileOpt, lang);
        }

        // ===== NOMZODNI BLOKLASH =====
        if (state == EmployerState.WAITING_FOR_BLOCK_CANDIDATE_ID) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.BLOCKED_CANDIDATES_MENU);
                return showBlockedCandidates(chatId, profileOpt, lang);
            }
            if (text == null || !text.matches("^\\d+$")) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Faqat raqam kiriting:", "⚠️ Введите только цифры:"),
                        getCancelKeyboard(lang));
            }
            if (profileOpt.isEmpty()) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId, getText(lang, "❌ Profil topilmadi.", "❌ Профиль не найден."));
            }

            Long candidateId = Long.parseLong(text);
            if (candidateId.equals(chatId)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ O'zingizni bloklay olmaysiz!", "⚠️ Вы не можете заблокировать себя!"),
                        getCancelKeyboard(lang));
            }

            EmployerEntity entity = profileOpt.get();
            String blocked = entity.getBlockedCandidateIds();

            List<String> list;
            if (blocked == null || blocked.isBlank()) {
                list = new ArrayList<>();
            } else {
                list = new ArrayList<>(List.of(blocked.split(",")));
            }

            if (list.contains(String.valueOf(candidateId))) {
                list.remove(String.valueOf(candidateId));
                entity.setBlockedCandidateIds(list.isEmpty() ? null : String.join(",", list));
                employerRepository.save(entity);
                states.put(chatId, EmployerState.BLOCKED_CANDIDATES_MENU);
                return createMessage(chatId,
                        getText(lang, "✅ Nomzod blokdan chiqarildi! (ID: " + candidateId + ")",
                                "✅ Кандидат разблокирован! (ID: " + candidateId + ")"),
                        getBlockedCandidatesKeyboard(lang));
            } else {
                list.add(String.valueOf(candidateId));
                entity.setBlockedCandidateIds(String.join(",", list));
                employerRepository.save(entity);
                states.put(chatId, EmployerState.BLOCKED_CANDIDATES_MENU);
                return createMessage(chatId,
                        getText(lang, "✅ Nomzod bloklandi! (ID: " + candidateId + ")",
                                "✅ Кандидат заблокирован! (ID: " + candidateId + ")"),
                        getBlockedCandidatesKeyboard(lang));
            }
        }

        // ===== PROFILNI TAHRIRLASH MENYUSI =====
        if (state == EmployerState.EDIT_PROFILE_MENU) {
            if (text.equals("👤 Ismni tahrirlash") || text.equals("👤 Изменить имя")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_NAME);
                return createMessage(chatId,
                        getText(lang, "👤 **Yangi ism va familiyani kiriting:**", "👤 **Введите новое имя и фамилию:**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("🎂 Yoshni tahrirlash") || text.equals("🎂 Изменить возраст")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_AGE);
                return createMessage(chatId,
                        getText(lang, "🎂 **Yangi yoshni kiriting (18+):**", "🎂 **Введите новый возраст (18+):**"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("🏢 Kompaniya nomini tahrirlash") || text.equals("🏢 Изменить название компании")) {
                states.put(chatId, EmployerState.WAITING_FOR_EDIT_COMPANY);
                return createMessage(chatId,
                        getText(lang,
                                "🏢 **Kompaniya nomini kiriting:**\n\n(Agar kompaniya yo'q bo'lsa, \"Yo'q\" deb yozing)",
                                "🏢 **Введите название компании:**\n\n(Если компании нет, напишите \"Нет\")"),
                        getCancelKeyboard(lang));
            }
            if (text.equals("⬅️ Orqaga") || text.equals("⬅️ Назад") || text.equals("❌ Bekor qilish") || text.equals("❌ Отмена")) {
                states.put(chatId, EmployerState.MAIN_MENU);
                return showProfile(chatId, profileOpt, lang);
            }
            return createMessage(chatId,
                    getText(lang, "✏️ **Profilni tahrirlash:**", "✏️ **Редактировать профиль:**"),
                    getEditProfileKeyboard(lang));
        }

        // ===== ISMNI TAHRIRLASH =====
        if (state == EmployerState.WAITING_FOR_EDIT_NAME) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
                return createMessage(chatId,
                        getText(lang, "❌ Tahrirlash bekor qilindi.", "❌ Редактирование отменено."),
                        getEditProfileKeyboard(lang));
            }
            if (text.isBlank() || text.length() < 3) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Ism kamida 3 ta belgidan iborat bo'lishi kerak. Qaytadan kiriting:",
                                "⚠️ Имя должно содержать минимум 3 символа. Введите снова:"),
                        getCancelKeyboard(lang));
            }
            if (profileOpt.isEmpty()) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId,
                        getText(lang, "❌ Profil topilmadi. Qaytadan ro'yxatdan o'ting.",
                                "❌ Профиль не найден. Зарегистрируйтесь снова."));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setFullName(text);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Ism muvaffaqiyatli yangilandi!", "✅ Имя успешно обновлено!"),
                    getEditProfileKeyboard(lang));
        }

        // ===== YOSHNI TAHRIRLASH =====
        if (state == EmployerState.WAITING_FOR_EDIT_AGE) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
                return createMessage(chatId,
                        getText(lang, "❌ Tahrirlash bekor qilindi.", "❌ Редактирование отменено."),
                        getEditProfileKeyboard(lang));
            }
            if (!isValidAge(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Yosh 18 dan katta va 99 dan kichik bo'lishi kerak. Qaytadan kiriting:",
                                "⚠️ Возраст должен быть от 18 до 99 лет. Введите снова:"),
                        getCancelKeyboard(lang));
            }
            if (profileOpt.isEmpty()) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId,
                        getText(lang, "❌ Profil topilmadi. Qaytadan ro'yxatdan o'ting.",
                                "❌ Профиль не найден. Зарегистрируйтесь снова."));
            }
            EmployerEntity entity = profileOpt.get();
            entity.setAge(Integer.parseInt(text));
            employerRepository.save(entity);
            states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Yosh muvaffaqiyatli yangilandi!", "✅ Возраст успешно обновлён!"),
                    getEditProfileKeyboard(lang));
        }

        // ===== KOMPANIYA NOMINI TAHRIRLASH =====
        if (state == EmployerState.WAITING_FOR_EDIT_COMPANY) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
                return createMessage(chatId,
                        getText(lang, "❌ Tahrirlash bekor qilindi.", "❌ Редактирование отменено."),
                        getEditProfileKeyboard(lang));
            }
            if (text == null || text.isBlank()) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Iltimos, kompaniya nomini kiriting yoki \"Yo'q\" deb yozing:",
                                "⚠️ Введите название компании или напишите \"Нет\":"),
                        getCancelKeyboard(lang));
            }
            if (profileOpt.isEmpty()) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId,
                        getText(lang, "❌ Profil topilmadi. Qaytadan ro'yxatdan o'ting.",
                                "❌ Профиль не найден. Зарегистрируйтесь снова."));
            }

            String companyName = isNoCompanyText(text) ? null : text;
            EmployerEntity entity = profileOpt.get();
            entity.setCompanyName(companyName);
            employerRepository.save(entity);
            states.put(chatId, EmployerState.EDIT_PROFILE_MENU);
            return createMessage(chatId,
                    getText(lang, "✅ Kompaniya nomi yangilandi!", "✅ Название компании обновлено!"),
                    getEditProfileKeyboard(lang));
        }

        // ===== SOZLAMALAR =====
        if (state == EmployerState.SETTINGS_MENU) {
            if (text.equals("🌐 Til") || text.equals("🌐 Язык")) {
                states.put(chatId, EmployerState.WAITING_FOR_LANGUAGE);
                return createMessage(chatId, "🌐 **Tilni tanlang / Выберите язык:**", getLanguageKeyboard());
            }
            if (text.equals("🚫 Bloklangan nomzodlar") || text.equals("🚫 Заблокированные кандидаты")) {
                states.put(chatId, EmployerState.BLOCKED_CANDIDATES_MENU);
                return showBlockedCandidates(chatId, profileOpt, lang);
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

        // ===== ARIZALAR =====
        if (state == EmployerState.VIEWING_APPLICATIONS) {
            return showApplications(chatId, lang);
        }

        // ===== STATISTIKA (Dashboard bilan bir xil) =====
        if (state == EmployerState.VIEWING_STATISTICS) {
            return showDashboard(chatId, lang);
        }

        // ===== VAKANSIYANI TAHRIRLASH =====
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

        // ===== VAKANSIYA YARATISH =====
        if (state == EmployerState.WAITING_FOR_TITLE) {
            if (!isValidTitle(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ **Sarlavha noto'g'ri!** 3-100 ta belgi bo'lishi kerak:",
                                "⚠️ **Неверный заголовок!** Должно быть 3-100 символов:"),
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
                        getText(lang, "⚠️ **Kategoriya juda qisqa:**", "⚠️ **Категория слишком короткая:**"),
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
                    getText(lang, "⌛️ **Ish soatlarini tanlang:**", "⌛️ **Выберите часы работы:**"),
                    getWorkHoursKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_WORK_HOURS) {
            if (!isValidWorkHours(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ **Ish soati noto'g'ri!**", "⚠️ **Неверные часы!**"),
                        getWorkHoursKeyboard(lang));
            }
            draftData.get(chatId).put("workHours", text);
            states.put(chatId, EmployerState.WAITING_FOR_WORKER_COUNT);
            return createMessage(chatId,
                    getText(lang, "👥 **Nechta xodim kerak?**", "👥 **Сколько сотрудников нужно?**"),
                    getCancelKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_WORKER_COUNT) {
            if (!isValidWorkerCount(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ **Faqat musbat raqam kiriting:**", "⚠️ **Введите только положительное число:**"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("workerCount", text);
            states.put(chatId, EmployerState.WAITING_FOR_SALARY);
            return createMessage(chatId,
                    getText(lang, "💰 **Maoshni kiriting:**", "💰 **Введите зарплату:**"),
                    getSalaryQuickKeyboard(lang));
        }

        if (state == EmployerState.WAITING_FOR_SALARY) {
            if (!isValidSalary(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ **Maosh noto'g'ri:**", "⚠️ **Неверная зарплата:**"),
                        getSalaryQuickKeyboard(lang));
            }
            draftData.get(chatId).put("salary", text);
            states.put(chatId, EmployerState.WAITING_FOR_REQUIREMENTS);
            return createMessage(chatId,
                    getText(lang, "📌 **Talablarni kiriting:**", "📌 **Введите требования:**"),
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
                        getText(lang, "⚠️ **Telefon raqam noto'g'ri!** Format: `+998901234567`",
                                "⚠️ **Неверный номер!** Формат: `+998901234567`"),
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
            vacancy.setVacancyStatus(null);
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
                                    "💰 **Maosh:** %s",
                            vacancy.getTitle(),
                            vacancy.getCategory() != null ? vacancy.getCategory() : "Ko'rsatilmagan",
                            vacancy.getSalary() != null ? vacancy.getSalary() : "Kelishiladi"
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
                    String.format("🎉 **Вакансия опубликована!**\n\n📌 **Название:** %s\n📁 **Категория:** %s",
                            vacancy.getTitle(), vacancy.getCategory()) :
                    String.format("🎉 **E'loningiz chop etildi!**\n\n📌 **Nomi:** %s\n📁 **Kategoriya:** %s",
                            vacancy.getTitle(), vacancy.getCategory());

            return createMessage(chatId, successMsg, getMainMenuKeyboard(lang));
        }

        return createMessage(chatId,
                getText(lang, "📢 **Ish beruvchi menyusi:**", "📢 **Меню работодателя:**"),
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

        // ===== NOMZODLAR BAZASI CALLBACKLARI =====
        if (data != null && data.startsWith("cand_cat_")) {
            String cat = data.replace("cand_cat_", "");

            if (cat.equals("BACK")) {
                states.put(chatId, EmployerState.SEARCH_CANDIDATES);
                try {
                    EditMessageText edit = new EditMessageText();
                    edit.setChatId(chatId.toString());
                    edit.setMessageId(messageId);
                    edit.setText(lang.equals("ru") ? "🔍 **Поиск кандидатов:**\n\nВыберите категорию:" : "🔍 **Nomzod izlash:**\n\nKategoriyani tanlang:");
                    edit.setParseMode("Markdown");
                    edit.setReplyMarkup((InlineKeyboardMarkup) showCandidateSearch(chatId, lang).getReplyMarkup());
                    telegram.execute(edit);
                } catch (Exception e) {
                    log.error("❌ Edit error", e);
                }
                return null;
            }

            try {
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                SendMessage resp = showCandidatesByCategory(chatId, cat, lang);
                edit.setText(resp.getText());
                edit.setParseMode("Markdown");
                edit.setReplyMarkup((InlineKeyboardMarkup) resp.getReplyMarkup());
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        if (data != null && data.startsWith("cand_view_")) {
            Long profileId = Long.parseLong(data.replace("cand_view_", ""));
            try {
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                SendMessage resp = showCandidateProfile(chatId, profileId, lang);
                edit.setText(resp.getText());
                edit.setParseMode("Markdown");
                edit.setReplyMarkup((InlineKeyboardMarkup) resp.getReplyMarkup());
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        if (data != null && data.startsWith("cand_chat_")) {
            Long partnerId = Long.parseLong(data.replace("cand_chat_", ""));
            states.put(chatId, EmployerState.WAITING_FOR_CHAT_MESSAGE);
            draftData.get(chatId).put("chat_partner_id", String.valueOf(partnerId));

            Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findByUserId(partnerId);
            String partnerName = seekerOpt.map(JobSeekerProfile::getFullName).orElse("Foydalanuvchi");

            return createMessage(chatId,
                    getText(lang, "✍️ **" + partnerName + "ga xabar yozing:**",
                            "✍️ **Напишите сообщение для " + partnerName + ":**"),
                    getChatKeyboard(lang));
        }

        // ===== CHAT OCHISH =====
        if (data != null && data.startsWith("chat_open_")) {
            Long partnerId = Long.parseLong(data.replace("chat_open_", ""));
            try {
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                SendMessage resp = showChatMessages(chatId, partnerId, lang);
                edit.setText(resp.getText());
                edit.setParseMode("Markdown");
                edit.setReplyMarkup((InlineKeyboardMarkup) resp.getReplyMarkup());
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        // ===== KATEGORIYA (VAKANSIYA YARATISH) =====
        if (state == EmployerState.WAITING_FOR_CATEGORY && data != null && data.startsWith("emp_cat_")) {
            String catValue = data.substring("emp_cat_".length());

            if (catValue.equals("OTHER")) {
                states.put(chatId, EmployerState.WAITING_FOR_CUSTOM_CATEGORY);
                return createMessage(chatId,
                        getText(lang, "📝 **Kategoriya nomini kiriting:**", "📝 **Введите название категории:**"),
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

        // ===== VAKANSIYANI ARXIVLASH =====
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
            if (!newStatus) {
                vac.setVacancyStatus(null);
            }
            jobVacancyRepository.save(vac);

            sendAnswerCallback(callbackQuery.getId(),
                    newStatus
                            ? getText(lang, "✅ Faollashtirildi!", "✅ Активирована!")
                            : getText(lang, "🔴 Arxivlandi!", "🔴 Архивирована!"),
                    false);

            try {
                List<JobVacancy> allJobs = jobVacancyRepository.findAllByEmployerChatId(chatId);
                int index = -1;
                for (int i = 0; i < allJobs.size(); i++) {
                    if (allJobs.get(i).getId().equals(vacancyId)) {
                        index = i + 1;
                        break;
                    }
                }
                if (index > 0) {
                    SendMessage updatedCard = buildVacancyCard(chatId, vac, index, lang);
                    EditMessageText edit = new EditMessageText();
                    edit.setChatId(chatId.toString());
                    edit.setMessageId(messageId);
                    edit.setText(updatedCard.getText());
                    edit.setParseMode("Markdown");
                    edit.setReplyMarkup((InlineKeyboardMarkup) updatedCard.getReplyMarkup());
                    telegram.execute(edit);
                }
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        // ===== VAKANSIYANI "TO'LDIRILDI" QILISH =====
        if (data != null && data.startsWith("vac_filled_")) {
            Long vacancyId = Long.parseLong(data.replace("vac_filled_", ""));
            Optional<JobVacancy> vacOpt = jobVacancyRepository.findByIdAndEmployerChatId(vacancyId, chatId);
            if (vacOpt.isEmpty()) {
                sendAnswerCallback(callbackQuery.getId(),
                        getText(lang, "❌ Vakansiya topilmadi!", "❌ Вакансия не найдена!"), true);
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
                            ? getText(lang, "🟢 Faolga qaytarildi!", "🟢 Возвращено в активные!")
                            : getText(lang, "✅ To'ldirildi deb belgilandi!", "✅ Отмечено как заполненное!"),
                    false);

            try {
                List<JobVacancy> allJobs = jobVacancyRepository.findAllByEmployerChatId(chatId);
                int index = -1;
                for (int i = 0; i < allJobs.size(); i++) {
                    if (allJobs.get(i).getId().equals(vacancyId)) {
                        index = i + 1;
                        break;
                    }
                }
                if (index > 0) {
                    SendMessage updatedCard = buildVacancyCard(chatId, vac, index, lang);
                    EditMessageText edit = new EditMessageText();
                    edit.setChatId(chatId.toString());
                    edit.setMessageId(messageId);
                    edit.setText(updatedCard.getText());
                    edit.setParseMode("Markdown");
                    edit.setReplyMarkup((InlineKeyboardMarkup) updatedCard.getReplyMarkup());
                    telegram.execute(edit);
                }
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        // ===== VAKANSIYANI TAHRIRLASH =====
        if (data != null && data.startsWith("vac_edit_") && !data.startsWith("vac_edit_field_")) {
            Long vacancyId = Long.parseLong(data.replace("vac_edit_", ""));
            draftData.get(chatId).put("edit_vacancy_id", String.valueOf(vacancyId));
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang,
                    "✏️ **Vakansiyani tahrirlash:**\n\nQaysi maydonni?",
                    "✏️ **Редактировать вакансию:**\n\nКакое поле?"));
            edit.setParseMode("Markdown");
            edit.setReplyMarkup(buildEditVacancyKeyboard(vacancyId, lang));
            try {
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

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

        // ===== VAKANSIYANI O'CHIRISH =====
        if (data != null && data.startsWith("vac_delete_") && !data.startsWith("vac_delete_confirm_") && !data.startsWith("vac_delete_cancel")) {
            Long vacancyId = Long.parseLong(data.replace("vac_delete_", ""));

            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang,
                    "🗑 **Vakansiyani o'chirishni tasdiqlaysizmi?**",
                    "🗑 **Подтвердите удаление вакансии?**"));
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
                sendAnswerCallback(callbackQuery.getId(), getText(lang, "❌ Topilmadi!", "❌ Не найдено!"), true);
                return null;
            }
            String deletedTitle = vacOpt.get().getTitle();
            jobVacancyRepository.delete(vacOpt.get());

            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(getText(lang,
                    "🗑 **O'chirildi:** " + deletedTitle,
                    "🗑 **Удалено:** " + deletedTitle));
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
            edit.setText(getText(lang, "❌ **Bekor qilindi.**", "❌ **Отменено.**"));
            edit.setParseMode("Markdown");
            try {
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
            }
            return null;
        }

        // ===== ARIZA STATUSI =====
        if (data != null && data.startsWith("emp_app_status_")) {
            String[] parts = data.replace("emp_app_status_", "").split("_");
            String action = parts[0];
            Long appId = Long.parseLong(parts[1]);

            Optional<JobApplication> appOpt = jobApplicationRepository.findById(appId);
            if (appOpt.isEmpty()) {
                sendAnswerCallback(callbackQuery.getId(), getText(lang, "❌ Ariza topilmadi!", "❌ Заявка не найдена!"), true);
                return null;
            }

            JobApplication app = appOpt.get();
            boolean belongsToEmployer = jobVacancyRepository.findByIdAndEmployerChatId(app.getJobId(), chatId).isPresent();
            if (!belongsToEmployer) {
                sendAnswerCallback(callbackQuery.getId(), getText(lang, "❌ Ruxsat yo'q!", "❌ Нет доступа!"), true);
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
                    getText(lang, "✅ Yangilandi: " + newStatus.name(), "✅ Обновлено: " + newStatus.name()),
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
                                "👁 Arizangiz ko'rib chiqildi: «" + jobTitle + "»",
                                "👁 Ваша заявка просмотрена: «" + jobTitle + "»");
                        case INTERVIEW -> notificationText = getText(lang,
                                "📅 Suhbatga chaqirildingiz: «" + jobTitle + "»",
                                "📅 Вас пригласили на собеседование: «" + jobTitle + "»");
                        case ACCEPTED -> notificationText = getText(lang,
                                "🎉 Tabriklaymiz! Ishga qabul qilindingiz: «" + jobTitle + "»",
                                "🎉 Поздравляем! Вы приняты: «" + jobTitle + "»");
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
                log.error("❌ Xabar yuborishda xatolik", e);
            }

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
                log.error("❌ Edit error", e);
            }
            return null;
        }

        // ===== ESKI ACCEPT/REJECT =====
        if (data != null && (data.startsWith("accept_app_") || data.startsWith("reject_app_"))) {
            boolean isAccept = data.startsWith("accept_app_");
            String[] parts = data.split("_");

            String originalText = "";
            if (callbackQuery.getMessage() instanceof org.telegram.telegrambots.meta.api.objects.Message msg) {
                originalText = msg.hasText() ? msg.getText() : "";
            }

            String statusMessage = isAccept ?
                    (lang.equals("ru") ? "✅ **Заявка принята**" : "✅ **Ariza qabul qilindi**") :
                    (lang.equals("ru") ? "❌ **Заявка отклонена**" : "❌ **Ariza rad etildi**");

            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(originalText + "\n\n" + statusMessage);
            edit.setParseMode("Markdown");
            try {
                telegram.execute(edit);
            } catch (Exception e) {
                log.error("❌ Edit error", e);
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
                                    String.format("👤 **Кандидат:**\n\n👤 **ФИО:** %s\n📞 **Тел:** %s",
                                            seeker.getFullName(), seeker.getPhoneNumber()) :
                                    String.format("👤 **Nomzod:**\n\n👤 **F.I.O:** %s\n📞 **Tel:** %s",
                                            seeker.getFullName(), seeker.getPhoneNumber());

                            SendMessage employerInfoMsg = new SendMessage(chatId.toString(), seekerInfo);
                            employerInfoMsg.setParseMode("Markdown");
                            telegram.execute(employerInfoMsg);

                            String employerInfo = String.format("🎉 **Arizangiz qabul qilindi!**\n\n🏢 **Ish beruvchi:** %s\n📞 **Tel:** %s",
                                    employer.getFullName(), employer.getPhoneNumber());

                            SendMessage seekerInfoMsg = new SendMessage(employeeChatId.toString(), employerInfo);
                            seekerInfoMsg.setParseMode("Markdown");
                            telegram.execute(seekerInfoMsg);
                        }
                    } else {
                        String seekerMsgText = (lang.equals("ru") ? "⚠️ Ваша заявка была отклонена." : "⚠️ Arizangiz rad etildi.");
                        SendMessage seekerMsg = new SendMessage(employeeChatId.toString(), seekerMsgText);
                        telegram.execute(seekerMsg);
                    }
                } catch (Exception e) {
                    log.error("❌ Xabar yuborishda xatolik", e);
                }
            }
            return null;
        }

        return null;
    }

    // ============================================
    // ⭐️ 1. DASHBOARD
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
        long viewedApps = allApps.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.VIEWED).count();
        long interviewApps = allApps.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.INTERVIEW).count();
        long acceptedApps = allApps.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.ACCEPTED).count();
        long rejectedApps = allApps.stream().filter(a -> a.getStatus() == JobApplication.ApplicationStatus.REJECTED).count();

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime weekStart = LocalDateTime.now().minusDays(7);

        long todayApps = allApps.stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(todayStart))
                .count();

        long weekApps = allApps.stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(weekStart))
                .count();

        Map<Long, Long> appCountByVacancy = allApps.stream()
                .collect(Collectors.groupingBy(JobApplication::getJobId, Collectors.counting()));

        Optional<Map.Entry<Long, Long>> topVacancy = appCountByVacancy.entrySet().stream()
                .max(Map.Entry.comparingByValue());

        StringBuilder sb = new StringBuilder();

        if (lang.equals("ru")) {
            sb.append("╔════════════════════════════════╗\n");
            sb.append("║   📊 ПАНЕЛЬ РАБОТОДАТЕЛЯ      ║\n");
            sb.append("╚════════════════════════════════╝\n\n");

            sb.append("📋 **ВАКАНСИИ:**\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("   📌 Всего: **").append(totalVacancies).append("**\n");
            if (activeVacancies > 0) sb.append("   🟢 Активных: **").append(activeVacancies).append("**\n");
            if (filledVacancies > 0) sb.append("   🟣 Заполнено: **").append(filledVacancies).append("**\n");
            if (archivedVacancies > 0) sb.append("   🔴 В архиве: **").append(archivedVacancies).append("**\n");
            sb.append("\n");

            if (totalApps > 0) {
                sb.append("📩 **ЗАЯВКИ:**\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("   📊 Всего: **").append(totalApps).append("**\n");
                if (pendingApps > 0) sb.append("   ⏳ Новых: **").append(pendingApps).append("** 🆕\n");
                if (viewedApps > 0) sb.append("   👁 Просмотрено: ").append(viewedApps).append("\n");
                if (interviewApps > 0) sb.append("   📅 Собеседование: ").append(interviewApps).append("\n");
                if (acceptedApps > 0) sb.append("   ✅ Принято: ").append(acceptedApps).append("\n");
                if (rejectedApps > 0) sb.append("   ❌ Отклонено: ").append(rejectedApps).append("\n");
                sb.append("\n");

                sb.append("📅 **АКТИВНОСТЬ:**\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("   🌞 Сегодня: **").append(todayApps).append("** заявок\n");
                sb.append("   📆 За неделю: **").append(weekApps).append("** заявок\n\n");
            } else {
                sb.append("📩 **ЗАЯВКИ:**\n   📭 Пока нет заявок\n\n");
            }

            if (topVacancy.isPresent()) {
                Long topId = topVacancy.get().getKey();
                Long topCount = topVacancy.get().getValue();
                String topTitle = jobVacancyRepository.findById(topId).map(JobVacancy::getTitle).orElse("Вакансия");
                sb.append("🏆 **ТОП ВАКАНСИЯ:**\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("   📌 ").append(topTitle).append("\n");
                sb.append("   📩 ").append(topCount).append(" заявок\n");
            }
        } else {
            sb.append("╔════════════════════════════════╗\n");
            sb.append("║  📊 ISH BERUVCHI PANELI      ║\n");
            sb.append("╚════════════════════════════════╝\n\n");

            sb.append("📋 **VAKANSIYALAR:**\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("   📌 Jami: **").append(totalVacancies).append("**\n");
            if (activeVacancies > 0) sb.append("   🟢 Faol: **").append(activeVacancies).append("**\n");
            if (filledVacancies > 0) sb.append("   🟣 To'ldirildi: **").append(filledVacancies).append("**\n");
            if (archivedVacancies > 0) sb.append("   🔴 Arxivda: **").append(archivedVacancies).append("**\n");
            sb.append("\n");

            if (totalApps > 0) {
                sb.append("📩 **ARIZALAR:**\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("   📊 Jami: **").append(totalApps).append("**\n");
                if (pendingApps > 0) sb.append("   ⏳ Yangi: **").append(pendingApps).append("** 🆕\n");
                if (viewedApps > 0) sb.append("   👁 Ko'rilgan: ").append(viewedApps).append("\n");
                if (interviewApps > 0) sb.append("   📅 Suhbat: ").append(interviewApps).append("\n");
                if (acceptedApps > 0) sb.append("   ✅ Qabul: ").append(acceptedApps).append("\n");
                if (rejectedApps > 0) sb.append("   ❌ Rad etilgan: ").append(rejectedApps).append("\n");
                sb.append("\n");

                sb.append("📅 **FAOLLIK:**\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("   🌞 Bugun: **").append(todayApps).append("** ta ariza\n");
                sb.append("   📆 Hafta: **").append(weekApps).append("** ta ariza\n\n");
            } else {
                sb.append("📩 **ARIZALAR:**\n   📭 Hozircha ariza yo'q\n\n");
            }

            if (topVacancy.isPresent()) {
                Long topId = topVacancy.get().getKey();
                Long topCount = topVacancy.get().getValue();
                String topTitle = jobVacancyRepository.findById(topId).map(JobVacancy::getTitle).orElse("Vakansiya");
                sb.append("🏆 **TOP VAKANSIYA:**\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("   📌 ").append(topTitle).append("\n");
                sb.append("   📩 ").append(topCount).append(" ta ariza\n");
            }
        }

        return createMessage(chatId, sb.toString(), getMainMenuKeyboard(lang));
    }

    // ============================================
    // ⭐️ 2. NOMZODLAR BAZASI
    // ============================================
    private SendMessage showCandidateSearch(Long chatId, String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append(lang.equals("ru") ? "🔍 **Поиск кандидатов:**\n\n" : "🔍 **Nomzod izlash:**\n\n");
        sb.append(lang.equals("ru") ? "Выберите категорию:" : "Kategoriyani tanlang:");

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
        allBtn.setText(lang.equals("ru") ? "🌐 Все кандидаты" : "🌐 Barcha nomzodlar");
        allBtn.setCallbackData("cand_cat_ALL");
        rows.add(List.of(allBtn));

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        return message;
    }

    private SendMessage showCandidatesByCategory(Long chatId, String category, String lang) {
        List<JobSeekerProfile> allCandidates = jobSeekerProfileRepository.findAll();

        List<JobSeekerProfile> filtered;
        if ("ALL".equals(category)) {
            filtered = allCandidates;
        } else {
            filtered = allCandidates.stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }

        if (filtered.isEmpty()) {
            return createMessage(chatId,
                    lang.equals("ru") ? "📭 Кандидаты не найдены." : "📭 Nomzodlar topilmadi.",
                    getMainMenuKeyboard(lang));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(lang.equals("ru")
                ? "👥 **Найдено кандидатов:** " + filtered.size() + "\n\n"
                : "👥 **Topilgan nomzodlar:** " + filtered.size() + "\n\n");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < Math.min(filtered.size(), 10); i++) {
            JobSeekerProfile p = filtered.get(i);

            String name = p.getFullName() != null ? p.getFullName() : "Nomzod";
            String prof = p.getProfession() != null ? p.getProfession() : "Ko'rsatilmagan";
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
        backBtn.setText(lang.equals("ru") ? "⬅️ Назад" : "⬅️ Orqaga");
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
                    lang.equals("ru") ? "❌ Кандидат не найден!" : "❌ Nomzod topilmadi!",
                    getMainMenuKeyboard(lang));
        }

        JobSeekerProfile c = candOpt.get();

        String name = c.getFullName() != null ? c.getFullName() : "Nomzod";
        String phone = c.getPhoneNumber() != null ? c.getPhoneNumber() : "Ko'rsatilmagan";
        String prof = c.getProfession() != null ? c.getProfession() : "Ko'rsatilmagan";
        String exp = c.getExperience() != null ? c.getExperience() : "Ko'rsatilmagan";
        String rating = c.getRating() != null ? String.format("%.1f ⭐", c.getRating()) : "0.0 ⭐";
        String cat = c.getCategory() != null ? c.getCategory() : "Ko'rsatilmagan";

        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════╗\n");
        sb.append("║  👤 **Nomzod profili**\n");
        sb.append("╠══════════════════════════════╣\n");
        sb.append("║ 👤 **").append(name).append("**\n");
        sb.append("║ 📞 ").append(phone).append("\n");
        sb.append("║ 💼 ").append(prof).append("\n");
        sb.append("║ 📁 ").append(cat).append("\n");
        sb.append("║ 📝 ").append(exp.length() > 100 ? exp.substring(0, 100) + "..." : exp).append("\n");
        sb.append("║ ⭐ ").append(rating).append("\n");
        sb.append("╚══════════════════════════════╝");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton chatBtn = new InlineKeyboardButton();
        chatBtn.setText(lang.equals("ru") ? "💬 Написать сообщение" : "💬 Xabar yozish");
        chatBtn.setCallbackData("cand_chat_" + c.getUserId());
        rows.add(List.of(chatBtn));

        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText(lang.equals("ru") ? "⬅️ Назад" : "⬅️ Orqaga");
        backBtn.setCallbackData("cand_cat_ALL");
        rows.add(List.of(backBtn));

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        return message;
    }

    // ============================================
    // ⭐️ 3. CHAT
    // ============================================
    private SendMessage showChatMenu(Long chatId, String lang) {
        List<Long> partners = chatMessageRepository.findAllChatPartners(chatId);

        if (partners.isEmpty()) {
            return createMessage(chatId,
                    lang.equals("ru") ? "💬 **Чатов пока нет.**" : "💬 **Chatlar yo'q.**",
                    getMainMenuKeyboard(lang));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(lang.equals("ru") ? "💬 **Ваши чаты:**\n\n" : "💬 **Sizning chatlaringiz:**\n\n");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < partners.size(); i++) {
            Long partnerId = partners.get(i);

            Optional<JobSeekerProfile> seekerOpt = jobSeekerProfileRepository.findByUserId(partnerId);
            String name = seekerOpt.map(JobSeekerProfile::getFullName).orElse("Foydalanuvchi");

            long unread = chatMessageRepository.countByReceiverChatIdAndSenderChatIdAndIsReadFalse(chatId, partnerId);
            String badge = unread > 0 ? " 🔴 (" + unread + ")" : "";

            sb.append(i + 1).append(". 👤 **").append(name).append("**").append(badge).append("\n");

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
        String partnerName = seekerOpt.map(JobSeekerProfile::getFullName).orElse("Foydalanuvchi");

        StringBuilder sb = new StringBuilder();
        sb.append(getText(lang, "💬 **Chat: " + partnerName + "**\n\n",
                "💬 **Чат: " + partnerName + "**\n\n"));

        if (messages.isEmpty()) {
            sb.append(getText(lang, "(Xabarlar yo'q)\n", "(Сообщений нет)\n"));
        } else {
            int start = Math.max(0, messages.size() - 20);
            for (int i = start; i < messages.size(); i++) {
                ChatMessage m = messages.get(i);
                boolean isMine = m.getSenderChatId().equals(chatId);
                sb.append(isMine ? "👤 **Siz:** " : "👥 **" + partnerName + ":** ")
                        .append(m.getMessageText()).append("\n\n");
            }
        }

        sb.append(getText(lang, "\n✍️ Xabar yozing:", "\n✍️ Напишите сообщение:"));

        states.put(chatId, EmployerState.WAITING_FOR_CHAT_MESSAGE);
        draftData.get(chatId).put("chat_partner_id", String.valueOf(partnerId));

        return createMessage(chatId, sb.toString(), getChatKeyboard(lang));
    }

    private ReplyKeyboardMarkup getChatKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "❌ Bekor qilish", "❌ Отмена")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    // ============================================
    // ⭐️ 4. REYTING
    // ============================================
    private SendMessage showEmployerRating(Long chatId, String lang) {
        Double avgRating = employerReviewRepository.getAverageRating(chatId);
        long reviewCount = employerReviewRepository.countByEmployerChatId(chatId);
        List<EmployerReview> reviews = employerReviewRepository.findByEmployerChatIdOrderByCreatedAtDesc(chatId);

        double rating = avgRating != null ? avgRating : 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append(getText(lang, "⭐ **Sizning reytingingiz**\n\n",
                "⭐ **Ваш рейтинг**\n\n"));

        sb.append("┌─────────────────────────────┐\n");
        sb.append("│ ⭐ **").append(String.format("%.1f", rating)).append(" / 5.0**\n");
        sb.append("│ 📊 Baholar: ").append(reviewCount).append(" ta\n");

        if (reviewCount > 0) {
            long fiveStars = reviews.stream().filter(r -> r.getRating() == 5).count();
            long fourStars = reviews.stream().filter(r -> r.getRating() == 4).count();
            long threeStars = reviews.stream().filter(r -> r.getRating() == 3).count();
            long twoStars = reviews.stream().filter(r -> r.getRating() == 2).count();
            long oneStar = reviews.stream().filter(r -> r.getRating() == 1).count();

            sb.append("│\n");
            sb.append("│ 5️⃣ ").append(fiveStars).append(" ta\n");
            sb.append("│ 4️⃣ ").append(fourStars).append(" ta\n");
            sb.append("│ 3️⃣ ").append(threeStars).append(" ta\n");
            sb.append("│ 2️⃣ ").append(twoStars).append(" ta\n");
            sb.append("│ 1️⃣ ").append(oneStar).append(" ta\n");
        }

        sb.append("└─────────────────────────────┘\n\n");

        if (!reviews.isEmpty()) {
            sb.append(getText(lang, "📝 **Oxirgi baholar:**\n\n",
                    "📝 **Последние отзывы:**\n\n"));

            int count = 0;
            for (EmployerReview r : reviews) {
                if (count >= 5) break;

                String stars = "⭐".repeat(r.getRating());
                String comment = r.getComment() != null && !r.getComment().isBlank()
                        ? r.getComment()
                        : (lang.equals("ru") ? "(без комментария)" : "(izohsiz)");

                sb.append(stars).append(" — ").append(comment).append("\n");
                count++;
            }
        } else {
            sb.append(getText(lang, "📭 Hozircha baholar yo'q.\nNomzodlar ishga qabul qilingandan so'ng sizni baholaydi.",
                    "📭 Пока нет отзывов.\nКандидаты смогут оценить вас после трудоустройства."));
        }

        return createMessage(chatId, sb.toString(), getMainMenuKeyboard(lang));
    }    // ============================================
    // ARIZALAR
    // ============================================
    private SendMessage showApplications(Long chatId, String lang) {
        List<JobApplication> applications = jobApplicationRepository.findAllByEmployerChatId(chatId);

        if (applications == null || applications.isEmpty()) {
            String emptyMsg = lang.equals("ru")
                    ? "📭 У вас пока нет заявок."
                    : "📭 Sizda hozircha arizalar yo'q.";
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
    // RO'YXATDAN O'TISH
    // ============================================
    private SendMessage handleRegistration(Long chatId, String text, Message message, String lang, Optional<EmployerEntity> profileOpt) {
        EmployerState regState = states.getOrDefault(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);

        if (regState == EmployerState.WAITING_FOR_REG_FULL_NAME) {
            if (text.isEmpty() || text.startsWith("/") || text.equals("🏠 Asosiy menyu") || text.equals("🏠 Главное меню")
                    || text.toLowerCase().contains("ish beruvchi") || text.toLowerCase().contains("employer")) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId,
                        getText(lang,
                                "👤 **Ish beruvchi sifatida ro'yxatdan o'tish**\n\nIsm va familiyangizni kiriting:",
                                "👤 **Регистрация работодателя**\n\nВведите имя и фамилию:"));
            }
            if (text.length() < 3) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Ism kamida 3 ta belgidan iborat bo'lishi kerak:", "⚠️ Имя минимум 3 символа:"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("reg_fio", text);
            states.put(chatId, EmployerState.WAITING_FOR_REG_AGE);
            return createMessage(chatId,
                    getText(lang, "🎂 **Yoshingizni kiriting (18+):**", "🎂 **Введите возраст (18+):**"),
                    getCancelKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_AGE) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                return createMessage(chatId, getText(lang, "Ism va familiyangizni kiriting:", "Введите имя и фамилию:"));
            }
            if (!isValidAge(text)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Yosh 18 dan katta va 99 dan kichik bo'lishi kerak:",
                                "⚠️ Возраст от 18 до 99:"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("reg_age", text);
            states.put(chatId, EmployerState.WAITING_FOR_REG_PASSPORT);
            return createMessage(chatId,
                    getText(lang, "🪪 **Pasport seriya va raqamini kiriting:**",
                            "🪪 **Введите серию и номер паспорта:**"),
                    getCancelKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_PASSPORT) {
            if (isCancelText(text)) {
                states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
                draftData.get(chatId).clear();
                return createMessage(chatId, getText(lang, "Ism va familiyangizni kiriting:", "Введите имя и фамилию:"));
            }
            if (!PASSPORT_PATTERN.matcher(text.trim()).matches()) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Pasport formati: 2 ta katta harf + 7 ta raqam (AA1234567):",
                                "⚠️ Формат: 2 заглавные буквы + 7 цифр (AA1234567):"),
                        getCancelKeyboard(lang));
            }
            draftData.get(chatId).put("reg_passport", text);
            states.put(chatId, EmployerState.WAITING_FOR_REG_PHONE);
            return createMessage(chatId,
                    getText(lang, "📞 **Telefon raqamingizni yuboring:**", "📞 **Отправьте номер телефона:**"),
                    getContactKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_PHONE) {
            String phone = message.hasContact() ? message.getContact().getPhoneNumber() : text;
            if (!isValidPhone(phone)) {
                return createMessage(chatId,
                        getText(lang, "⚠️ Format: +998901234567", "⚠️ Формат: +998901234567"),
                        getContactKeyboard(lang));
            }
            draftData.get(chatId).put("reg_phone", phone);
            states.put(chatId, EmployerState.WAITING_FOR_REG_COMPANY);
            return createMessage(chatId,
                    getText(lang,
                            "🏢 **Kompaniya nomini kiriting:**\n\n(Agar yo'q bo'lsa, \"Yo'q\" deb yozing)",
                            "🏢 **Введите название компании:**\n\n(Если нет, напишите \"Нет\")"),
                    getCancelKeyboard(lang));
        }

        if (regState == EmployerState.WAITING_FOR_REG_COMPANY) {
            if (isCancelText(text)) {
                return createMessage(chatId,
                        getText(lang,
                                "⚠️ Iltimos, kompaniya nomini kiriting yoki \"Yo'q\" deb yozing:",
                                "⚠️ Введите название компании или напишите \"Нет\":"),
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
                            "✅ **Ro'yxatdan o'tdingiz!**" +
                                    (companyName != null ? "\n\n🏢 Kompaniya: **" + companyName + "**" : ""),
                            "✅ **Регистрация завершена!**" +
                                    (companyName != null ? "\n\n🏢 Компания: **" + companyName + "**" : "")),
                    getMainMenuKeyboard(lang));
        }

        states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
        return createMessage(chatId,
                getText(lang, "Ism va familiyangizni kiriting:", "Введите имя и фамилию:"));
    }

    // ============================================
    // KOMPANIYA MA'LUMOTLARI
    // ============================================
    private SendMessage showCompanyInfo(Long chatId, Optional<EmployerEntity> profileOpt, String lang) {
        if (profileOpt.isEmpty()) {
            states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
            return createMessage(chatId, getText(lang, "❌ Profil topilmadi.", "❌ Профиль не найден."));
        }

        EmployerEntity e = profileOpt.get();
        String company = e.getCompanyName() != null && !e.getCompanyName().isBlank()
                ? e.getCompanyName() : (lang.equals("ru") ? "Не указано" : "Ko'rsatilmagan");
        String desc = e.getCompanyDescription() != null && !e.getCompanyDescription().isBlank()
                ? e.getCompanyDescription() : (lang.equals("ru") ? "Не указано" : "Ko'rsatilmagan");
        String address = e.getCompanyAddress() != null && !e.getCompanyAddress().isBlank()
                ? e.getCompanyAddress() : (lang.equals("ru") ? "Не указано" : "Ko'rsatilmagan");
        String website = e.getCompanyWebsite() != null && !e.getCompanyWebsite().isBlank()
                ? e.getCompanyWebsite() : (lang.equals("ru") ? "Не указано" : "Ko'rsatilmagan");
        String size = e.getCompanySize() != null && !e.getCompanySize().isBlank()
                ? e.getCompanySize() : (lang.equals("ru") ? "Не указано" : "Ko'rsatilmagan");
        String logo = e.getCompanyLogoFileId() != null ? "✅" : "❌";

        String msg;
        if (lang.equals("ru")) {
            msg = String.format("""
                    🏢 **Информация о компании:**
                    
                    🏷 **Название:** %s
                    📝 **Описание:** %s
                    📍 **Адрес:** %s
                    🌐 **Веб-сайт:** %s
                    👥 **Сотрудников:** %s
                    🖼 **Логотип:** %s
                    """,
                    company, desc, address, website, size, logo);
        } else {
            msg = String.format("""
                    🏢 **Kompaniya ma'lumotlari:**
                    
                    🏷 **Nomi:** %s
                    📝 **Tavsif:** %s
                    📍 **Manzil:** %s
                    🌐 **Veb-sayt:** %s
                    👥 **Xodimlar:** %s
                    🖼 **Logotip:** %s
                    """,
                    company, desc, address, website, size, logo);
        }

        return createMessage(chatId, msg, getCompanyInfoKeyboard(lang));
    }

    // ============================================
    // BLOKLANGAN NOMZODLAR
    // ============================================
    private SendMessage showBlockedCandidates(Long chatId, Optional<EmployerEntity> profileOpt, String lang) {
        if (profileOpt.isEmpty()) {
            states.put(chatId, EmployerState.WAITING_FOR_REG_FULL_NAME);
            return createMessage(chatId, getText(lang, "❌ Profil topilmadi.", "❌ Профиль не найден."));
        }

        String blocked = profileOpt.get().getBlockedCandidateIds();
        String list;
        if (blocked == null || blocked.isBlank()) {
            list = lang.equals("ru") ? "📭 Список пуст." : "📭 Ro'yxat bo'sh.";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(lang.equals("ru") ? "🚫 **Заблокированные кандидаты:**\n\n" : "🚫 **Bloklangan nomzodlar:**\n\n");
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
    // YORDAMCHI METODLAR
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

    private boolean isGlobalButton(String text) {
        return text != null && (
                text.equals("/start") ||
                        text.equals("👤 Profilim") || text.equals("👤 Профиль") ||
                        text.equals("✏️ Profilni tahrirlash") || text.equals("✏️ Редактировать профиль") ||
                        text.equals("🏢 Kompaniya") || text.equals("🏢 Компания") ||
                        text.equals("⚙️ Sozlamalar") || text.equals("⚙️ Настройки") ||
                        text.equals("📢 Vakansiya yaratish") || text.equals("📢 Создать вакансию") ||
                        text.equals("📋 Mening vakansiyalarim") || text.equals("📋 Мои вакансии") ||
                        text.equals("📩 Arizalar") || text.equals("📩 Заявки") ||
                        text.equals("📊 Dashboard") || text.equals("📊 Дашборд") ||
                        text.equals("💬 Chatlar") || text.equals("💬 Чаты") ||
                        text.equals("🔍 Nomzod izlash") || text.equals("🔍 Поиск кандидатов") ||
                        text.equals("⭐ Reytingim") || text.equals("⭐ Мой рейтинг") ||
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
                List.of(getText(lang, "📊 Dashboard", "📊 Дашборд")),
                List.of(getText(lang, "📢 Vakansiya yaratish", "📢 Создать вакансию")),
                List.of(getText(lang, "📋 Mening vakansiyalarim", "📋 Мои вакансии")),
                List.of(getText(lang, "📩 Arizalar", "📩 Заявки"), getText(lang, "💬 Chatlar", "💬 Чаты")),
                List.of(getText(lang, "🔍 Nomzod izlash", "🔍 Поиск кандидатов")),
                List.of(getText(lang, "👤 Profilim", "👤 Профиль"), getText(lang, "🏢 Kompaniya", "🏢 Компания")),
                List.of(getText(lang, "⭐ Reytingim", "⭐ Мой рейтинг"), getText(lang, "⚙️ Sozlamalar", "⚙️ Настройки")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    private ReplyKeyboardMarkup getProfileKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "✏️ Profilni tahrirlash", "✏️ Редактировать профиль")),
                List.of(getText(lang, "🏢 Kompaniya", "🏢 Компания")),
                List.of(getText(lang, "🏠 Asosiy menyu", "🏠 Главное меню"))
        ));
    }

    private ReplyKeyboardMarkup getEditProfileKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "👤 Ismni tahrirlash", "👤 Изменить имя")),
                List.of(getText(lang, "🎂 Yoshni tahrirlash", "🎂 Изменить возраст")),
                List.of(getText(lang, "🏢 Kompaniya nomini tahrirlash", "🏢 Изменить название компании")),
                List.of(getText(lang, "⬅️ Orqaga", "⬅️ Назад"))
        ));
    }

    private ReplyKeyboardMarkup getCompanyInfoKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "📝 Kompaniya tavsifi", "📝 Описание компании")),
                List.of(getText(lang, "📍 Manzil", "📍 Адрес")),
                List.of(getText(lang, "🌐 Veb-sayt", "🌐 Веб-сайт")),
                List.of(getText(lang, "👥 Xodimlar soni", "👥 Количество сотрудников")),
                List.of(getText(lang, "🖼 Logotip", "🖼 Логотип")),
                List.of(getText(lang, "⬅️ Orqaga", "⬅️ Назад"))
        ));
    }

    private ReplyKeyboardMarkup getCompanySizeKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of("1-10", "11-50"),
                List.of("51-200", "200+"),
                List.of(getText(lang, "❌ Bekor qilish", "❌ Отмена"))
        ));
    }

    private ReplyKeyboardMarkup getBlockedCandidatesKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "➕ Nomzodni bloklash", "➕ Заблокировать кандидата")),
                List.of(getText(lang, "✅ Blokdan chiqarish", "✅ Разблокировать")),
                List.of(getText(lang, "⬅️ Orqaga", "⬅️ Назад"))
        ));
    }

    private ReplyKeyboardMarkup getSettingsKeyboard(String lang) {
        return createReplyKeyboard(List.of(
                List.of(getText(lang, "🌐 Til", "🌐 Язык")),
                List.of(getText(lang, "🚫 Bloklangan nomzodlar", "🚫 Заблокированные кандидаты")),
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
        otherBtn.setText(lang.equals("ru") ? "➕ Другое" : "➕ Boshqa");
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
    // ⭐️ HAR BIR VAKANSIYA UCHUN ALOHIDA XABAR
    // ============================================
    private SendMessage showMyVacanciesWithButtons(Long chatId, String lang) {
        List<JobVacancy> myJobs = jobVacancyRepository.findAllByEmployerChatId(chatId);
        if (myJobs.isEmpty()) {
            return createMessage(chatId,
                    lang.equals("ru") ? "📭 У вас нет вакансий." : "📭 Sizda vakansiyalar yo'q.",
                    getMainMenuKeyboard(lang));
        }

        SendMessage header = new SendMessage();
        header.setChatId(chatId.toString());
        header.setParseMode("Markdown");
        header.setText(lang.equals("ru")
                ? "📋 **Ваши вакансии:**\n\nJami: *" + myJobs.size() + " ta*"
                : "📋 **Sizning vakansiyalaringiz:**\n\nJami: *" + myJobs.size() + " ta*");
        try {
            telegram.execute(header);
        } catch (Exception e) {
            log.error("❌ Header yuborishda xatolik", e);
        }

        for (int i = 0; i < myJobs.size(); i++) {
            JobVacancy job = myJobs.get(i);
            SendMessage card = buildVacancyCard(chatId, job, i + 1, lang);
            try {
                telegram.execute(card);
            } catch (Exception e) {
                log.error("❌ Karta yuborishda xatolik", e);
            }
        }

        return null;
    }

    private SendMessage buildVacancyCard(Long chatId, JobVacancy job, int index, String lang) {
        boolean isActive = job.getIsActive() == null || job.getIsActive();
        boolean isFilled = "FILLED".equals(job.getVacancyStatus());

        String statusEmoji;
        String statusText;
        if (isFilled) {
            statusEmoji = "🟣";
            statusText = lang.equals("ru") ? "Заполнено" : "To'ldirildi";
        } else if (isActive) {
            statusEmoji = "🟢";
            statusText = lang.equals("ru") ? "Активна" : "Faol";
        } else {
            statusEmoji = "🔴";
            statusText = lang.equals("ru") ? "В архиве" : "Arxivda";
        }

        String jobTitle = job.getTitle() != null ? job.getTitle() : "Nomsiz";
        String salary = job.getSalary() != null ? job.getSalary() : "Kelishiladi";

        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════╗\n");
        sb.append("║ ").append(index).append("️⃣ **").append(jobTitle).append("**\n");
        sb.append("╠══════════════════════════╣\n");
        sb.append("║ 💰 **").append(salary).append("**\n");
        sb.append("║ ").append(statusEmoji).append(" ").append(statusText).append("\n");

        if (job.getCategory() != null && !job.getCategory().isBlank()) {
            sb.append("║ 📁 ").append(job.getCategory()).append("\n");
        }
        if (job.getType() != null && !job.getType().isBlank()) {
            sb.append("║ ⏰ ").append(job.getType()).append("\n");
        }
        if (job.getWorkHours() != null && !job.getWorkHours().isBlank()) {
            sb.append("║ ⌛ ").append(job.getWorkHours()).append("\n");
        }
        sb.append("╚══════════════════════════╝");

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();

        InlineKeyboardButton editBtn = new InlineKeyboardButton();
        editBtn.setText(lang.equals("ru") ? "✏️ Изменить" : "✏️ Tahrirlash");
        editBtn.setCallbackData("vac_edit_" + job.getId());
        row1.add(editBtn);

        InlineKeyboardButton toggleBtn = new InlineKeyboardButton();
        toggleBtn.setText(isActive
                ? (lang.equals("ru") ? "🔴 В архив" : "🔴 Arxivlash")
                : (lang.equals("ru") ? "🟢 Активировать" : "🟢 Faollashtirish"));
        toggleBtn.setCallbackData("vac_toggle_" + job.getId());
        row1.add(toggleBtn);

        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();

        InlineKeyboardButton filledBtn = new InlineKeyboardButton();
        filledBtn.setText(isFilled
                ? (lang.equals("ru") ? "🟢 Вернуть" : "🟢 Faolga")
                : (lang.equals("ru") ? "✅ Заполнено" : "✅ To'ldirildi"));
        filledBtn.setCallbackData("vac_filled_" + job.getId());
        row2.add(filledBtn);

        InlineKeyboardButton deleteBtn = new InlineKeyboardButton();
        deleteBtn.setText(lang.equals("ru") ? "🗑 Удалить" : "🗑 O'chirish");
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
        hoursBtn.setText(lang.equals("ru") ? "⏰ Часы работы" : "⏰ Ish vaqti");
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
    // VAKANSIYA TAHRIRLASH
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
                    getText(lang, "Ism va familiyangizni kiriting:", "Введите имя и фамилию:"),
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
                String.format("🔔 **Новый отклик!**\n\n📌 **Вакансия:** %s\n👤 **Кандидат:** %s\n📞 **Тел:** %s",
                        vacancyTitle, candidateName, candidatePhone) :
                String.format("🔔 **Yangi ariza!**\n\n📌 **Vakansiya:** %s\n👤 **Nomzod:** %s\n📞 **Tel:** %s",
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