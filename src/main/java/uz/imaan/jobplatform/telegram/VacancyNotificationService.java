package uz.imaan.jobplatform.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;

import java.util.List;

@Slf4j
@Service
public class VacancyNotificationService {

    private final JobVacancyRepository jobVacancyRepository;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final Telegram telegramBot;

    public VacancyNotificationService(JobVacancyRepository jobVacancyRepository,
                                      JobSeekerProfileRepository jobSeekerProfileRepository,
                                      @Lazy Telegram telegramBot) {
        this.jobVacancyRepository = jobVacancyRepository;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.telegramBot = telegramBot;
    }

    @Scheduled(fixedDelay = 20000)
    @Transactional
    public void notifyJobSeekersAboutNewVacancies() {
        List<JobVacancy> newVacancies = jobVacancyRepository.findPendingNotificationVacancies();

        if (newVacancies.isEmpty()) {
            return;
        }

        List<JobSeekerProfile> allSeekers = jobSeekerProfileRepository.findAll();

        for (JobVacancy vacancy : newVacancies) {
            String vacCategory = vacancy.getCategory() != null ? vacancy.getCategory().toLowerCase() : "";
            String vacTitle = vacancy.getTitle() != null ? vacancy.getTitle().toLowerCase() : "";

            int sentCount = 0;

            for (JobSeekerProfile seeker : allSeekers) {
                Long chatId = seeker.getUserId();
                if (chatId == null) continue;

                // Ish beruvchi o'ziga o'zi bildirishnoma yubormasligi uchun
                if (vacancy.getEmployerChatId() != null && vacancy.getEmployerChatId().equals(chatId)) {
                    continue;
                }

                String seekerCat = seeker.getCategory() != null ? seeker.getCategory().toLowerCase() : "";
                String seekerProf = seeker.getProfession() != null ? seeker.getProfession().toLowerCase() : "";

                boolean matches = isCategoryMatching(vacCategory, vacTitle, seekerCat, seekerProf);

                if (matches) {
                    boolean success = sendNewVacancyAlert(chatId, vacancy, seeker);
                    if (success) {
                        sentCount++;
                    }

                    // ⭐️ 429 TOO MANY REQUESTS OLDINI OLISH:
                    // Telegram limiti sekundiga ~30 ta. 40 ms tanaffus = sekundiga maksimal 25 ta xabar.
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            vacancy.setNotified(true);
            jobVacancyRepository.save(vacancy);
            if (sentCount > 0) {
                log.info("📢 Vakansiya '{}' bo'yicha {} ta nomzodga bildirishnoma yuborildi.", vacancy.getTitle(), sentCount);
            }
        }
    }

    private boolean isCategoryMatching(String vacCat, String vacTitle, String seekerCat, String seekerProf) {
        if (seekerCat.isBlank() && seekerProf.isBlank()) return false;

        if (!vacCat.isBlank() && (vacCat.equalsIgnoreCase(seekerCat) || vacCat.equalsIgnoreCase(seekerProf))) {
            return true;
        }

        boolean isItSeeker = seekerCat.contains("it") || seekerCat.contains("dastur") ||
                seekerProf.contains("it") || seekerProf.contains("dastur") || seekerProf.contains("java");
        boolean isItVacancy = vacCat.contains("it") || vacCat.contains("dastur") ||
                vacTitle.contains("java") || vacTitle.contains("developer") || vacTitle.contains("dasturchi");

        if (isItSeeker && isItVacancy) {
            return true;
        }

        return (!vacCat.isBlank() && seekerCat.contains(vacCat)) ||
                (!vacCat.isBlank() && seekerProf.contains(vacCat));
    }

    private boolean sendNewVacancyAlert(Long chatId, JobVacancy vacancy, JobSeekerProfile seeker) {
        try {
            String company = vacancy.getCompanyName() != null ? vacancy.getCompanyName() : "Ko'rsatilmagan";
            String salary = vacancy.getSalary() != null ? vacancy.getSalary() : "Kelishuv asosida";

            String messageText = String.format("""
                    🔔 <b>Sizning sohangiz bo'yicha yangi ish joylandi!</b>
                    
                    📂 <b>Soha:</b> %s
                    📌 <b>Vakansiya:</b> %s
                    🏢 <b>Ish beruvchi:</b> %s
                    💰 <b>Maosh:</b> %s
                    🕒 <b>Ish turi:</b> %s
                    """,
                    escapeHtml(vacancy.getCategory()),
                    escapeHtml(vacancy.getTitle()),
                    escapeHtml(company),
                    escapeHtml(salary),
                    escapeHtml(vacancy.getType() != null ? vacancy.getType() : "Noma'lum")
            );

            SendMessage sm = new SendMessage(chatId.toString(), messageText);
            sm.setParseMode("HTML");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            InlineKeyboardButton viewBtn = new InlineKeyboardButton();
            viewBtn.setText("👁 Vakansiyani ko'rish");
            viewBtn.setCallbackData("view_vac_" + vacancy.getId() + "_all_0");

            markup.setKeyboard(List.of(List.of(viewBtn)));
            sm.setReplyMarkup(markup);

            telegramBot.execute(sm);
            return true;

        } catch (TelegramApiException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";

            // ⭐️ 403 FORBIDDEN: Foydalanuvchi botni bloklagan bo'lsa
            if (errorMsg.contains("bot was blocked by the user") || errorMsg.contains("403")) {
                log.warn("🚫 Foydalanuvchi (chatId={}) botni bloklagan. Kelgusida xabar yuborilmaydi.", chatId);
            }
            // ⭐️ HTML Parse xatosi bo'lsa oddiy matnda yuborish
            else if (errorMsg.contains("can't parse entities")) {
                try {
                    SendMessage fallbackSm = new SendMessage(chatId.toString(),
                            "🔔 Sizning sohangiz bo'yicha yangi ish joylandi: " + vacancy.getTitle());
                    telegramBot.execute(fallbackSm);
                    return true;
                } catch (Exception ex) {
                    log.error("Fallback yuborishda ham xatolik: {}", ex.getMessage());
                }
            } else {
                log.error("Bildirishnoma yuborishda xatolik (chatId={}): {}", chatId, errorMsg);
            }
            return false;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}