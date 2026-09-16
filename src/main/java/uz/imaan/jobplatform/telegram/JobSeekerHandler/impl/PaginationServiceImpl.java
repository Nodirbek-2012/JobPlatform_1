package uz.imaan.jobplatform.telegram.JobSeekerHandler.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.imaan.jobplatform.employer.job.JobStore;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;
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
    private final JobVacancyRepository jobVacancyRepository;


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

    // ============================================
    // ⭐️ KALIT SO'Z VA MAOSH FILTRI BILAN QIDIRUV
    // ============================================
    @Override
    public SendMessage handleSearchPagination(Long chatId, String query, String salaryFilter, int page, Optional<JobSeekerProfile> profileOpt) {
        try {
            // 1. Bazadan kalit so'zga mos vakansiyalarni olish
            List<JobVacancy> list = jobVacancyRepository.searchByKeyword(query);

            // 2. Maosh bo'yicha filtrlash
            String currentFilter = (salaryFilter == null || salaryFilter.isBlank()) ? "all" : salaryFilter;
            list = list.stream()
                    .filter(v -> matchesSalary(v.getSalary(), currentFilter))
                    .toList();

            if (list.isEmpty()) {
                String emptyMsg = String.format(
                        "🔍 <b>«%s»</b> so'rovi bo'yicha tanlangan maosh filtrida vakansiyalar topilmadi.\n\n" +
                                "<i>Boshqa kalit so'z kiritib ko'ring yoki maosh filtrini «Barchasi»ga o'tkazing.</i>",
                        escapeHtml(query)
                );
                return createHtmlMessage(chatId, emptyMsg, buildSalaryFilterKeyboard(query, currentFilter, 0, 0, List.of()));
            }

            // 3. Paginatsiya hisoblash
            int totalItems = list.size();
            int totalPages = (int) Math.ceil((double) totalItems / PAGE_SIZE);
            if (page < 0) page = 0;
            if (page >= totalPages) page = totalPages - 1;

            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, totalItems);
            List<JobVacancy> pageVacancies = list.subList(start, end);

            // 4. Xabar matni
            StringBuilder sb = new StringBuilder();
            sb.append("🔍 <b>Qidiruv natijalari: «").append(escapeHtml(query)).append("»</b>\n")
                    .append("📊 Jami topildi: <b>").append(totalItems).append(" ta</b> | Sahifa: <b>").append(page + 1).append("/").append(totalPages).append("</b>\n\n");

            int idx = 1;
            for (JobVacancy vac : pageVacancies) {
                sb.append(idx++).append(". 📌 <b>").append(escapeHtml(vac.getTitle())).append("</b>\n")
                        .append("   💰 <b>Maosh:</b> ").append(escapeHtml(vac.getSalary() != null ? vac.getSalary() : "Kelishilgan")).append("\n")
                        .append("   🏢 <b>Kompaniya:</b> ").append(escapeHtml(vac.getCompanyName() != null ? vac.getCompanyName() : "Ko'rsatilmagan")).append("\n")
                        .append("   📁 <b>Toifa:</b> ").append(escapeHtml(vac.getCategory())).append("\n")
                        .append("──────────────────\n");
            }

            // 5. Tugmalar (Maosh filtri + Sahifalash)
            InlineKeyboardMarkup markup = buildSalaryFilterKeyboard(query, currentFilter, page, totalPages, pageVacancies);
            return createHtmlMessage(chatId, sb.toString(), markup);

        } catch (Exception e) {
            log.error("❌ Qidiruv xatoligi: {}", e.getMessage(), e);
            return createHtmlMessage(chatId, "❌ Qidiruvda xatolik yuz berdi. Qaytadan urinib ko'ring.", null);
        }
    }

    // Maoshni matndan sonlarga ajratib filtrga solishtirish
    private boolean matchesSalary(String salaryText, String filter) {
        if (filter.equals("all")) return true;
        if (salaryText == null) return filter.equals("agreed");

        String lower = salaryText.toLowerCase();

        if (filter.equals("agreed")) {
            return lower.contains("kelishilgan") || lower.contains("dogovor") || lower.contains("ko'rsatilmagan");
        }

        long amount = extractSalaryAmount(lower);
        if (amount == 0) {
            return filter.equals("agreed");
        }

        if (filter.equals("range_3_6")) {
            return amount >= 3_000_000 && amount <= 6_000_000;
        }
        if (filter.equals("above_6")) {
            return amount > 6_000_000;
        }
        return true;
    }

    private long extractSalaryAmount(String text) {
        try {
            // Agar "mln" bo'lsa (masalan: 4 mln yoki 4.5 mln)
            if (text.contains("mln")) {
                String clean = text.replaceAll("[^0-9.]", " ").trim();
                String[] parts = clean.split("\\s+");
                if (parts.length > 0 && !parts[0].isBlank()) {
                    double mln = Double.parseDouble(parts[0]);
                    return (long) (mln * 1_000_000);
                }
            }
            // Agar oddiy raqamlar bo'lsa (masalan: 5 000 000 yoki 4500000)
            String digits = text.replaceAll("[^0-9]", "");
            if (digits.length() >= 6) { // kamida 100 000 so'm
                return Long.parseLong(digits.substring(0, Math.min(digits.length(), 8)));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // Qidiruv klaviaturasi (Maosh filtri va Raqamlar)
    private InlineKeyboardMarkup buildSalaryFilterKeyboard(String query, String currentFilter, int page, int totalPages, List<JobVacancy> pageVacancies) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // 1-Qator: Maosh filtrlari tugmalari
        List<InlineKeyboardButton> filterRow1 = new ArrayList<>();
        filterRow1.add(InlineKeyboardButton.builder()
                .text((currentFilter.equals("all") ? "✅ " : "") + "Hammasi")
                .callbackData("sq_" + query + "_all_0").build());
        filterRow1.add(InlineKeyboardButton.builder()
                .text((currentFilter.equals("agreed") ? "✅ " : "") + "Kelishilgan")
                .callbackData("sq_" + query + "_agreed_0").build());
        rows.add(filterRow1);

        List<InlineKeyboardButton> filterRow2 = new ArrayList<>();
        filterRow2.add(InlineKeyboardButton.builder()
                .text((currentFilter.equals("range_3_6") ? "✅ " : "") + "3-6 mln")
                .callbackData("sq_" + query + "_range_3_6_0").build());
        filterRow2.add(InlineKeyboardButton.builder()
                .text((currentFilter.equals("above_6") ? "✅ " : "") + "6 mln+")
                .callbackData("sq_" + query + "_above_6_0").build());
        rows.add(filterRow2);

        // 2-Qator: Vakansiyalarni ochish raqamlari [ 1️⃣ ] [ 2️⃣ ] ...
        if (!pageVacancies.isEmpty()) {
            List<InlineKeyboardButton> numRow = new ArrayList<>();
            String[] emojis = {"1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣"};
            for (int i = 0; i < pageVacancies.size() && i < emojis.length; i++) {
                numRow.add(InlineKeyboardButton.builder()
                        .text(emojis[i])
                        .callbackData("view_vac_" + pageVacancies.get(i).getId() + "_search_0")
                        .build());
            }
            rows.add(numRow);
        }

        // 3-Qator: Sahifalash [ ⬅️ ] [ 1/3 ] [ ➡️ ]
        if (totalPages > 1) {
            List<InlineKeyboardButton> navRow = new ArrayList<>();
            if (page > 0) {
                navRow.add(InlineKeyboardButton.builder().text("⬅️").callbackData("sq_" + query + "_" + currentFilter + "_" + (page - 1)).build());
            }
            navRow.add(InlineKeyboardButton.builder().text((page + 1) + "/" + totalPages).callbackData("noop").build());
            if (page < totalPages - 1) {
                navRow.add(InlineKeyboardButton.builder().text("➡️").callbackData("sq_" + query + "_" + currentFilter + "_" + (page + 1)).build());
            }
            rows.add(navRow);
        }

        // 4-Qator: Orqaga
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(InlineKeyboardButton.builder().text("⬅️ Toifalarga qaytish").callbackData("back_to_categories").build());
        rows.add(backRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }
}