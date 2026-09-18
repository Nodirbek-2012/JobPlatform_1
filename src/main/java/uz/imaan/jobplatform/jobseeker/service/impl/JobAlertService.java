package uz.imaan.jobplatform.jobseeker.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.telegram.Telegram;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobAlertService {

    private final JobVacancyRepository jobVacancyRepository;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final ApplicationContext applicationContext;

    // Har 5 daqiqada yangi vakansiyalarni tekshiradi va mos foydalanuvchilarga yuboradi
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void processNewJobAlerts() {
        List<JobVacancy> unnotifiedVacancies = jobVacancyRepository.findByNotifiedFalseOrNotifiedIsNull();

        if (unnotifiedVacancies.isEmpty()) {
            return;
        }

        log.info("🔔 Bildirishnoma uchun {} ta yangi vakansiya topildi.", unnotifiedVacancies.size());
        Telegram bot = applicationContext.getBean(Telegram.class);

        for (JobVacancy vacancy : unnotifiedVacancies) {
            sendAlertForVacancy(vacancy, bot);
            vacancy.setNotified(true);
            jobVacancyRepository.save(vacancy);
        }
    }

    private void sendAlertForVacancy(JobVacancy vacancy, Telegram bot) {
        String category = vacancy.getCategory();
        if (category == null || category.isBlank()) {
            return;
        }

        // Kategoriya yoki kasbi mos keluvchi nomzodlarni topish
        List<JobSeekerProfile> matchingProfiles =
                jobSeekerProfileRepository.findByCategoryIgnoreCaseOrProfessionIgnoreCase(category, category);

        for (JobSeekerProfile profile : matchingProfiles) {
            Long chatId = profile.getUserId();
            if (chatId == null) continue;

            String lang = profile.getLanguage() != null ? profile.getLanguage() : "uz";
            String alertText = buildAlertMessage(vacancy, lang);
            InlineKeyboardMarkup keyboard = buildAlertKeyboard(vacancy.getId(), lang);

            try {
                SendMessage msg = new SendMessage(chatId.toString(), alertText);
                msg.setParseMode("HTML");
                msg.setReplyMarkup(keyboard);
                bot.executeMessage(msg);

                // Telegram limitiga (spam limit) tushmaslik uchun qisqa pauza (35ms)
                Thread.sleep(35);
            } catch (Exception e) {
                log.warn("⚠️ ChatId: {} ga bildirishnoma yuborilmadi (bot bloklangan bo'lishi mumkin): {}", chatId, e.getMessage());
            }
        }
    }

    private String buildAlertMessage(JobVacancy vacancy, String lang) {
        String title = escapeHtml(vacancy.getTitle());
        String company = escapeHtml(vacancy.getCompanyName() != null ? vacancy.getCompanyName() : "Ko'rsatilmagan");
        String salary = escapeHtml(vacancy.getSalary() != null ? vacancy.getSalary() : "Kelishiladi");
        String workType = escapeHtml(vacancy.getType() != null ? vacancy.getType() : "To'liq bandlik");

        return switch (lang) {
            case "ru" -> String.format("""
                    🔔 <b>Новая вакансия по вашей профессии!</b>
                    
                    📌 <b>%s</b>
                    🏢 <b>Компания:</b> %s
                    💰 <b>Зарплата:</b> %s
                    🕒 <b>Занятость:</b> %s
                    
                    <i>Нажмите кнопку ниже, чтобы посмотреть подробнее!</i>
                    """, title, company, salary, workType);

            case "en" -> String.format("""
                    🔔 <b>New job matching your profession!</b>
                    
                    📌 <b>%s</b>
                    🏢 <b>Company:</b> %s
                    💰 <b>Salary:</b> %s
                    🕒 <b>Type:</b> %s
                    
                    <i>Click the button below to view details!</i>
                    """, title, company, salary, workType);

            default -> String.format("""
                    🔔 <b>Sizning kasbingiz bo'yicha yangi vakansiya!</b>
                    
                    📌 <b>%s</b>
                    🏢 <b>Kompaniya:</b> %s
                    💰 <b>Maosh:</b> %s
                    🕒 <b>Bandlik:</b> %s
                    
                    <i>Batafsil ma'lumot olish uchun pastdagi tugmani bosing!</i>
                    """, title, company, salary, workType);
        };
    }

    private InlineKeyboardMarkup buildAlertKeyboard(Long vacancyId, String lang) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String viewLabel = switch (lang) {
            case "ru" -> "👁 Подробнее";
            case "en" -> "👁 View details";
            default -> "👁 Batafsil ko'rish";
        };

        String favLabel = switch (lang) {
            case "ru" -> "⭐ В избранное";
            case "en" -> "⭐ Save";
            default -> "⭐ Saqlash";
        };

        // 1. To'g'ridan-to'g'ri vakansiya kartasini ochish tugmasi
        InlineKeyboardButton viewBtn = new InlineKeyboardButton();
        viewBtn.setText(viewLabel);
        viewBtn.setCallbackData("view_vac_" + vacancyId + "_all_0");

        // 2. Bir bosishda saralanganlarga saqlash
        InlineKeyboardButton favBtn = new InlineKeyboardButton();
        favBtn.setText(favLabel);
        favBtn.setCallbackData("fav_toggle_" + vacancyId + "_all_0");

        rows.add(List.of(viewBtn, favBtn));
        markup.setKeyboard(rows);
        return markup;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}