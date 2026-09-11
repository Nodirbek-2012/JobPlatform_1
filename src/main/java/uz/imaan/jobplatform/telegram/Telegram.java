package uz.imaan.jobplatform.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
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

    private final JobSeekerHandler jobSeekerHandler;
    private final EmployerHandler employerHandler;
    private final TelegramCallbackService callbackService; // ⭐️ Yangi servis ulandi

    private final Map<Long, UserRole> userRoles = new ConcurrentHashMap<>();

    @Autowired
    public Telegram(
            JobSeekerHandler jobSeekerHandler,
            EmployerHandler employerHandler,
            TelegramCallbackService callbackService,
            @Value("${telegram.bot.token:8449248126:AAFPgTpsBD2o1k_cp8YbG8_wqp9o8KnRCss}") String botToken) {
        super(botToken);
        this.jobSeekerHandler = jobSeekerHandler;
        this.employerHandler = employerHandler;
        this.callbackService = callbackService;
        log.info("✅ Telegram bot muvaffaqiyatli ishga tushdi!");
    }

    @Override
    public String getBotUsername() {
        return "@JobPlatformUzBot";
    }

    @Override
    public void onUpdateReceived(Update update) {

        // ============================================
        // 1. INLINE TUGMALAR (Barchasi yangi servisga o'tadi)
        // ============================================
        if (update.hasCallbackQuery()) {
            callbackService.processCallback(update.getCallbackQuery(), this);
            return;
        }

        // ============================================
        // 2. TEKST XABARLAR (Asosiy boshqaruv)
        // ============================================
        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.hasText() ? message.getText() : "";

        log.info("📩 Matnli xabar: chatId={}, text={}", chatId, text);

        // ⭐ Saralanganlar
        if (text.contains("Saralanganlar") || text.contains("Избранное") || text.contains("Favorites")) {
            callbackService.showFavoritesPage(chatId, 0, null, this);
            return;
        }

        // /start va menyu
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

    // ============================================
    // BOT CHAT AMALLARI (PUBLIC METODLAR)
    // ============================================

    public void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Xabar jo'natishda xatolik: {}", e.getMessage());
        }
    }

    public void executeEditMessage(EditMessageText message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Xabarni tahrirlashda xatolik: {}", e.getMessage());
        }
    }

    public void executeEditReplyMarkup(EditMessageReplyMarkup markup) {
        try {
            execute(markup);
        } catch (TelegramApiException e) {
            log.error("❌ Tugmalarni yangilashda xatolik: {}", e.getMessage());
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
            log.error("❌ Callback javobida xatolik: {}", e.getMessage());
        }
    }

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

        row1.add(InlineKeyboardButton.builder().text("🇺🇿 O'zbek").callbackData("lang_uz").build());
        row1.add(InlineKeyboardButton.builder().text("🇷🇺 Русский").callbackData("lang_ru").build());
        row1.add(InlineKeyboardButton.builder().text("🇬🇧 English").callbackData("lang_en").build());

        rows.add(row1);
        markup.setKeyboard(rows);
        return markup;
    }
}