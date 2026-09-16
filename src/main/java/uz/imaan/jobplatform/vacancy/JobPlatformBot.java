package uz.imaan.jobplatform.vacancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.imaan.jobplatform.telegram.EmployerHandler;

@Slf4j
@RequiredArgsConstructor
public class JobPlatformBot extends TelegramLongPollingBot {

    private final EmployerHandler employerHandler;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            SendMessage responseMessage = null;

            if (update.hasCallbackQuery()) {
                String callbackQueryId = update.getCallbackQuery().getId();
                String data = update.getCallbackQuery().getData();
                log.info("👉 Inline tugma bosildi! Data: {}", data);

                // Telegramdagi tugma aylanishini (loading) to'xtatish
                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(callbackQueryId);
                try {
                    execute(answer);
                } catch (TelegramApiException e) {
                    log.error("Callback javobini yuborishda xatolik: {}", e.getMessage());
                }

                // Employer handlerga yo'naltiramiz
                responseMessage = employerHandler.handleCallback(update.getCallbackQuery());

            } else if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                log.info("💬 Matnli xabar keldi: {}", text);
                responseMessage = employerHandler.handleEmployer(update.getMessage());
            } else if (update.hasMessage() && update.getMessage().hasContact()) {
                log.info("📞 Kontakt keldi");
                responseMessage = employerHandler.handleEmployer(update.getMessage());
            }

            // Javobni Telegramga yuborish
            if (responseMessage != null) {
                execute(responseMessage);
                log.info("📤 Bot tomonidan javob yuborildi.");
            }

        } catch (TelegramApiException e) {
            log.error("❌ Telegram API xatoligi: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Kutilmagan xatolik: {}", e.getMessage(), e);
        }
    }
}