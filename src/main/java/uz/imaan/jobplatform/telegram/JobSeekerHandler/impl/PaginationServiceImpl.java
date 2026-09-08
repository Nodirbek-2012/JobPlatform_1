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
            case "it" -> "IT & Dasturlash";
            case "design" -> "Dizayn";
            case "construction" -> "Qurilish";
            case "driver" -> "Haydovchi";
            case "education" -> "Ta'lim";
            case "trade" -> "Savdo";
            case "cleaner" -> "Farrosh";
            case "cook" -> "Oshpaz";
            case "security" -> "Qorovul";
            case "courier" -> "Kuryer";
            case "all" -> "Barcha";
            default -> key;
        };
    }

    private SendMessage createMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML"); // Markdown o'rniga HTML xavfsizroq va qotmaydi
        if (keyboard != null) message.setReplyMarkup(keyboard);
        return message;
    }


    // ============================================
    // PAGINATION KEYBOARD (Raqamli tugmalar bilan)
    // ============================================
    private InlineKeyboardMarkup buildPaginationKeyboard(String categoryKey, int currentPage, int totalPages,
                                                         List<JobVacancy> pageVacancies, int start,
                                                         Optional<JobSeekerProfile> profileOpt) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // 1. Har bir vakansiyani tanlash uchun raqamli tugmalar: [ 1️⃣ ] [ 2️⃣ ] [ 3️⃣ ] ...
        List<InlineKeyboardButton> numberRow = new ArrayList<>();
        int index = start + 1;
        for (JobVacancy vac : pageVacancies) {
            InlineKeyboardButton numBtn = new InlineKeyboardButton();
            numBtn.setText(getEmojiNumber(index));
            // callback: view_vac_{id}_{kategoriya}_{sahifa}
            numBtn.setCallbackData("view_vac_" + vac.getId() + "_" + categoryKey + "_" + currentPage);
            numberRow.add(numBtn);
            index++;
        }
        rows.add(numberRow);

        // 2. Sahifalash (Oldingi / Sahifa / Keyingi)
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (currentPage > 0) {
            InlineKeyboardButton prev = new InlineKeyboardButton();
            prev.setText("⬅️ Oldingi");
            prev.setCallbackData("vacancy_page_" + categoryKey + "_" + (currentPage - 1));
            navRow.add(prev);
        }

        InlineKeyboardButton pageInfo = new InlineKeyboardButton();
        pageInfo.setText((currentPage + 1) + " / " + totalPages);
        pageInfo.setCallbackData("noop");
        navRow.add(pageInfo);

        if (currentPage < totalPages - 1) {
            InlineKeyboardButton next = new InlineKeyboardButton();
            next.setText("Keyingi ➡️");
            next.setCallbackData("vacancy_page_" + categoryKey + "_" + (currentPage + 1));
            navRow.add(next);
        }
        rows.add(navRow);

        // 3. Kategoriyalarga qaytish tugmasi
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton back = new InlineKeyboardButton();
        String backText = isRussian(profileOpt) ? "📂 К категориям" : (isEnglish(profileOpt) ? "📂 To categories" : "📂 Kategoriyalarga qaytish");
        back.setText(backText);
        back.setCallbackData("back_to_categories");
        backRow.add(back);
        rows.add(backRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    // Raqamlar uchun emojilar
    private String getEmojiNumber(int num) {
        return switch (num) {
            case 1 -> "1️⃣";
            case 2 -> "2️⃣";
            case 3 -> "3️⃣";
            case 4 -> "4️⃣";
            case 5 -> "5️⃣";
            case 6 -> "6️⃣";
            case 7 -> "7️⃣";
            case 8 -> "8️⃣";
            case 9 -> "9️⃣";
            case 10 -> "🔟";
            default -> String.valueOf(num);
        };
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
            List<JobVacancy> rawVacancies = jobStore.getAllVacancies();
            if (rawVacancies == null) {
                rawVacancies = List.of();
            }

            // 1. Vakansiyalarni filtrlash (xavfsiz va 2 tomonlama qidiruv)
            List<JobVacancy> allVacancies;
            if ("all".equalsIgnoreCase(categoryKey)) {
                allVacancies = rawVacancies;
            } else {
                String cleanName = getCategoryName(categoryKey).toLowerCase();
                String keyLower = categoryKey.toLowerCase();

                allVacancies = rawVacancies.stream()
                        .filter(v -> {
                            if (v.getCategory() == null) return false;
                            String vacCat = v.getCategory().toLowerCase();
                            return vacCat.contains(cleanName) ||
                                    cleanName.contains(vacCat) ||
                                    vacCat.contains(keyLower);
                        })
                        .toList();
            }

            // 2. Vakansiyalar mavjudligini tekshirish
            if (allVacancies.isEmpty()) {
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

            // 5. Xabar matnini tayyorlash (HTML formatida)
            StringBuilder result = new StringBuilder();
            result.append("📋 <b>Topilgan vakansiyalar (").append(allVacancies.size()).append("):</b>\n\n");

            int globalIndex = start + 1;
            for (JobVacancy vacancy : pageVacancies) {
                String title = vacancy.getTitle() != null ? vacancy.getTitle() : "Nomsiz e'lon";
                String category = vacancy.getCategory() != null ? vacancy.getCategory() : "-";
                String salary = vacancy.getSalary() != null ? vacancy.getSalary() : "Kelishuv asosida";
                String company = vacancy.getCompanyName() != null ? vacancy.getCompanyName() : "Ko'rsatilmagan";

                result.append(globalIndex++).append(". 📌 <b>").append(title).append("</b>\n");
                result.append("   📂 Kategoriya: ").append(category).append("\n");
                result.append("   💰 Maosh: ").append(salary).append("\n");
                result.append("   🏢 Kompaniya: ").append(company).append("\n");
                result.append("───────────────\n");
            }

            // 6. Keyboard
            // 6. Keyboard (pageVacancies va start uzatiladi)
            InlineKeyboardMarkup keyboard = buildPaginationKeyboard(categoryKey, page, totalPages, pageVacancies, start, profileOpt);

            // 7. Xabar qaytarish
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