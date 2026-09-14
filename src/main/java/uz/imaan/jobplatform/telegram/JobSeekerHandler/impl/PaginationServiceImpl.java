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

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // ⭐️ To'g'rilangan toifa solishtirish (Repetitor, ofitsant so'zlarini IT ga qo'shib yubormaydi)
    private boolean matchesCategory(String vacancyCategory, String categoryKey) {
        if (vacancyCategory == null) return false;
        String lower = vacancyCategory.toLowerCase().trim();

        return switch (categoryKey.toLowerCase()) {
            case "it" -> lower.contains("dastur") || lower.contains("it &") || lower.equals("it") || lower.matches(".*\\b(it)\\b.*");
            case "design" -> lower.contains("dizayn") || lower.contains("design");
            case "construction" -> lower.contains("qurilish");
            case "driver" -> lower.contains("haydovchi") || lower.contains("shofyor");
            case "education" -> lower.contains("ta'lim") || lower.contains("talim") || lower.contains("repetitor") || lower.contains("tarbiyachi");
            case "trade" -> lower.contains("savdo") || lower.contains("sotuvchi") || lower.contains("kassir");
            case "cleaner" -> lower.contains("farrosh") || lower.contains("tozalash");
            case "cook" -> lower.contains("pazanda") || lower.contains("oshpaz") || lower.contains("ofitsant");
            case "security" -> lower.contains("qorovul") || lower.contains("xavfsizlik");
            case "courier" -> lower.contains("kuryer") || lower.contains("yetkazib");
            case "all" -> true;
            default -> lower.contains(categoryKey.toLowerCase());
        };
    }

    private SendMessage createHtmlMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        if (keyboard != null) message.setReplyMarkup(keyboard);
        return message;
    }

    // ============================================
    // PAGINATION KEYBOARD (Raqamlar + Sahifalash)
    // ============================================
    private InlineKeyboardMarkup buildPaginationKeyboard(String categoryKey, int currentPage, int totalPages,
                                                         List<JobVacancy> pageVacancies,
                                                         Optional<JobSeekerProfile> profileOpt) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // 1-QATOR: Raqamli tugmalar [ 1️⃣ ] [ 2️⃣ ] ...
        List<InlineKeyboardButton> numberRow = new ArrayList<>();
        String[] numberEmojis = {"1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣"};

        for (int i = 0; i < pageVacancies.size() && i < numberEmojis.length; i++) {
            JobVacancy vac = pageVacancies.get(i);
            InlineKeyboardButton numBtn = new InlineKeyboardButton();
            numBtn.setText(numberEmojis[i]);
            numBtn.setCallbackData("view_vac_" + vac.getId() + "_" + categoryKey + "_" + currentPage);
            numberRow.add(numBtn);
        }
        if (!numberRow.isEmpty()) {
            rows.add(numberRow);
        }

        // 2-QATOR: Sahifalash [ ⬅️ ] [ 1/11 ] [ ➡️ ]
        List<InlineKeyboardButton> navRow = new ArrayList<>();

        if (currentPage > 0) {
            InlineKeyboardButton prev = new InlineKeyboardButton();
            prev.setText("⬅️");
            prev.setCallbackData("vacancy_page_" + categoryKey + "_" + (currentPage - 1));
            navRow.add(prev);
        }

        InlineKeyboardButton pageInfo = new InlineKeyboardButton();
        pageInfo.setText((currentPage + 1) + "/" + totalPages);
        pageInfo.setCallbackData("noop");
        navRow.add(pageInfo);

        if (currentPage < totalPages - 1) {
            InlineKeyboardButton next = new InlineKeyboardButton();
            next.setText("➡️");
            next.setCallbackData("vacancy_page_" + categoryKey + "_" + (currentPage + 1));
            navRow.add(next);
        }
        rows.add(navRow);

        // 3-QATOR: Kategoriyalarga qaytish
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
    }

    @Override
    public SendMessage handleVacancyPagination(Long chatId, String categoryKey, int page,
                                               Optional<JobSeekerProfile> profileOpt) {
        try {
            List<JobVacancy> allVacancies = jobStore.getAllVacancies();

            // 1. Kategoriyaga mos vakansiyalarni to'g'ri filtrlash
            if (allVacancies != null && !"all".equalsIgnoreCase(categoryKey) && !categoryKey.isBlank()) {
                allVacancies = allVacancies.stream()
                        .filter(v -> matchesCategory(v.getCategory(), categoryKey))
                        .toList();
            }

            // 2. Vakansiyalar topilmagan holat
            if (allVacancies == null || allVacancies.isEmpty()) {
                String msg = keyboardService.getText(profileOpt,
                        "❌ В этой категории пока нет активных вакансий.",
                        "❌ Bu kategoriya bo'yicha hozircha faol vakansiyalar mavjud emas.",
                        "❌ No active vacancies available for this category."
                );
                return createHtmlMessage(chatId, msg, null);
            }

            // 3. Paginatsiya hisoblash
            int totalItems = allVacancies.size();
            int totalPages = (int) Math.ceil((double) totalItems / PAGE_SIZE);
            if (page < 0) page = 0;
            if (page >= totalPages) page = totalPages - 1;

            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, totalItems);
            List<JobVacancy> pageVacancies = allVacancies.subList(start, end);

            // 4. Xabar matni (HTML)
            StringBuilder result = new StringBuilder();
            result.append("📋 <b>Topilgan vakansiyalar (").append(totalItems).append("):</b>\n\n");

            int localIndex = 1;
            for (JobVacancy vacancy : pageVacancies) {
                result.append(localIndex++).append(". 📌 <b>").append(escapeHtml(vacancy.getTitle())).append("</b>\n")
                        .append("   📁 Kategoriya: ").append(escapeHtml(vacancy.getCategory() != null ? vacancy.getCategory() : "Barcha vakansiyalar")).append("\n")
                        .append("   💰 Maosh: ").append(escapeHtml(vacancy.getSalary() != null ? vacancy.getSalary() : "Kelishilgan holda")).append("\n")
                        .append("   🏢 Kompaniya: ").append(escapeHtml(vacancy.getCompanyName() != null ? vacancy.getCompanyName() : "Ko'rsatilmagan")).append("\n")
                        .append("──────────────────\n");
            }

            // 5. Klaviaturani biriktirish
            InlineKeyboardMarkup keyboard = buildPaginationKeyboard(categoryKey, page, totalPages, pageVacancies, profileOpt);

            return createHtmlMessage(chatId, result.toString(), keyboard);

        } catch (Exception e) {
            log.error("❌ Paginatsiya xatoligi: {}", e.getMessage(), e);
            String msg = keyboardService.getText(profileOpt,
                    "❌ Произошла ошибка. Пожалуйста, попробуйте еще раз.",
                    "❌ Xatolik yuz berdi. Qaytadan urinib ko'ring.",
                    "❌ An error occurred. Please try again."
            );
            return createHtmlMessage(chatId, msg, null);
        }
    }
}