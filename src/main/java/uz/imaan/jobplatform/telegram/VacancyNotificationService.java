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

        log.info("🔔 Bildirishnoma kutayotgan yangi vakansiyalar: {} ta", newVacancies.size());
        List<JobSeekerProfile> allSeekers = jobSeekerProfileRepository.findAll();
        log.info("👥 Bazadagi jami ish izlovchilar soni: {} ta", allSeekers.size());

        for (JobVacancy vacancy : newVacancies) {
            String vacCategory = vacancy.getCategory() != null ? vacancy.getCategory() : "";
            String vacTitle = vacancy.getTitle() != null ? vacancy.getTitle() : "";

            int sentCount = 0;

            for (JobSeekerProfile seeker : allSeekers) {
                Long chatId = seeker.getUserId();
                if (chatId == null) continue;

                // Test paytida o'zingizga ham borishi uchun bu shart vaqtincha tekshiriladi
                if (vacancy.getEmployerChatId() != null && vacancy.getEmployerChatId().equals(chatId)) {
                    log.info("ℹ️ Vakansiya egasi o'zi bo'lgani uchun o'tkazib yuborildi (chatId={})", chatId);
                    continue;
                }

                String seekerCat = seeker.getCategory() != null ? seeker.getCategory() : "";
                String seekerProf = seeker.getProfession() != null ? seeker.getProfession() : "";

                boolean matches = isCategoryMatching(vacCategory, vacTitle, seekerCat, seekerProf);

                log.info("🔎 Taqqoslash [chatId={}]: Vakansiya(kat='{}', sarlavha='{}') <---> Nomzod(kat='{}', kasb='{}') => Natija: {}",
                        chatId, vacCategory, vacTitle, seekerCat, seekerProf, matches);

                if (matches) {
                    boolean success = sendNewVacancyAlert(chatId, vacancy, seeker);
                    if (success) {
                        sentCount++;
                    }

                    // Telegram Rate limit (429 xatosi) oldini olish uchun
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            vacancy.setNotified(true);
            jobVacancyRepository.save(vacancy);
            log.info("📢 Vakansiya '{}' bo'yicha jami {} ta nomzodga bildirishnoma yuborildi.", vacancy.getTitle(), sentCount);
        }
    }

    private boolean isCategoryMatching(String vacCat, String vacTitle, String seekerCat, String seekerProf) {
        String vCat = (vacCat != null) ? vacCat.toLowerCase().trim() : "";
        String vTitle = (vacTitle != null) ? vacTitle.toLowerCase().trim() : "";
        String sCat = (seekerCat != null) ? seekerCat.toLowerCase().trim() : "";
        String sProf = (seekerProf != null) ? seekerProf.toLowerCase().trim() : "";

        if (sCat.isBlank() && sProf.isBlank()) {
            return false;
        }

        // 1. To'g'ridan-to'g'ri tenglik yoki o'z ichiga olish
        if (!vCat.isBlank() && (vCat.contains(sCat) || sCat.contains(vCat) || vCat.contains(sProf) || sProf.contains(vCat))) {
            return true;
        }

        // 2. IT & Dasturlash
        boolean isItSeeker = sCat.contains("it") || sCat.contains("dastur") || sProf.contains("it") || sProf.contains("dastur");
        boolean isItVacancy = vCat.contains("it") || vCat.contains("dastur") || vTitle.contains("java") || vTitle.contains("developer") || vTitle.contains("dasturchi");
        if (isItSeeker && isItVacancy) return true;

        // 3. Savdo / Sotuvchi
        boolean isTradeSeeker = sCat.contains("savdo") || sCat.contains("sotuvchi") || sProf.contains("savdo") || sProf.contains("sotuvchi");
        boolean isTradeVacancy = vCat.contains("savdo") || vCat.contains("sotuvchi") || vCat.contains("trade") || vTitle.contains("sotuvchi") || vTitle.contains("kassir");
        if (isTradeSeeker && isTradeVacancy) return true;

        // 4. Haydovchi / Kuryer
        boolean isDriverSeeker = sCat.contains("haydovchi") || sCat.contains("kuryer") || sProf.contains("haydovchi") || sProf.contains("kuryer");
        boolean isDriverVacancy = vCat.contains("haydovchi") || vCat.contains("kuryer") || vCat.contains("driver") || vCat.contains("courier") || vTitle.contains("haydovchi") || vTitle.contains("dostavka");
        if (isDriverSeeker && isDriverVacancy) return true;

        // 5. Pazanda / Oshpaz
        boolean isCookSeeker = sCat.contains("pazanda") || sCat.contains("oshpaz") || sProf.contains("oshpaz") || sProf.contains("pazanda");
        boolean isCookVacancy = vCat.contains("pazanda") || vCat.contains("oshpaz") || vCat.contains("cook") || vTitle.contains("oshpaz") || vTitle.contains("somsa");
        if (isCookSeeker && isCookVacancy) return true;

        // 6. Qurilish
        boolean isConstSeeker = sCat.contains("qurilish") || sProf.contains("qurilish") || sProf.contains("usta");
        boolean isConstVacancy = vCat.contains("qurilish") || vCat.contains("construction") || vTitle.contains("usta") || vTitle.contains("payvand");
        if (isConstSeeker && isConstVacancy) return true;

        // 7. Qorovul / Xavfsizlik
        boolean isSecuritySeeker = sCat.contains("qorovul") || sCat.contains("xavfsizlik") || sProf.contains("qorovul");
        boolean isSecurityVacancy = vCat.contains("qorovul") || vCat.contains("xavfsizlik") || vCat.contains("security") || vTitle.contains("oxrana");
        if (isSecuritySeeker && isSecurityVacancy) return true;

        // 8. Farrosh / Tozalash
        boolean isCleanerSeeker = sCat.contains("farrosh") || sCat.contains("tozalash") || sProf.contains("farrosh");
        boolean isCleanerVacancy = vCat.contains("farrosh") || vCat.contains("cleaner") || vTitle.contains("uborka") || vTitle.contains("tozalovchi");
        if (isCleanerSeeker && isCleanerVacancy) return true;

        return false;
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

            if (errorMsg.contains("bot was blocked by the user") || errorMsg.contains("403")) {
                log.warn("🚫 Foydalanuvchi (chatId={}) botni bloklagan.", chatId);
            } else if (errorMsg.contains("can't parse entities")) {
                try {
                    SendMessage fallbackSm = new SendMessage(chatId.toString(),
                            "🔔 Sizning sohangiz bo'yicha yangi ish joylandi: " + vacancy.getTitle());
                    telegramBot.execute(fallbackSm);
                    return true;
                } catch (Exception ex) {
                    log.error("Fallback yuborishda xatolik: {}", ex.getMessage());
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