package uz.imaan.jobplatform.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import uz.imaan.jobplatform.admin.category.Role;
import uz.imaan.jobplatform.admin.dto.AdminDtoTwo;
import uz.imaan.jobplatform.admin.dto.AdminStatsDto;
import uz.imaan.jobplatform.admin.dto.BlockUserDto;
import uz.imaan.jobplatform.admin.service.AdminService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelegramBotService extends TelegramLongPollingBot {

    private final Map<Long, String> userLanguages = new ConcurrentHashMap<>();

    private final String botUsername;
    private long lastUpdateId = 0;

    @Autowired
    @Lazy
    private AdminService adminService;

    public TelegramBotService(
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String username) {
        super(token);
        this.botUsername = username;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    private String getLanguage(long chatId) {
        return userLanguages.getOrDefault(chatId, "RU");
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message = update.getMessage();
                if (lastUpdateId >= update.getUpdateId()) return;
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

        if (!text.startsWith("/")) return;

        switch (text.split("\\s+")[0]) {
            case "/start" -> {
                userLanguages.putIfAbsent(chatId, "RU");
                sendMessage(chatId, "Привет! Бот поиска почасовой работы JobPlatform запущен.");
            }
            case "/admin" -> {
                if (!adminService.isAdmin(chatId)) {
                    sendMessage(chatId, "У вас нет прав администратора.");
                    return;
                }
                sendAdminMenu(chatId);
            }
            case "/block", "/unblock" -> handleAdminCommand(text, chatId);
            case "/info" -> handleInfoCommand(chatId, text);
            default -> { /* ignore */ }
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

        switch (data) {
            case "change_language" -> sendLanguageMenu(chatId);
            case "lang_ru" -> {
                userLanguages.put(chatId, "RU");
                sendMessage(chatId, "✅ Язык успешно изменён на *Русский*!");
                sendAdminMenu(chatId);
            }
            case "lang_uz" -> {
                userLanguages.put(chatId, "UZ");
                sendMessage(chatId, "✅ Til *O'zbekcha*ga muvaffaqiyatli o'zgartirildi!");
                sendAdminMenu(chatId);
            }
            case "admin_workers", "workers" -> sendMessage(chatId, adminService.getAllWorkersList());
            case "admin_employers", "employers" -> sendMessage(chatId, adminService.getFormattedEmployersList());
            case "admin_refresh" -> sendAdminMenu(chatId);
            case "admin_find_by_tg_id" -> {
                String text = "UZ".equals(getLanguage(chatId))
                        ? "🔍 Foydalanuvchi haqida ma'lumot olish uchun:\n\n`/info 123456789`"
                        : "🔍 Чтобы получить информацию о пользователе, отправьте:\n\n`/info 123456789`";
                sendMessage(chatId, text);
            }
            default -> { /* ignore */ }
        }
    }

    private void handleAdminCommand(String text, Long adminChatId) {
        if (text.startsWith("/block")) {
            String[] parts = text.split(" ", 3);
            if (parts.length < 3) {
                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("⚠️ Формат: /block <ID> <Причина>").build());
                return;
            }
            try {
                Long targetUserId = Long.parseLong(parts[1]);
                String reason = parts[2];
                adminService.blockUser(new BlockUserDto(targetUserId, reason));
                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("✅ Пользователь " + targetUserId + " заблокирован.").build());
            } catch (NumberFormatException e) {
                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("❌ Неверный ID пользователя.").build());
            }
        } else if (text.startsWith("/unblock")) {
            String[] parts = text.split(" ", 2);
            if (parts.length < 2) {
                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("⚠️ Формат: /unblock <ID>").build());
                return;
            }
            try {
                Long targetUserId = Long.parseLong(parts[1]);
                adminService.unblockUser(targetUserId);
                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("✅ Пользователь " + targetUserId + " разблокирован.").build());
            } catch (NumberFormatException e) {
                executeMessage(SendMessage.builder()
                        .chatId(adminChatId.toString())
                        .text("❌ Неверный ID пользователя.").build());
            }
        }
    }

    public void sendAdminMenu(long chatId) {
        AdminStatsDto stats = adminService.getDetailedStats();
        String lang = getLanguage(chatId);

        String menuText = "UZ".equals(lang)
                ? String.format("""
                        ⚙️ *JobPlatform Administrator Paneli*

                        📊 *Statistika:*
                         ├ 👥 Adminlar: `%d`
                         ├ 🏢 Ish beruvchilar: `%d`
                         ├ 👤 Ishchilar: `%d`
                         ├ 💼 Faol vakansiyalar: `%d`
                         └ ✅ Yakunlangan smenalar: `%d`

                        Harakatni tanlang:""",
                stats.totalAdmins(), stats.totalEmployers(),
                stats.totalWorkers(), stats.activeJobs(), stats.completedShifts())
                : String.format("""
                        ⚙️ *Панель администратора JobPlatform*

                        📊 *Статистика:*
                         ├ 👥 Администраторов: `%d`
                         ├ 🏢 Работодателей: `%d`
                         ├ 👤 Исполнителей: `%d`
                         ├ 💼 Активных вакансий: `%d`
                         └ ✅ Завершённых смен: `%d`

                        Выберите действие:""",
                stats.totalAdmins(), stats.totalEmployers(),
                stats.totalWorkers(), stats.activeJobs(), stats.completedShifts());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
                button("👷 " + ("UZ".equals(lang) ? "Ishchilar" : "Рабочие"), "admin_workers"),
                button("🏢 " + ("UZ".equals(lang) ? "Ish beruvchilar" : "Работодатели"), "admin_employers")
        ));
        rows.add(List.of(
                button("🔍 " + ("UZ".equals(lang) ? "TG ID bo'yicha" : "Инфо по TG ID"), "admin_find_by_tg_id")
        ));
        rows.add(List.of(
                button("🔄 " + ("UZ".equals(lang) ? "Yangilash" : "Обновить"), "admin_refresh"),
                button("🌐 " + ("UZ".equals(lang) ? "Til" : "Язык"), "change_language")
        ));

        markup.setKeyboard(rows);

        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(menuText)
                .parseMode("Markdown")
                .replyMarkup(markup)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardButton button(String text, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(data);
        return b;
    }

    private void handleInfoCommand(long chatId, String text) {
        try {
            String[] parts = text.split("\\s+");
            if (parts.length < 2) {
                sendMessage(chatId, "⚠️ Укажите ID: `/info 123456789`");
                return;
            }
            Long targetTgId = Long.parseLong(parts[1].trim());
            sendMessage(chatId, adminService.getUserInfoByTgId(targetTgId));
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Некорректный ID.");
        } catch (Exception e) {
            sendMessage(chatId, "⚠️ Ошибка: " + e.getMessage());
        }
    }

    public void sendMessage(long chatId, String text) {
        executeMessage(SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .build());
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendLanguageMenu(long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(
                button("🇷🇺 Русский", "lang_ru"),
                button("🇺🇿 O'zbekcha", "lang_uz")
        )));

        try {
            execute(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text("🌐 *Выберите язык / Tilni tanlang:*")
                    .parseMode("Markdown")
                    .replyMarkup(markup)
                    .build());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleAddAdminCommand(String text, long chatId) {
        if (!adminService.isAdmin(chatId)) {
            sendMessage(chatId, "❌ Нет прав.");
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "⚠️ Формат: `/addadmin <Telegram_ID>`");
            return;
        }
        try {
            Long newAdminId = Long.parseLong(parts[1]);
            adminService.createAdmin(
                    AdminDtoTwo.builder()
                            .telegramId(newAdminId)
                            .role(Role.ADMIN)
                            .isActive(true)
                            .build()
            );
            sendMessage(chatId, "✅ Пользователь `" + newAdminId + "` назначен админом.");
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Некорректный ID.");
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }
}