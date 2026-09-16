/*
package uz.imaan.jobplatform.config;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.imaan.jobplatform.admin.TelegramBotService;




public class BotInitializerForAdmin {

    private final TelegramBotService adminBotService;

    public BotInitializerForAdmin(TelegramBotService adminBotService) {
        this.adminBotService = adminBotService;
    }

    @EventListener({ContextRefreshedEvent.class})
    public void init() {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(adminBotService);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}*/
