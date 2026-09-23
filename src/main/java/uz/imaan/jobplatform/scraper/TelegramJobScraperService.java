package uz.imaan.jobplatform.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramJobScraperService {

    private final JobVacancyRepository jobVacancyRepository;

    private static final List<String> CHANNELS = List.of("kunlik_ishlar", "talabaga_ish","toshkent_ishlar_bormi");

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 15 */2 * * *") // Har 2 soatda tekshiradi
    public void scrapeHourlyJobs() {
        log.info("🚀 Telegram kanallaridan soatlik ishlarni tortish boshlandi...");

        for (String channel : CHANNELS) {
            String url = "https://t.me/s/" + channel;
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .timeout(10000)
                        .get();

                Elements messages = doc.select(".tgme_widget_message");

                for (Element message : messages) {
                    Element textElem = message.selectFirst(".tgme_widget_message_text");
                    Element linkElem = message.selectFirst(".tgme_widget_message_date");

                    if (textElem != null) {
                        String postText = textElem.text();
                        String postLink = linkElem != null ? linkElem.attr("href") : "";
                        String externalId = "tg_" + (linkElem != null ? linkElem.attr("href").replaceAll("[^0-9a-zA-Z_-]", "") : postText.hashCode());

                        if (jobVacancyRepository.existsByExternalId(externalId)) {
                            continue;
                        }

                        if (JobFilterUtil.isHourlyOrPartTime(postText, "")) {
                            String dynamicTitle = postText.length() > 60
                                    ? postText.substring(0, 57) + "..."
                                    : postText;

                            JobVacancy vacancy = JobVacancy.builder()
                                    .title(dynamicTitle)
                                    .category("Boshqa soatlik ishlar")
                                    .salary("E'londa ko'rsatilgan")
                                    .type("Soatlik / Part-time")
                                    .requirements(postText.length() > 300 ? postText.substring(0, 297) + "..." : postText)
                                    .phoneNumber("Kanal havolasi orqali bog'laning")
                                    .companyName("@" + channel + " (Telegram)")
                                    .externalId(externalId)
                                    .sourceUrl(postLink)
                                    .isActive(true)
                                    .notified(false)
                                    .build();

                            jobVacancyRepository.save(vacancy);
                            log.info("✅ Telegram [Soatlik] e'lon saqlandi: @{}", channel);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Telegram kanalni o'qishda xatolik (@{}): {}", channel, e.getMessage());
            }
        }
    }
}