package uz.imaan.jobplatform.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.imaan.jobplatform.admin.dto.AdminDtoTwo;
import uz.imaan.jobplatform.admin.service.AdminService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelegramBotService extends TelegramLongPollingBot {

    private final Map<Long, String> userLanguages = new ConcurrentHashMap<>();
    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, AdminDtoTwo> pendingAdmins = new ConcurrentHashMap<>();

    private String botUsername = "employment_chirchik_bot";
    private long lastUpdateId = 0;

    // Direct injection orqali AOP Proxy bilan bog'liq siklik bog'lanish va Lazy muammosini oldini olamiz
    @Autowired
    @Lazy
    private AdminService adminService;

    public TelegramBotService() {
        super("8757778609:AAEtyutp-PvYDx8DbtcIYSQmw7W4hU2GryI");
        this.botUsername = "employment_chirchik_bot";
    }

    private String getLanguage(long chatId) {
        return userLanguages.getOrDefault(chatId, "RU");
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message = update.getMessage();

                if (lastUpdateId >= update.getUpdateId()) {
                    return;
                }
                lastUpdateId = update.getUpdateId();

                handleTextMessage(message);
                return;
            }

            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleTextMessage(Message message) {
        String text = message.getText();
        long chatId = message.getChatId();

        if (text == null) return;

        if (text.startsWith("/addadmin")) {
            handleAddAdminCommand(text, chatId);
            return;
        }

        if (text.startsWith("/")) {
            if ("/start".equals(text)) {
                if (!userLanguages.containsKey(chatId)) {
                    userLanguages.put(chatId, "RU");
                }
                sendMessage(chatId, "Привет! Бот поиска почасовой работы JobPlatform запущен.");
                return;
            }

            if ("/admin".equals(text)) {
                if (!adminService.isAdmin(chatId)) {
                    sendMessage(chatId, "У вас нет прав администратора.");
                    return;
                }
                sendAdminMenu(chatId);
                return;
            }

            if (text.startsWith("/block") || text.startsWith("/unblock")) {
                handleAdminCommand(text, chatId);
                return;
            }

            if (text.startsWith("/info")) {
                handleInfoCommand(chatId, text);
                return;
            }
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            execute(answer);
        } catch (Exception ignored) {}

        if ("change_language".equals(data)) {
            sendLanguageMenu(chatId);
        } else if ("lang_ru".equals(data)) {
            userLanguages.put(chatId, "RU");
            sendMessage(chatId, "✅ Язык успешно изменён на **Русский**!");
            sendAdminMenu(chatId);
        } else if ("lang_uz".equals(data)) {
            userLanguages.put(chatId, "UZ");
            sendMessage(chatId, "✅ Til **O'zbekcha**ga muvaffaqiyatli o'zgartirildi!");
            sendAdminMenu(chatId);
        } else if ("admin_stats".equals(data) || "stats".equals(data)) {
            AdminDtoTwo stats = adminService.getStats();

            long admins = stats.totalAdmins() != null ? stats.totalAdmins() : 0;
            long employers = stats.totalEmployers() != null ? stats.totalEmployers() : 0;
            long workers = stats.totalWorkers() != null ? stats.totalWorkers() : 0;

            String statsText = String.format(
                    "📊 *Статистика платформы:*\n\n👥 Админов: `%d`\n🏢 Работодателей: `%d`\n👤 Исполнителей: `%d`",
                    admins, employers, workers
            );

            sendMessage(chatId, statsText);
        } else if ("admin_workers".equals(data) || "workers".equals(data)) {
            sendMessage(chatId, adminService.getAllWorkersList());
        } else if ("admin_employers".equals(data) || "employers".equals(data)) {
            sendMessage(chatId, adminService.getFormattedEmployersList());
        } else if ("admin_refresh".equals(data)) {
            sendAdminMenu(chatId);
        } else if ("admin_find_by_tg_id".equals(data)) {
            String text = "UZ".equals(getLanguage(chatId))
                    ? "🔍 Foydalanuvchi haqida to'liq ma'lumot olish uchun buyruqni yuboring:\n\n`/info 123456789`"
                    : "🔍 Чтобы получить полную информацию о пользователе, отправьте команду:\n\n`/info 123456789`";
            sendMessage(chatId, text);
        }
    }

    public void handleAdminCommand(String text, Long adminChatId) {
        if (text.startsWith("/block")) {
            String[] parts = text.split(" ", 3);

            if (parts.length < 3) {
                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("⚠️ Формат команды: /block <ID_пользователя> <Причина>")
                        .build());
                return;
            }

            try {
                Long targetUserId = Long.parseLong(parts[1]);
                String reason = parts[2];

                AdminDtoTwo blockDTO = new AdminDtoTwo(
                        null, null, null, null, null, null, null, null, null,
                        0L, null, null, null, targetUserId, reason, null, null, null, 0L, 0L
                );

                blockUser(blockDTO);

                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("✅ Пользователь " + targetUserId + " успешно заблокирован.")
                        .build());

            } catch (NumberFormatException e) {
                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("❌ Неверный ID пользователя. ID должен состоять только из цифр.")
                        .build());
            }
        } else if (text.startsWith("/unblock")) {
            String[] parts = text.split(" ", 2);

            if (parts.length < 2) {
                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("⚠️ Формат команды: /unblock <ID_пользователя>")
                        .build());
                return;
            }

            try {
                Long targetUserId = Long.parseLong(parts[1]);

                unblockUser(targetUserId);

                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("✅ Пользователь " + targetUserId + " разблокирован.")
                        .build());

            } catch (NumberFormatException e) {
                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("❌ Неверный ID пользователя.")
                        .build());
            }
        }
    }

    public void sendAdminMenu(long chatId) {
        AdminDtoTwo stats = adminService.getDetailedStats();

        long admins = stats.totalAdmins() != null ? stats.totalAdmins() : 0;
        long employers = stats.totalEmployers() != null ? stats.totalEmployers() : 0;
        long workers = stats.totalWorkers() != null ? stats.totalWorkers() : 0;
        long activeJobs = stats.activeJobs() != null ? stats.activeJobs() : 0;
        long completedShifts = stats.completedShifts();
        String lang = getLanguage(chatId);

        String menuText = "UZ".equals(lang) ?
                String.format(
                        "⚙️ *JobPlatform Administrator Paneli*\n\n" +
                                "📊 *Batafsil platforma statistikasi:*\n" +
                                " ├ 👥 Adminlar: `%d`\n" +
                                " ├ 🏢 Ish beruvchilar: `%d`\n" +
                                " ├ 👤 Ishchilar (Izlovchilar): `%d`\n" +
                                " ├ 💼 Faol vakansiyalar: `%d`\n" +
                                " └ ✅ Yakunlangan smenalar: `%d`\n\n" +
                                "Harakatni tanlang:",
                        admins, employers, workers, activeJobs, completedShifts
                ) :
                String.format(
                        "⚙️ *Панель администратора JobPlatform*\n\n" +
                                "📊 *Подробная статистика платформы:*\n" +
                                " ├ 👥 Администраторов: `%d`\n" +
                                " ├ 🏢 Работодателей: `%d`\n" +
                                " ├ 👤 Исполнителей: `%d`\n" +
                                " ├ 💼 Активных вакансий: `%d`\n" +
                                " └ ✅ Завершенных смен: `%d`\n\n" +
                                "Выберите действие:",
                        admins, employers, workers, activeJobs, completedShifts
                );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton btnWorkers = new InlineKeyboardButton();
        btnWorkers.setText("👷 " + ("UZ".equals(lang) ? "Ishchilar" : "Рабочие"));
        btnWorkers.setCallbackData("admin_workers");

        InlineKeyboardButton btnEmployers = new InlineKeyboardButton();
        btnEmployers.setText("🏢 " + ("UZ".equals(lang) ? "Ish beruvchilar" : "Работодатели"));
        btnEmployers.setCallbackData("admin_employers");

        rows.add(List.of(btnWorkers, btnEmployers));

        InlineKeyboardButton btnFindTgId = new InlineKeyboardButton();
        btnFindTgId.setText("🔍 " + ("UZ".equals(lang) ? "TG ID bo'yicha qidiruv" : "Инфо по TG ID"));
        btnFindTgId.setCallbackData("admin_find_by_tg_id");

        rows.add(List.of(btnFindTgId));

        InlineKeyboardButton btnRefresh = new InlineKeyboardButton();
        btnRefresh.setText("🔄 " + ("UZ".equals(lang) ? "Yangilash" : "Обновить"));
        btnRefresh.setCallbackData("admin_refresh");

        InlineKeyboardButton btnLang = new InlineKeyboardButton();
        btnLang.setText("🌐 " + ("UZ".equals(lang) ? "Tilni o'zgartirish" : "Сменить язык"));
        btnLang.setCallbackData("change_language");

        rows.add(List.of(btnRefresh, btnLang));

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(menuText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleInfoCommand(long chatId, String text) {
        try {
            String[] parts = text.split("\\s+");
            if (parts.length > 1) {
                Long targetTgId = Long.parseLong(parts[1].trim());
                String infoText = adminService.getUserInfoByTgId(targetTgId);

                if (infoText == null || infoText.trim().isEmpty()) {
                    sendMessage(chatId, "⚠️ Пользователь с ID `" + targetTgId + "` не найден.");
                } else {
                    sendMessage(chatId, infoText);
                }
            } else {
                sendMessage(chatId, "⚠️ Укажите ID, например: `/info 123456789`");
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Некорректный формат ID. Используйте только цифры.");
        } catch (Exception e) {
            e.printStackTrace();
            // Konsolda xatoni aniq ko'rish uchun logga olamiz:
            System.err.println("CRITICAL ERROR IN INFO COMMAND: " + e.getMessage());
            sendMessage(chatId, "⚠️ Ошибка при получении информации о пользователе: " + e.getMessage());
        }
    }

    public void sendMessage(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .build();
        executeMessage(message);
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void blockUser(AdminDtoTwo blockDTO) {
        adminService.blockUser(blockDTO);

        String blockMessage = String.format(
                "❌ Ваш аккаунт заблокирован.\nПричина: %s",
                blockDTO.reason()
        );

        SendMessage sendMessage = SendMessage.builder()
                .chatId(blockDTO.userId().toString())
                .text(blockMessage)
                .build();

        executeMessage(sendMessage);
    }

    public void unblockUser(Long userId) {
        adminService.unblockUser(userId);

        String unblockMessage = "✅ Ваш аккаунт успешно разблокирован! Вы снова можете пользоваться ботом.";

        SendMessage sendMessage = SendMessage.builder()
                .chatId(userId.toString())
                .text(unblockMessage)
                .build();

        executeMessage(sendMessage);
    }

    private void sendLanguageMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("🌐 **Выберите язык / Tilni tanlang:**");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton ruBtn = new InlineKeyboardButton();
        ruBtn.setText("🇷🇺 Русский");
        ruBtn.setCallbackData("lang_ru");

        InlineKeyboardButton uzBtn = new InlineKeyboardButton();
        uzBtn.setText("🇺🇿 O'zbekcha");
        uzBtn.setCallbackData("lang_uz");

        rows.add(List.of(ruBtn, uzBtn));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        message.setParseMode("Markdown");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleAddAdminCommand(String text, long chatId) {
        if (!adminService.isAdmin(chatId)) {
            sendMessage(chatId, "❌ У вас нет прав для выполнения этой команды.");
            return;
        }

        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "⚠️ Формат команды: `/addadmin <Telegram_ID>`");
            return;
        }

        try {
            Long newAdminId = Long.parseLong(parts[1]);

            AdminDtoTwo newAdminDto = new AdminDtoTwo(
                    null, null, null, null, null, null, null, null, null,
                    newAdminId, null, null, null, null, null, null, null, null, 0L, 0L
            );
            adminService.createAdmin(newAdminDto);

            sendMessage(chatId, "✅ Пользователь с ID `" + newAdminId + "` успешно назначен администратором.");
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Некорректный Telegram ID. ID должен состоять только из цифр.");
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при добавлении администратора: " + e.getMessage());
        }
    }
}