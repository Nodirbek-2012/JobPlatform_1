package uz.imaan.jobplatform.telegram.JobSeekerHandler.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.KeyboardService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor

public class KeyboardServiceImpl implements KeyboardService {

    // ============================================
    // YORDAMCHI METODLAR
    // ============================================
    @Override
    public String getText(Optional<JobSeekerProfile> profileOpt, String ru, String uz, String en) {
        if (profileOpt.isPresent()) {
            String lang = profileOpt.get().getLanguage();
            if ("ru".equals(lang)) return ru;
            if ("en".equals(lang)) return en;
            return uz;
        }
        return uz;
    }

    private boolean isRussian(Optional<JobSeekerProfile> profileOpt) {
        return profileOpt.isPresent() && "ru".equals(profileOpt.get().getLanguage());
    }

    private boolean isEnglish(Optional<JobSeekerProfile> profileOpt) {
        return profileOpt.isPresent() && "en".equals(profileOpt.get().getLanguage());
    }

    // ============================================
    // MAIN MENU TEXT
    // ============================================
    @Override
    public String getMainMenuText(Optional<JobSeekerProfile> profileOpt) {
        if (isRussian(profileOpt)) return "🛠 **Меню работника**\n\nВыберите нужный раздел:";
        if (isEnglish(profileOpt)) return "🛠 **Worker menu**\n\nSelect the section you need:";
        return "🛠 **Ishchi menyusi**\n\nKerakli bo'limni tanlang:";
    }
    // ============================================
    // INLINE KEYBOARD – KATEGORIYALAR
    // ============================================
    @Override
    public InlineKeyboardMarkup getCategoryInlineKeyboard(Optional<JobSeekerProfile> profileOpt) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String it, design, construction, driver, education, trade, cleaner, cook, security, courier, all;

        if (isRussian(profileOpt)) {
            it = "💻 IT & Программирование";
            design = "🎨 Дизайн";
            construction = "🏗️ Строительство";
            driver = "🚗 Водитель / Курьер";
            education = "📚 Образование / Репетитор";
            trade = "🛒 Продавец";
            cleaner = "🧹 Уборщик";
            cook = "👨‍🍳 Повар";
            security = "🔒 Охрана";
            courier = "📦 Доставка";
            all = "🌐 Все вакансии";
        } else if (isEnglish(profileOpt)) {
            it = "💻 IT & Programming";
            design = "🎨 Design";
            construction = "🏗️ Construction";
            driver = "🚗 Driver / Courier";
            education = "📚 Education / Tutor";
            trade = "🛒 Sales / Seller";
            cleaner = "🧹 Cleaner";
            cook = "👨‍🍳 Cook / Chef";
            security = "🔒 Security";
            courier = "📦 Delivery / Courier";
            all = "🌐 All vacancies";
        } else {
            it = "💻 IT & Dasturlash";
            design = "🎨 Dizayn";
            construction = "🏗️ Qurilish";
            driver = "🚗 Haydovchi / Kuryer";
            education = "📚 Ta'lim / Repetitor";
            trade = "🛒 Savdo / Sotuvchi";
            cleaner = "🧹 Farrosh / Tozalash";
            cook = "👨‍🍳 Pazanda / Oshpaz";
            security = "🔒 Qorovul / Xavfsizlik";
            courier = "📦 Kuryer / Yetkazib berish";
            all = "🌐 Barcha vakansiyalar";
        }

        // Qator 1
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder().text(it).callbackData("category_it").build());
        row1.add(InlineKeyboardButton.builder().text(design).callbackData("category_design").build());
        rows.add(row1);

        // Qator 2
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder().text(construction).callbackData("category_construction").build());
        row2.add(InlineKeyboardButton.builder().text(driver).callbackData("category_driver").build());
        rows.add(row2);

        // Qator 3
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(InlineKeyboardButton.builder().text(education).callbackData("category_education").build());
        row3.add(InlineKeyboardButton.builder().text(trade).callbackData("category_trade").build());
        rows.add(row3);

        // Qator 4
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(InlineKeyboardButton.builder().text(cleaner).callbackData("category_cleaner").build());
        row4.add(InlineKeyboardButton.builder().text(cook).callbackData("category_cook").build());
        rows.add(row4);

        // Qator 5
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        row5.add(InlineKeyboardButton.builder().text(security).callbackData("category_security").build());
        row5.add(InlineKeyboardButton.builder().text(courier).callbackData("category_courier").build());
        rows.add(row5);

        // Qator 6
        List<InlineKeyboardButton> row6 = new ArrayList<>();
        row6.add(InlineKeyboardButton.builder().text(all).callbackData("category_all").build());
        rows.add(row6);

        markup.setKeyboard(rows);
        return markup;
    }

    // ============================================
    // ASOSIY MENYU
    // ============================================
    @Override
    public ReplyKeyboardMarkup getMainMenuKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();
        KeyboardRow row3 = new KeyboardRow();
        KeyboardRow row4 = new KeyboardRow();

        if (isRussian(profileOpt)) {
            row1.add("🔍 Поиск работы");
            row1.add("⚡ Мои активные работы");
            row2.add("👤 Мой профиль");
            row2.add("📂 Мои заявки");
            row3.add("💳 Кошелек");
            row3.add("⚙️ Настройки");
            row4.add("🏠 Главное меню");
        } else if (isEnglish(profileOpt)) {
            row1.add("🔍 Job search");
            row1.add("⚡ My active jobs");
            row2.add("👤 My profile");
            row2.add("📂 My applications");
            row3.add("💳 Wallet");
            row3.add("⚙️ Settings");
            row4.add("🏠 Main menu");
        } else {
            row1.add("🔍 Ish qidirish");
            row1.add("⚡ Faol ishlarim");
            row2.add("👤 Profilim");
            row2.add("📂 Arizalar");
            row3.add("💳 Hamyon");
            row3.add("⚙️ Sozlamalar");
            row4.add("Asosiy menyu");
        }

        markup.setKeyboard(List.of(row1, row2, row3, row4));
        return markup;
    }

    // ============================================
    // ISH TURI TANLASH
    // ============================================
    @Override
    public ReplyKeyboardMarkup getJobTypeKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();

        if (isRussian(profileOpt)) {
            row1.add("👨‍💻 По профессии");
            row1.add("🔧 Обычный рабочий");
            row2.add("⬅️ Назад");
        } else if (isEnglish(profileOpt)) {
            row1.add("👨‍💻 By profession");
            row1.add("🔧 Ordinary worker");
            row2.add("⬅️ Back");
        } else {
            row1.add("👨‍💻 Kasbim bo'yicha");
            row1.add("🔧 Oddiy ishchi");
            row2.add("⬅️ Orqaga");
        }

        markup.setKeyboard(List.of(row1, row2));
        return markup;
    }

    // ============================================
    // RO'YXATDAN O'TISH KATEGORIYALARI
    // ============================================
    @Override
    public ReplyKeyboardMarkup getRegistrationCategoryKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();
        KeyboardRow row3 = new KeyboardRow();
        KeyboardRow row4 = new KeyboardRow();
        KeyboardRow row5 = new KeyboardRow();

        String it, design, construction, driver, education, trade, cleaner, cook, security, courier, back;

        if (isRussian(profileOpt)) {
            it = "💻 IT & Программирование";
            design = "🎨 Дизайн";
            construction = "🏗️ Строительство";
            driver = "🚗 Водитель / Курьер";
            education = "📚 Образование / Репетитор";
            trade = "🛒 Продавец";
            cleaner = "🧹 Уборщик";
            cook = "👨‍🍳 Повар";
            security = "🔒 Охрана";
            courier = "📦 Доставка";
            back = "⬅️ Назад";
        } else if (isEnglish(profileOpt)) {
            it = "💻 IT & Programming";
            design = "🎨 Design";
            construction = "🏗️ Construction";
            driver = "🚗 Driver / Courier";
            education = "📚 Education / Tutor";
            trade = "🛒 Sales / Seller";
            cleaner = "🧹 Cleaner";
            cook = "👨‍🍳 Cook / Chef";
            security = "🔒 Security";
            courier = "📦 Delivery / Courier";
            back = "⬅️ Back";
        } else {
            it = "💻 IT & Dasturlash";
            design = "🎨 Dizayn";
            construction = "🏗️ Qurilish";
            driver = "🚗 Haydovchi / Kuryer";
            education = "📚 Ta'lim / Repetitor";
            trade = "🛒 Savdo / Sotuvchi";
            cleaner = "🧹 Farrosh / Tozalash";
            cook = "👨‍🍳 Pazanda / Oshpaz";
            security = "🔒 Qorovul / Xavfsizlik";
            courier = "📦 Kuryer / Yetkazib berish";
            back = "⬅️ Orqaga";
        }

        row1.add(it);
        row1.add(design);
        row2.add(construction);
        row2.add(driver);
        row3.add(education);
        row3.add(trade);
        row4.add(cleaner);
        row4.add(cook);
        row5.add(security);
        row5.add(courier);

        KeyboardRow row6 = new KeyboardRow();
        row6.add(back);

        markup.setKeyboard(List.of(row1, row2, row3, row4, row5, row6));
        return markup;
    }

    // ============================================
    // QIDIRUV KATEGORIYALARI
    // ============================================
    @Override
    public ReplyKeyboardMarkup getSearchCategoryKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();
        KeyboardRow row3 = new KeyboardRow();
        KeyboardRow row4 = new KeyboardRow();
        KeyboardRow row5 = new KeyboardRow();
        KeyboardRow row6 = new KeyboardRow();

        String it, design, construction, driver, education, trade, cleaner, cook, security, courier, all, back;

        if (isRussian(profileOpt)) {
            it = "💻 IT & Программирование";
            design = "🎨 Дизайн";
            construction = "🏗️ Строительство";
            driver = "🚗 Водитель / Курьер";
            education = "📚 Образование / Репетитор";
            trade = "🛒 Продавец";
            cleaner = "🧹 Уборщик";
            cook = "👨‍🍳 Повар";
            security = "🔒 Охрана";
            courier = "📦 Доставка";
            all = "🌐 Все вакансии";
            back = "⬅️ Назад";
        } else if (isEnglish(profileOpt)) {
            it = "💻 IT & Programming";
            design = "🎨 Design";
            construction = "🏗️ Construction";
            driver = "🚗 Driver / Courier";
            education = "📚 Education / Tutor";
            trade = "🛒 Sales / Seller";
            cleaner = "🧹 Cleaner";
            cook = "👨‍🍳 Cook / Chef";
            security = "🔒 Security";
            courier = "📦 Delivery / Courier";
            all = "🌐 All vacancies";
            back = "⬅️ Back";
        } else {
            it = "💻 IT & Dasturlash";
            design = "🎨 Dizayn";
            construction = "🏗️ Qurilish";
            driver = "🚗 Haydovchi / Kuryer";
            education = "📚 Ta'lim / Repetitor";
            trade = "🛒 Savdo / Sotuvchi";
            cleaner = "🧹 Farrosh / Tozalash";
            cook = "👨‍🍳 Pazanda / Oshpaz";
            security = "🔒 Qorovul / Xavfsizlik";
            courier = "📦 Kuryer / Yetkazib berish";
            all = "🌐 Barcha vakansiyalar";
            back = "⬅️ Orqaga";
        }

        row1.add(it);
        row1.add(design);
        row2.add(construction);
        row2.add(driver);
        row3.add(education);
        row3.add(trade);
        row4.add(cleaner);
        row4.add(cook);
        row5.add(security);
        row5.add(courier);
        row6.add(all);

        KeyboardRow row7 = new KeyboardRow();
        row7.add(back);

        markup.setKeyboard(List.of(row1, row2, row3, row4, row5, row6, row7));
        return markup;
    }

    // ============================================
    // PROFIL MENYUSI
    // ============================================
    @Override
    public ReplyKeyboardMarkup getProfileKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();
        KeyboardRow row3 = new KeyboardRow();

        if (isRussian(profileOpt)) {
            row1.add("📊 Информация");
            row1.add("📁 Портфолио");
            row1.add("⭐ Рейтинг");
            row2.add("🖼 Фото");
            row2.add("💼 Профессия");
            row2.add("✏️ Редактировать");
            row3.add("⬅️ Назад");
        } else if (isEnglish(profileOpt)) {
            row1.add("📊 Information");
            row1.add("📁 Portfolio");
            row1.add("⭐ Rating");
            row2.add("🖼 Photo");
            row2.add("💼 Profession");
            row2.add("✏️ Edit");
            row3.add("⬅️ Back");
        } else {
            row1.add("📊 Ma'lumotlar");
            row1.add("📁 Portfolio");
            row1.add("⭐ Reyting");
            row2.add("🖼 Rasm");
            row2.add("💼 Kasb");
            row2.add("✏️ Tahrirlash");
            row3.add("⬅️ Orqaga");
        }

        markup.setKeyboard(List.of(row1, row2, row3));
        return markup;
    }

    // ============================================
    // HAMYON MENYUSI
    // ============================================
    @Override
    public ReplyKeyboardMarkup getWalletKeyboard(Optional<JobSeekerProfile> profileOpt) {
        boolean hasCard = profileOpt.map(p -> p.getBankCards() != null && !p.getBankCards().isEmpty()).orElse(false);
        return getWalletKeyboard(profileOpt, hasCard);
    }

    @Override
    public ReplyKeyboardMarkup getWalletKeyboard(Optional<JobSeekerProfile> profileOpt, boolean hasCard) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();
        KeyboardRow row3 = new KeyboardRow();

        if (isRussian(profileOpt)) {
            if (!hasCard) row1.add("💳 Добавить банковскую карту");
            else row1.add("🗑️ Удалить карту");
            row1.add("💰 Баланс");
            row2.add("💳 Способы оплаты");
            row2.add("📜 История платежей");
            row3.add("💸 Снять деньги");
            row3.add("⬅️ Назад");
        } else if (isEnglish(profileOpt)) {
            if (!hasCard) row1.add("💳 Add bank card");
            else row1.add("🗑️ Delete card");
            row1.add("💰 Balance");
            row2.add("💳 Payment methods");
            row2.add("📜 Payment history");
            row3.add("💸 Withdraw");
            row3.add("⬅️ Back");
        } else {
            if (!hasCard) row1.add("💳 Bank kartasi qo'shish");
            else row1.add("🗑️ Kartani o'chirish");
            row1.add("💰 Hisob balansi");
            row2.add("💳 To'lov usullari");
            row2.add("📜 To'lov tarixi");
            row3.add("💸 Pul yechish");
            row3.add("⬅️ Orqaga");
        }

        markup.setKeyboard(List.of(row1, row2, row3));
        return markup;
    }

    // ============================================
    // SOZLAMALAR MENYUSI
    // ============================================
    @Override
    public ReplyKeyboardMarkup getSettingsKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();
        KeyboardRow row3 = new KeyboardRow();

        if (isRussian(profileOpt)) {
            row1.add("🌐 Язык");
            row1.add("🔒 Конфиденциальность");
            row2.add("🔔 Уведомления");
            row2.add("❓ Помощь");
            row3.add("⬅️ Назад");
        } else if (isEnglish(profileOpt)) {
            row1.add("🌐 Language");
            row1.add("🔒 Privacy");
            row2.add("🔔 Notifications");
            row2.add("❓ Help");
            row3.add("⬅️ Back");
        } else {
            row1.add("🌐 Til");
            row1.add("🔒 Maxfiylik");
            row2.add("🔔 Bildirishnoma");
            row2.add("❓ Yordam");
            row3.add("⬅️ Orqaga");
        }

        markup.setKeyboard(List.of(row1, row2, row3));
        return markup;
    }

    // ============================================
    // TIL TANLASH
    // ============================================
    @Override
    public ReplyKeyboardMarkup getLanguageKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        row1.add("🇺🇿 O'zbek");
        row1.add("🇷🇺 Русский");
        row1.add("🇬🇧 English");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("⬅️ Orqaga");

        markup.setKeyboard(List.of(row1, row2));
        return markup;
    }

    // ============================================
    // FAOL ISHLAR
    // ============================================
    @Override
    public ReplyKeyboardMarkup getActiveJobsKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();

        if (isRussian(profileOpt)) {
            row1.add("📌 Текущие работы");
            row1.add("📋 Задания");
            row1.add("📝 Задачи");
            row2.add("⬅️ Назад");
        } else if (isEnglish(profileOpt)) {
            row1.add("📌 Current jobs");
            row1.add("📋 Tasks");
            row1.add("📝 Assignments");
            row2.add("⬅️ Back");
        } else {
            row1.add("📌 Joriy ishlar");
            row1.add("📋 Topshiriqlar");
            row1.add("📝 Vazifalar");
            row2.add("⬅️ Orqaga");
        }

        markup.setKeyboard(List.of(row1, row2));
        return markup;
    }

    // ============================================
    // ARIZA TUGMALARI
    // ============================================
    @Override
    public ReplyKeyboardMarkup getJobActionKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();

        if (isRussian(profileOpt)) {
            row1.add("📝 Подать заявку");
            row2.add("⬅️ Назад");
        } else if (isEnglish(profileOpt)) {
            row1.add("📝 Submit application");
            row2.add("⬅️ Back");
        } else {
            row1.add("📝 Ariza berish");
            row2.add("⬅️ Orqaga");
        }

        markup.setKeyboard(List.of(row1, row2));
        return markup;
    }

    // ============================================
    // BEKOR QILISH
    // ============================================
    @Override
    public ReplyKeyboardMarkup getCancelKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        if (isRussian(profileOpt)) row.add("❌ Отмена");
        else if (isEnglish(profileOpt)) row.add("❌ Cancel");
        else row.add("❌ Bekor qilish");

        markup.setKeyboard(List.of(row));
        return markup;
    }

    // ============================================
    // ORQAGA
    // ============================================
    @Override
    public ReplyKeyboardMarkup getSubBackKeyboard(Optional<JobSeekerProfile> profileOpt) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        if (isRussian(profileOpt)) row.add("⬅️ Назад");
        else if (isEnglish(profileOpt)) row.add("⬅️ Back");
        else row.add("⬅️ Orqaga");

        markup.setKeyboard(List.of(row));
        return markup;
    }

    // ============================================
    // TELEFON RAQAM
    // ============================================
    @Override
    public ReplyKeyboardMarkup getPhoneKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        KeyboardButton button = new KeyboardButton("📱 Telefon raqamni yuborish");
        button.setRequestContact(true);
        row.add(button);

        markup.setKeyboard(List.of(row));
        return markup;
    }
}
