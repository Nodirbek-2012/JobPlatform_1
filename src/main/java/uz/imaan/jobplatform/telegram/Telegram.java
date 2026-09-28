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
import java.util.HashMap;
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
    // ======================================

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
        // 1. CALLBACK QUERY (Inline tugmalar)
        // ============================================
        if (update.hasCallbackQuery()) {
            log.info("📩 Callback keldi: {}", update.getCallbackQuery().getData());

            if (handleAdminCallback(update.getCallbackQuery())) {
                return;
            }

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

        // Admin komandalarini tekshirish
        if (handleAdminCommand(chatId, text)) {
            return;
        }

        // Start yoki rolni o'zgartirish
        if (text.equals("/start") || text.equals("🔄 Rolni o'zgartirish")) {
            userRoles.put(chatId, UserRole.NONE);
            sendRoleSelectionMenu(chatId, "Xush kelibsiz! Rolingizni tanlang:");
            return;
        }

        // ============================================
        // ROLLARNI TANLASH VA AJRATISH (To'qnashuvsiz)
        // ============================================
        if (text.contains("Employer (Ish beruvchi)") || text.contains("Ish beruvchi (Employer)")) {
            userRoles.put(chatId, UserRole.EMPLOYER);
            SendMessage response = employerHandler.handleEmployer(message);
            if (response != null) executeMessage(response);
            return; // 👈 Keyingi bloklarga o'tib ketmasligi uchun to'xtatadi
        }
        else if (text.contains("JobSeeker (Ish izlovchi)") || text.contains("Ish izlovchi (JobSeeker)")) {
            userRoles.put(chatId, UserRole.JOB_SEEKER);
            SendMessage response = jobSeekerHandler.handleJobSeeker(message);
            if (response != null) executeMessage(response);
            return; // 👈 Ish beruvchi kodi bilan aralashib ketmasligi uchun to'xtatadi
        }

        // ============================================
        // MAVJUD ROL BO'YICHA XABARLARNI YUBORISH
        // ============================================
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

    private boolean handleAdminCommand(long chatId, String text) {
        if (text == null || text.isBlank()) return false;

        String command = text.split("\\s+")[0].toLowerCase();

        if ("/admin".equals(command)) {
            log.info("🎯 Команда /admin от chatId={}", chatId);

            boolean isAdmin;
            try {
                isAdmin = adminService.isAdmin(chatId);
            } catch (Exception e) {
                log.error("❌ isAdmin error: {}", e.getMessage(), e);
                sendMessageSafe(chatId, "❌ Ошибка проверки прав: " + e.getMessage());
                return true;
            }

            if (!isAdmin) {
                log.warn("🚫 Отказ: chatId={} НЕ админ. Ответ отправлен.", chatId);
                sendMessageSafe(chatId, "❌ У вас нет прав администратора.\nВаш ID: " + chatId);
                return true;
            }

            log.info("✅ Доступ разрешён для chatId={}", chatId);
            adminMode.put(chatId, true);
            sendAdminMenu(chatId);
            return true;
        }

        if ("/exit_admin".equals(command)) {
            adminMode.put(chatId, false);
            sendMessageSafe(chatId, "👋 Вы вышли из админ-режима.");
            return true;
        }

        if (!isInAdminMode(chatId)) return false;

        switch (command) {
            case "/info" -> { handleInfoCommand(chatId, text); return true; }
            case "/block" -> { handleBlockCommand(chatId, text); return true; }
            case "/unblock" -> { handleUnblockCommand(chatId, text); return true; }
            case "/addadmin" -> { handleAddAdminCommand(chatId, text); return true; }
            case "/start" -> {
                adminMode.put(chatId, false);
                return false;
            }
            default -> { return false; }
        }
    }

    public void sendWorkersPage(long chatId, int page) {
        try {
            Object[] data = adminService.getWorkersListPageData(page);
            String content = (String) data[0];
            @SuppressWarnings("unchecked")
            List<Long> idsOnPage = (List<Long>) data[1];
            int totalPages = (int) data[2];

            if (page < 0) page = 0;
            if (page >= totalPages) page = totalPages - 1;

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            if (!idsOnPage.isEmpty()) {
                List<InlineKeyboardButton> numbersRow = new ArrayList<>();
                for (int i = 0; i < idsOnPage.size(); i++) {
                    Long userId = idsOnPage.get(i);
                    numbersRow.add(button(String.valueOf(i + 1), "worker_detail_" + userId));
                }
                rows.add(numbersRow);
            }

            if (totalPages > 1) {
                List<InlineKeyboardButton> navRow = new ArrayList<>();
                if (page > 0) navRow.add(button("⬅️", "workers_page_" + (page - 1)));
                navRow.add(button((page + 1) + "/" + totalPages, "noop"));
                if (page < totalPages - 1) navRow.add(button("➡️", "workers_page_" + (page + 1)));
                rows.add(navRow);
            }

            rows.add(List.of(
                    button("⬅️ Назад в админ-меню", "admin_refresh")
            ));

            markup.setKeyboard(rows);

            SendMessage message = SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(content)
                    .parseMode("HTML")
                    .replyMarkup(markup)
                    .build();

            executeMessage(message);

        } catch (Exception e) {
            log.error("❌ sendWorkersPage error: {}", e.getMessage(), e);
            sendMessageSafe(chatId, "⚠️ Ошибка: " + e.getMessage());
        }
    }

    public void sendEmployersPage(long chatId, int page) {
        try {
            Object[] data = adminService.getEmployersListPageData(page);
            String content = (String) data[0];
            @SuppressWarnings("unchecked")
            List<Long> idsOnPage = (List<Long>) data[1];
            int totalPages = (int) data[2];

            if (page < 0) page = 0;
            if (page >= totalPages) page = totalPages - 1;

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            if (!idsOnPage.isEmpty()) {
                List<InlineKeyboardButton> numbersRow = new ArrayList<>();
                for (int i = 0; i < idsOnPage.size(); i++) {
                    Long userId = idsOnPage.get(i);
                    numbersRow.add(button(String.valueOf(i + 1), "employer_detail_" + userId));
                }
                rows.add(numbersRow);
            }

            if (totalPages > 1) {
                List<InlineKeyboardButton> navRow = new ArrayList<>();
                if (page > 0) navRow.add(button("⬅️", "employers_page_" + (page - 1)));
                navRow.add(button((page + 1) + "/" + totalPages, "noop"));
                if (page < totalPages - 1) navRow.add(button("➡️", "employers_page_" + (page + 1)));
                rows.add(navRow);
            }

            rows.add(List.of(
                    button("⬅️ Назад в админ-меню", "admin_refresh")
            ));

            markup.setKeyboard(rows);

            SendMessage message = SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(content)
                    .parseMode("HTML")
                    .replyMarkup(markup)
                    .build();

            executeMessage(message);

        } catch (Exception e) {
            log.error("❌ sendEmployersPage error: {}", e.getMessage(), e);
            sendMessageSafe(chatId, "⚠️ Ошибка: " + e.getMessage());
        }
    }

    private boolean handleAdminCallback(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        if (data == null) return false;
        if (!isInAdminMode(chatId)) return false;

        // ===== Рабочие — пагинация =====
        if (data.startsWith("workers_page_")) {
            try {
                int page = Integer.parseInt(data.substring("workers_page_".length()));
                sendWorkersPage(chatId, page);
            } catch (Exception e) {
                log.error("❌ workers page error: {}", e.getMessage(), e);
            }
            return true;
        }

        // ===== Работодатели — пагинация =====
        if (data.startsWith("employers_page_")) {
            try {
                int page = Integer.parseInt(data.substring("employers_page_".length()));
                sendEmployersPage(chatId, page);
            } catch (Exception e) {
                log.error("❌ employers page error: {}", e.getMessage(), e);
            }
            return true;
        }

        // ===== Детали рабочего =====
        if (data.startsWith("worker_detail_")) {
            try {
                Long workerId = Long.parseLong(data.substring("worker_detail_".length()));
                String details = adminService.getWorkerDetails(workerId);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                markup.setKeyboard(List.of(List.of(
                        button("⬅️ Назад к списку", "admin_workers")
                )));

                SendMessage message = SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text(details)
                        .parseMode("HTML")
                        .replyMarkup(markup)
                        .build();
                executeMessage(message);
            } catch (Exception e) {
                log.error("❌ worker_detail error: {}", e.getMessage(), e);
            }
            return true;
        }

        // ===== Детали работодателя =====
        if (data.startsWith("employer_detail_")) {
            try {
                Long employerId = Long.parseLong(data.substring("employer_detail_".length()));
                String details = adminService.getUserInfoByTgId(employerId);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                markup.setKeyboard(List.of(List.of(
                        button("⬅️ Назад к списку", "admin_employers")
                )));

                SendMessage message = SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text(details)
                        .parseMode("HTML")
                        .replyMarkup(markup)
                        .build();
                executeMessage(message);
            } catch (Exception e) {
                log.error("❌ employer_detail error: {}", e.getMessage(), e);
            }
            return true;
        }

        // ===== Остальные =====
        switch (data) {
            case "admin_workers" -> { sendWorkersPage(chatId, 0); return true; }
            case "admin_employers" -> { sendEmployersPage(chatId, 0); return true; }
            case "admin_refresh" -> { sendAdminMenu(chatId); return true; }
            case "admin_find_by_tg_id" -> {
                sendMessageSafe(chatId, "🔍 Отправьте: /info 123456789");
                return true;
            }
            case "admin_exit" -> {
                adminMode.put(chatId, false);
                sendMessageSafe(chatId, "👋 Вы вышли из админ-режима.");
                return true;
            }
            case "change_language" -> { sendLanguageMenuAdmin(chatId); return true; }
            case "lang_ru" -> {
                sendMessageSafe(chatId, "✅ Язык изменён на Русский!");
                sendAdminMenu(chatId);
                return true;
            }
            case "lang_uz" -> {
                sendMessageSafe(chatId, "✅ Til O'zbekchaga o'zgartirildi!");
                sendAdminMenu(chatId);
                return true;
            }
            case "admin_categories" -> { sendCategoryStats(chatId); return true; }
            default -> { return false; }
        }
    }

    private boolean isInAdminMode(long chatId) {
        return Boolean.TRUE.equals(adminMode.getOrDefault(chatId, false));
    }

    public void sendAdminMenu(long chatId) {
        try {
            AdminStatsDto stats = adminService.getDetailedStats();

            StringBuilder menuText = new StringBuilder();
            menuText.append("⚙️ Панель администратора JobPlatform\n\n");

            menuText.append("👥 ПОЛЬЗОВАТЕЛИ\n")
                    .append(" ├ Админов: ").append(stats.getTotalAdmins()).append("\n")
                    .append(" ├ Соискателей: ").append(stats.getTotalWorkers()).append("\n")
                    .append(" ├ Работодателей: ").append(stats.getTotalEmployers()).append("\n")
                    .append(" └ Всего: ").append(stats.getTotalUsers()).append("\n\n");

            menuText.append("💼 ВАКАНСИИ\n")
                    .append(" ├ Всего: ").append(stats.getTotalVacancies()).append("\n")
                    .append(" ├ Активных: ").append(stats.getActiveJobs()).append("\n")
                    .append(" └ Заполненных: ").append(stats.getFilledVacancies()).append("\n\n");

            menuText.append("📩 ЗАЯВКИ\n")
                    .append(" ├ Всего: ").append(stats.getTotalApplications()).append("\n")
                    .append(" ├ В ожидании: ").append(stats.getPendingApplications()).append("\n")
                    .append(" ├ Принято: ").append(stats.getAcceptedApplications()).append("\n")
                    .append(" └ Отклонено: ").append(stats.getRejectedApplications()).append("\n\n");

            menuText.append("💳 КАРТЫ И ФИНАНСЫ\n")
                    .append(" ├ Карт: ").append(stats.getTotalBankCards())
                    .append(" (активных: ").append(stats.getActiveBankCards()).append(")\n")
                    .append(" └ Баланс: ")
                    .append(stats.getTotalWalletBalance() != null ? stats.getTotalWalletBalance().toPlainString() : "0")
                    .append(" сум\n\n");

            menuText.append("⭐ ОТЗЫВЫ\n")
                    .append(" ├ Всего: ").append(stats.getTotalReviews()).append("\n")
                    .append(" └ Средний рейтинг: ")
                    .append(String.format("%.2f", stats.getAverageRating())).append("\n\n");

            menuText.append("📢 ПОДПИСКИ\n")
                    .append(" └ Всего: ").append(stats.getTotalSubscriptions()).append("\n\n");

            menuText.append("📂 КАТЕГОРИИ\n")
                    .append(" └ Всего: ").append(stats.getTotalCategories()).append("\n\n");

            Map<String, Long> vacByCat = stats.getVacanciesByCategory();
            if (vacByCat != null && !vacByCat.isEmpty()) {
                menuText.append("💼 ТОП-5 категорий по вакансиям:\n");
                vacByCat.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(5)
                        .forEach(e -> menuText.append(" ├ ")
                                .append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
                menuText.append("\n");
            }

            Map<String, Long> seekersByCat = stats.getSeekersByCategory();
            if (seekersByCat != null && !seekersByCat.isEmpty()) {
                menuText.append("👷 ТОП-5 категорий по соискателям:\n");
                seekersByCat.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(5)
                        .forEach(e -> menuText.append(" ├ ")
                                .append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
                menuText.append("\n");
            }

            Map<String, Long> subsByCat = stats.getSubscriptionsByCategory();
            if (subsByCat != null && !subsByCat.isEmpty()) {
                menuText.append("📢 ТОП-5 категорий по подпискам:\n");
                subsByCat.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(5)
                        .forEach(e -> menuText.append(" ├ ")
                                .append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
                menuText.append("\n");
            }

            menuText.append("Для выхода: /exit_admin\nВыберите действие:");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            rows.add(List.of(
                    button("👷 Рабочие", "admin_workers"),
                    button("🏢 Работодатели", "admin_employers")
            ));
            rows.add(List.of(
                    button("📩 Заявки", "admin_applications"),
                    button("💼 Вакансии", "admin_vacancies")
            ));
            rows.add(List.of(
                    button("📂 Категории", "admin_categories"),
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
                    .text(menuText.toString())
                    .replyMarkup(markup)
                    .build();

            executeMessage(message);

        } catch (Exception e) {
            log.error("❌ sendAdminMenu error: {}", e.getMessage(), e);
            sendMessageSafe(chatId, "⚠️ Ошибка при открытии админ-меню: " + e.getMessage());
        }
    }

    private void sendCategoryStats(long chatId) {
        try {
            AdminStatsDto stats = adminService.getDetailedStats();

            StringBuilder sb = new StringBuilder();
            sb.append("📂 СТАТИСТИКА ПО КАТЕГОРИЯМ\n\n");

            sb.append("Всего категорий: ").append(stats.getTotalCategories()).append("\n");
            sb.append("Вакансий: ").append(stats.getTotalVacancies()).append("\n");
            sb.append("Соискателей: ").append(stats.getTotalWorkers()).append("\n\n");

            Map<String, Long> vacByCat = stats.getVacanciesByCategory();
            if (vacByCat != null && !vacByCat.isEmpty()) {
                sb.append("💼 ЧТО ПУБЛИКУЮТ РАБОТОДАТЕЛИ:\n");
                vacByCat.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(e -> sb.append(" ├ ")
                                .append(e.getKey())
                                .append(" — ").append(e.getValue()).append(" вакансий\n"));
                sb.append("\n");
            } else {
                sb.append("💼 Вакансий по категориям нет.\n\n");
            }

            Map<String, Long> seekersByCat = stats.getSeekersByCategory();
            if (seekersByCat != null && !seekersByCat.isEmpty()) {
                sb.append("👷 ЧТО ВЫБИРАЮТ СОИСКАТЕЛИ:\n");
                seekersByCat.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(e -> sb.append(" ├ ")
                                .append(e.getKey())
                                .append(" — ").append(e.getValue()).append(" чел.\n"));
                sb.append("\n");
            } else {
                sb.append("👷 Соискателей по категориям нет.\n\n");
            }

            sb.append("🏆 ТОП-3 САМЫЕ ПОПУЛЯРНЫЕ КАТЕГОРИИ:\n");

            Map<String, Long> combined = new HashMap<>();
            if (seekersByCat != null) {
                seekersByCat.forEach((k, v) -> combined.merge(k, v * 2, Long::sum));
            }
            if (vacByCat != null) {
                vacByCat.forEach((k, v) -> combined.merge(k, v, Long::sum));
            }

            if (!combined.isEmpty()) {
                combined.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(3)
                        .forEach(e -> sb.append(" ├ ")
                                .append(e.getKey())
                                .append(" (score: ").append(e.getValue()).append(")\n"));
            } else {
                sb.append(" ├ Нет данных\n");
            }
            sb.append("\n");

            Map<String, Long> subsByCat = stats.getSubscriptionsByCategory();
            if (subsByCat != null && !subsByCat.isEmpty()) {
                sb.append("📢 ПОДПИСКИ НА КАТЕГОРИИ:\n");
                subsByCat.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(e -> sb.append(" ├ ")
                                .append(e.getKey())
                                .append(" — ").append(e.getValue()).append(" подписок\n"));
                sb.append("\n");
            }

            sb.append("Для возврата нажмите ⬅️ Назад");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(List.of(List.of(
                    button("⬅️ Назад", "admin_refresh"),
                    button("🔄 Обновить", "admin_categories")
            )));

            SendMessage message = SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(sb.toString())
                    .replyMarkup(markup)
                    .build();

            executeMessage(message);

        } catch (Exception e) {
            log.error("❌ sendCategoryStats error: {}", e.getMessage(), e);
            sendMessageSafe(chatId, "⚠️ Ошибка: " + e.getMessage());
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

    // ============ БЕЗОПАСНАЯ ОТПРАВКА ============

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
    // PUBLIC METODLAR
    // ================================================
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
    // VAZIFA UCHUN YORDAMCHI MENYULAR
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

    public void sendLanguageSelectionMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🌐 <b>Tilni tanlang / Выберите язык:</b>");
        message.setParseMode("HTML");
        message.setReplyMarkup(getLanguageInlineKeyboard());
        executeMessage(message);
    }

    /**
     * Faqat 2 ta til (O'zbek va Rus tillari). Ingliz tili butunlay olib tashlandi.
     */
    public InlineKeyboardMarkup getLanguageInlineKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
                .text("🇺🇿 O'zbek").callbackData("lang_uz").build());
        row1.add(InlineKeyboardButton.builder()
                .text("🇷🇺 Русский").callbackData("lang_ru").build());

        rows.add(row1);
        markup.setKeyboard(rows);
        return markup;
    }

    public void sendMessage(long chatId, String text) {
        executeMessage(SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .build());
    }
}