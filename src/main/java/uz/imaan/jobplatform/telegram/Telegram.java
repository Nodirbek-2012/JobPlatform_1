package uz.imaan.jobplatform.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
import uz.imaan.jobplatform.admin.category.Role;
import uz.imaan.jobplatform.admin.dto.AdminDtoTwo;
import uz.imaan.jobplatform.admin.dto.AdminStatsDto;
import uz.imaan.jobplatform.admin.dto.BlockUserDto;
import uz.imaan.jobplatform.admin.service.AdminService;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.JobSeekerHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class Telegram extends TelegramLongPollingBot {

    public enum UserRole {
        NONE,
        EMPLOYER,
        JOB_SEEKER
    }

    private final TelegramCallbackService telegramCallbackService;
    private final JobSeekerHandler jobSeekerHandler;
    private final EmployerHandler employerHandler;

    private final Map<Long, UserRole> userRoles = new ConcurrentHashMap<>();

    // ============ АДМИН-ЧАСТЬ ============
    @Autowired
    @Lazy
    private AdminService adminService;

    private final Map<Long, Boolean> adminMode = new ConcurrentHashMap<>();
    // ====================================

    @Autowired
    public Telegram(
            JobSeekerHandler jobSeekerHandler,
            EmployerHandler employerHandler,
            TelegramCallbackService telegramCallbackService,
            @Value("8500721788:AAHSLEd2QwxEBaQZj0vS4uiIW73jPKIFrdM") String botToken) {
        super(botToken);
        this.jobSeekerHandler = jobSeekerHandler;
        this.employerHandler = employerHandler;
        this.telegramCallbackService = telegramCallbackService;
        log.info("✅ Telegram bot ishga tushdi!");
    }

    @Override
    public String getBotUsername() {
        return "@JobPlatformUzBot";
    }

    @Override
    public void onUpdateReceived(Update update) {

        // ============================================
        // 1. CALLBACK QUERY
        // ============================================
        if (update.hasCallbackQuery()) {
            log.info("📩 Callback keldi: {}", update.getCallbackQuery().getData());

            // Админские callback-и
            if (handleAdminCallback(update.getCallbackQuery())) {
                return;
            }

            // Остальные — в ваш сервис
            telegramCallbackService.processCallback(update.getCallbackQuery(), this);
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

        // Админ-команды — перехватываем ДО всей остальной логики
        if (handleAdminCommand(chatId, text)) {
            return;
        }

        // ============================================
        // ВАША ЛОГИКА — не тронута
        // ============================================
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

    // ================================================
    // ============ АДМИН-ЧАСТЬ =======================
    // ================================================

    /**
     * @return true — если команда обработана, дальше идти не надо
     */
    private boolean handleAdminCommand(long chatId, String text) {
        if (text == null || text.isBlank()) return false;

        String command = text.split("\\s+")[0].toLowerCase();

        // ---- /admin — вход ----
        if ("/admin".equals(command)) {
            log.info("🎯 /admin detected for chatId={}", chatId);

            boolean isAdmin;
            try {
                isAdmin = adminService.isAdmin(chatId);
            } catch (Exception e) {
                log.error("❌ isAdmin error: {}", e.getMessage(), e);
                sendMessageSafe(chatId, "❌ Ошибка проверки прав: " + e.getMessage());
                return true;
            }

            if (!isAdmin) {
                sendMessageSafe(chatId, "❌ У вас нет прав администратора.");
                return true;
            }

            adminMode.put(chatId, true);
            sendAdminMenuSafe(chatId);
            return true;
        }

        // ---- /exit_admin — выход ----
        if ("/exit_admin".equals(command)) {
            adminMode.put(chatId, false);
            sendMessageSafe(chatId, "👋 Вы вышли из админ-режима.");
            return true;
        }

        // ---- Остальные админ-команды — только в админ-режиме ----
        if (!isInAdminMode(chatId)) return false;

        switch (command) {
            case "/info" -> { handleInfoCommand(chatId, text); return true; }
            case "/block" -> { handleBlockCommand(chatId, text); return true; }
            case "/unblock" -> { handleUnblockCommand(chatId, text); return true; }
            case "/addadmin" -> { handleAddAdminCommand(chatId, text); return true; }
            case "/start" -> {
                adminMode.put(chatId, false);
                return false; // пусть обработается ниже как обычный /start
            }
            default -> { return false; }
        }
    }

    private boolean handleAdminCallback(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        if (data == null) return false;
        if (!isInAdminMode(chatId)) return false;

        switch (data) {
            case "admin_workers" -> { sendMessageSafe(chatId, adminService.getAllWorkersList()); return true; }
            case "admin_employers" -> { sendMessageSafe(chatId, adminService.getFormattedEmployersList()); return true; }
            case "admin_refresh" -> { sendAdminMenuSafe(chatId); return true; }
            case "admin_find_by_tg_id" -> { sendMessageSafe(chatId, "🔍 Отправьте: /info 123456789"); return true; }
            case "admin_exit" -> {
                adminMode.put(chatId, false);
                sendMessageSafe(chatId, "👋 Вы вышли из админ-режима.");
                return true;
            }
            case "change_language" -> { sendLanguageMenuAdmin(chatId); return true; }
            case "lang_ru" -> { sendMessageSafe(chatId, "✅ Язык изменён на Русский!"); sendAdminMenuSafe(chatId); return true; }
            case "lang_uz" -> { sendMessageSafe(chatId, "✅ Til O'zbekchaga o'zgartirildi!"); sendAdminMenuSafe(chatId); return true; }
            default -> { return false; }
        }
    }

    private boolean isInAdminMode(long chatId) {
        return Boolean.TRUE.equals(adminMode.getOrDefault(chatId, false));
    }

    // ============ ОТПРАВКА АДМИН-МЕНЮ (БЕЗ MARKDOWN) ============

    public void sendAdminMenuSafe(long chatId) {
        try {
            AdminStatsDto stats = adminService.getDetailedStats();

            String menuText = String.format(
                    "⚙️ Панель администратора JobPlatform\n\n" +
                            "📊 Статистика:\n" +
                            " ├ 👥 Администраторов: %d\n" +
                            " ├ 🏢 Работодателей: %d\n" +
                            " ├ 👤 Исполнителей: %d\n" +
                            " ├ 💼 Активных вакансий: %d\n" +
                            " └ ✅ Завершённых смен: %d\n\n" +
                            "Для выхода отправьте /exit_admin\n\n" +
                            "Выберите действие:",
                    stats.totalAdmins(), stats.totalEmployers(),
                    stats.totalWorkers(), stats.activeJobs(), stats.completedShifts());

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            rows.add(List.of(
                    button("👷 Рабочие", "admin_workers"),
                    button("🏢 Работодатели", "admin_employers")
            ));
            rows.add(List.of(
                    button("🔍 Инфо по TG ID", "admin_find_by_tg_id")
            ));
            rows.add(List.of(
                    button("🔄 Обновить", "admin_refresh"),
                    button("🌐 Язык", "change_language")
            ));
            rows.add(List.of(
                    button("🚪 Выйти из админ-режима", "admin_exit")
            ));

            markup.setKeyboard(rows);

            SendMessage message = SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(menuText)
                    .replyMarkup(markup)
                    .build();

            executeMessage(message);

        } catch (Exception e) {
            log.error("❌ sendAdminMenu error: {}", e.getMessage(), e);
            sendMessageSafe(chatId, "⚠️ Ошибка при открытии админ-меню: " + e.getMessage());
        }
    }

    private void handleInfoCommand(long chatId, String text) {
        try {
            String[] parts = text.split("\\s+");
            if (parts.length < 2) {
                sendMessageSafe(chatId, "⚠️ Укажите ID: /info 123456789");
                return;
            }
            Long targetTgId = Long.parseLong(parts[1].trim());
            sendMessageSafe(chatId, adminService.getUserInfoByTgId(targetTgId));
        } catch (NumberFormatException e) {
            sendMessageSafe(chatId, "❌ Некорректный ID.");
        } catch (Exception e) {
            sendMessageSafe(chatId, "⚠️ Ошибка: " + e.getMessage());
        }
    }

    private void handleBlockCommand(long chatId, String text) {
        String[] parts = text.split(" ", 3);
        if (parts.length < 3) {
            sendMessageSafe(chatId, "⚠️ Формат: /block <ID> <Причина>");
            return;
        }
        try {
            Long targetUserId = Long.parseLong(parts[1]);
            String reason = parts[2];
            adminService.blockUser(new BlockUserDto(targetUserId, reason));
            sendMessageSafe(chatId, "✅ Пользователь " + targetUserId + " заблокирован.");
        } catch (NumberFormatException e) {
            sendMessageSafe(chatId, "❌ Неверный ID.");
        } catch (Exception e) {
            sendMessageSafe(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void handleUnblockCommand(long chatId, String text) {
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            sendMessageSafe(chatId, "⚠️ Формат: /unblock <ID>");
            return;
        }
        try {
            Long targetUserId = Long.parseLong(parts[1]);
            adminService.unblockUser(targetUserId);
            sendMessageSafe(chatId, "✅ Пользователь " + targetUserId + " разблокирован.");
        } catch (NumberFormatException e) {
            sendMessageSafe(chatId, "❌ Неверный ID.");
        } catch (Exception e) {
            sendMessageSafe(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void handleAddAdminCommand(long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sendMessageSafe(chatId, "⚠️ Формат: /addadmin <Telegram_ID>");
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
            sendMessageSafe(chatId, "✅ Пользователь " + newAdminId + " назначен админом.");
        } catch (NumberFormatException e) {
            sendMessageSafe(chatId, "❌ Некорректный ID.");
        } catch (Exception e) {
            sendMessageSafe(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private InlineKeyboardButton button(String text, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(data);
        return b;
    }

    private void sendLanguageMenuAdmin(long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(
                button("🇷🇺 Русский", "lang_ru"),
                button("🇺🇿 O'zbekcha", "lang_uz")
        )));

        executeMessage(SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text("🌐 Выберите язык / Tilni tanlang:")
                .replyMarkup(markup)
                .build());
    }

    // ============ БЕЗОПАСНАЯ ОТПРАВКА СООБЩЕНИЯ ============

    /**
     * Отправляет сообщение БЕЗ parseMode, чтобы Telegram не падал
     * на спецсимволах Markdown.
     */
    public void sendMessageSafe(long chatId, String text) {
        try {
            executeMessage(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(text)
                    .build());
        } catch (Exception e) {
            log.error("❌ sendMessage error: {}", e.getMessage(), e);
        }
    }

    // ================================================
    // ============ КОНЕЦ АДМИН-ЧАСТИ =================
    // ================================================

    // ============================================
    // PUBLIC METODLAR (TelegramCallbackService chaqiradi)
    // ============================================
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

    // ============================================
    // ВАШИ УТИЛИТЫ — НЕ ТРОНУТЫ
    // ============================================
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
        row1.add(InlineKeyboardButton.builder()
                .text("🇺🇿 O'zbek").callbackData("lang_uz").build());
        row1.add(InlineKeyboardButton.builder()
                .text("🇷🇺 Русский").callbackData("lang_ru").build());
        row1.add(InlineKeyboardButton.builder()
                .text("🇬🇧 English").callbackData("lang_en").build());

        rows.add(row1);
        markup.setKeyboard(rows);
        return markup;
    }

    // ============================================
    // ХЕЛПЕР
    // ============================================
    public void sendMessage(long chatId, String text) {
        sendMessageSafe(chatId, text);
    }
}