package uz.imaan.jobplatform.vacancy;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;



public class CallbackHandler {
    // 1. Asosiy callback'ni qabul qiluvchi metod
    public void handle(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String data = callbackQuery.getData();
        String chatId = String.valueOf(callbackQuery.getMessage().getChatId());

        System.out.println("📩 Callback keldi: chatId=" + chatId + ", data=" + data);

        // 2. Kategoriya tanlash callback'larini boshqarish
        if (data.startsWith("emp_cat_")) {
            handleCategorySelection(callbackQuery, chatId, data);
        }
        // Boshqa tugmalar uchun (masalan: "back", "next")
        else if (data.equals("back_to_menu")) {
            handleBackToMenu(callbackQuery, chatId);
        }
    }

    // Kategoriyani ajratib oluvchi yordamchi metod
    private void handleCategorySelection(CallbackQuery callbackQuery, String chatId, String data) {
        // "emp_cat_" prefiksini olib tashlaymiz va probellarni tozalaymiz
        String category = data.replace("emp_cat_", "").trim();

        // Konsolda ko'rish
        System.out.println("✅ Tanlangan kategoriya: " + category);

        // 3. Bazaga saqlash logikasi (JobPlatform uchun)
        // employerService.saveCategory(chatId, category);

        // 4. Tugmani "loading" holatidan chiqarish (AnswerCallbackQuery)
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        answer.setText("Siz tanladingiz: " + category);
        answer.setShowAlert(false); // true qilsangiz, ekranda oyna chiqadi

        // Bot orqali answer'ni jo'natish (botingizdagi send metodidan foydalaning)
        // sender.execute(answer);

        // 5. Keyingi savolga o'tish
        // sendNextQuestion(chatId);
    }

    private void handleBackToMenu(CallbackQuery callbackQuery, String chatId) {
        // Asosiy menyuga qaytish logikasi
    }
}
