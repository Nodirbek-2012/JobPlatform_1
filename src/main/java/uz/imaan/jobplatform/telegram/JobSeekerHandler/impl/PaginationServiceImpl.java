package uz.imaan.jobplatform.telegram.JobSeekerHandler.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.imaan.jobplatform.employer.job.JobStore;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.KeyboardService;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.PaginationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaginationServiceImpl implements PaginationService {

    private static final int PAGE_SIZE = 5;

    private final JobStore jobStore;
    private final KeyboardService keyboardService;

    // ============================================
    // YORDAMCHI METODLAR
    // ============================================
    private boolean isRussian(Optional<JobSeekerProfile> profileOpt) {
        return profileOpt.isPresent() && "ru".equals(profileOpt.get().getLanguage());
    }

    private boolean isEnglish(Optional<JobSeekerProfile> profileOpt) {
        return profileOpt.isPresent() && "en".equals(profileOpt.get().getLanguage());
    }

    private String getCategoryName(String key) {
        return switch (key) {
            case "it" -> "💻 IT & Dasturlash";
            case "design" -> "🎨 Dizayn";
            case "construction" -> "🏗️ Qurilish";
            case "driver" -> "🚗 Haydovchi / Kuryer";
            case "education" -> "📚 Ta'lim / Repetitor";
            case "trade" -> "🛒 Savdo / Sotuvchi";
            case "cleaner" -> "🧹 Farrosh / Tozalash";
            case "cook" -> "👨‍🍳 Pazanda / Oshpaz";
            case "security" -> "🔒 Qorovul / Xavfsizlik";
            case "courier" -> "📦 Kuryer / Yetkazib berish";
            case "all" -> "Barcha vakansiyalar";
            default -> key;
        };
    }

    private SendMessage createMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        if (keyboard != null) message.setReplyMarkup(keyboard);
        return message;
    }

    // ============================================
    // PAGINATION KEYBOARD
    // ============================================
    private InlineKeyboardMarkup buildPaginationKeyboard(String categoryKey, int currentPage, int totalPages,
                                                         Optional<JobSeekerProfile> profileOpt) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        // Oldingi sahifa
        if (currentPage > 0) {
            InlineKeyboardButton prev = new InlineKeyboardButton();
            prev.setText("⬅️");
            prev.setCallbackData("vacancy_page_" + categoryKey + "_" + (currentPage - 1));
            row.add(prev);
        }

        // Sahifa raqami
        InlineKeyboardButton pageInfo = new InlineKeyboardButton();
        pageInfo.setText((currentPage + 1) + "/" + totalPages);
        pageInfo.setCallbackData("ignore");
        row.add(pageInfo);

        // Keyingi sahifa
        if (currentPage < totalPages - 1) {
            InlineKeyboardButton next = new InlineKeyboardButton();
            next.setText("➡️");
            next.setCallbackData("vacancy_page_" + categoryKey + "_" + (currentPage + 1));
            row.add(next);
        }
        rows.add(row);

        // Orqaga qaytish
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton back = new InlineKeyboardButton();
        String backText = isRussian(profileOpt) ? "⬅️ Назад" : (isEnglish(profileOpt) ? "⬅️ Back" : "⬅️ Orqaga");
        back.setText(backText);
        back.setCallbackData("back_to_categories");
        backRow.add(back);
        rows.add(backRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    // ============================================
    // ASOSIY METODLAR
    // ============================================
    @Override
    public void handleCategorySearch(Long chatId, String categoryKey, Optional<JobSeekerProfile> profileOpt) {
        // Bu metod faqat paginationni chaqiradi, natijani yuborish Handler zimmasida
        // Handler bu metodni chaqirib, qaytgan SendMessage ni execute qiladi
    }

    @Override
    public SendMessage handleVacancyPagination(Long chatId, String categoryKey, int page,
                                               Optional<JobSeekerProfile> profileOpt) {
        try {
            // 1. Vakansiyalarni olish
            List<JobVacancy> allVacancies;
            if ("all".equals(categoryKey)) {
                allVacancies = jobStore.getAllVacancies();
            } else {
                String categoryName = getCategoryName(categoryKey);
                allVacancies = jobStore.getAllVacancies().stream()
                        .filter(v -> v.getCategory() != null &&
                                v.getCategory().toLowerCase().contains(categoryName.toLowerCase()))
                        .toList();
            }

            // 2. Vakansiyalar mavjudligini tekshirish
            if (allVacancies == null || allVacancies.isEmpty()) {
                String msg = keyboardService.getText(profileOpt,
                        "❌ В этой категории пока нет активных вакансий.",
                        "❌ Bu kategoriya bo'yicha hozircha faol vakansiyalar mavjud emas.",
                        "❌ No active vacancies available for this category."
                );
                return createMessage(chatId, msg, null);
            }

            // 3. Jami sahifalar soni
            int totalPages = (int) Math.ceil((double) allVacancies.size() / PAGE_SIZE);
            if (page < 0) page = 0;
            if (page >= totalPages) page = totalPages - 1;

            // 4. Joriy sahifa vakansiyalari
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, allVacancies.size());
            List<JobVacancy> pageVacancies = allVacancies.subList(start, end);

            // 5. Xabar matnini tayyorlash
            StringBuilder result = new StringBuilder();
            String title = "📋 **Topilgan vakansiyalar (" + allVacancies.size() + "):**\n\n";
            result.append(title);

            int globalIndex = start + 1;
            for (JobVacancy vacancy : pageVacancies) {
                result.append(globalIndex++).append(". 📌 **").append(vacancy.getTitle()).append("**\n");
                result.append("   📂 Kategoriya: ").append(vacancy.getCategory()).append("\n");
                result.append("   💰 Maosh: ").append(vacancy.getSalary()).append("\n");
                result.append("   🏢 Kompaniya: ").append(vacancy.getCompanyName() != null ? vacancy.getCompanyName() : "Ko'rsatilmagan").append("\n");
                result.append("───────────────\n");
            }

            // 6. Keyboard
            InlineKeyboardMarkup keyboard = buildPaginationKeyboard(categoryKey, page, totalPages, profileOpt);

            // 7. Xabar
            return createMessage(chatId, result.toString(), keyboard);

        } catch (Exception e) {
            log.error("❌ Paginatsiya xatoligi: {}", e.getMessage(), e);
            String msg = keyboardService.getText(profileOpt,
                    "❌ Произошла ошибка. Пожалуйста, попробуйте еще раз.",
                    "❌ Xatolik yuz berdi. Qaytadan urinib ko'ring.",
                    "❌ An error occurred. Please try again."
            );
            return createMessage(chatId, msg, null);
        }
    }
}
