package uz.imaan.jobplatform.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OsonIshScraperService {

    private final JobVacancyRepository jobVacancyRepository;

    private static final String BASE_URL = "https://osonish.uz";
    private static final String VACANCIES_URL = "https://osonish.uz/vacancies";

    // Har 1 soatda osonish.uz dan yangi e'lonlarni tekshiradi
    @Scheduled(fixedDelay = 3600000)
    public void scrapeOsonIshJobs() {
        try {
            Document doc = Jsoup.connect(VACANCIES_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();

            Elements cards = doc.select(".vacancy-card, .job-item, .card-vacancy, article, div[class*='vacancy']");

            for (Element card : cards) {
                try {
                    Element titleEl = card.selectFirst("h2, h3, a[class*='title'], .vacancy-title");
                    if (titleEl == null) continue;
                    String title = titleEl.text().trim();

                    Element linkEl = card.selectFirst("a[href*='/vacanc']");
                    String rawLink = linkEl != null ? linkEl.attr("href") : "";
                    String originalUrl = rawLink.startsWith("http") ? rawLink : BASE_URL + rawLink;

                    // Unikal ID hosil qilish (masalan: osonish_12345)
                    String externalId = "osonish_" + Math.abs(originalUrl.hashCode());

                    if (jobVacancyRepository.existsByExternalId(externalId)) {
                        continue;
                    }

                    Element companyEl = card.selectFirst(".company-name, .company, span[class*='company']");
                    String company = companyEl != null ? companyEl.text().trim() : "Oson Ish e'loni";

                    Element salaryEl = card.selectFirst(".salary, .price, span[class*='salary']");
                    String salary = salaryEl != null ? salaryEl.text().trim() : "Kelishilgan holda";

                    String cardText = card.text().toLowerCase();

                    // Soatlik / part-time e'lonlarni saralash
                    boolean isHourly = cardText.contains("soatbay") || cardText.contains("soatlik")
                            || cardText.contains("part-time") || cardText.contains("yarim stavka")
                            || cardText.contains("talabalar") || cardText.contains("smena");

                    String jobType = isHourly ? "Soatlik / Part-time" : "To'liq bandlik";

                    JobVacancy vacancy = JobVacancy.builder()
                            .title(truncate(title, 250))
                            .companyName(truncate(company, 150))
                            .category(categorizeJob(title, cardText))
                            .type(jobType)
                            .salary(truncate(salary, 100))
                            .requirements(truncate(cardText, 2000))
                            .phoneNumber("E'lon havolasi orqali")
                            .sourceUrl(originalUrl)
                            .externalId(externalId)
                            .isActive(true)
                            .notified(false)
                            .createdAt(LocalDateTime.now())
                            .build();

                    jobVacancyRepository.save(vacancy);
                    log.info("✅ OsonIsh vakansiyasi saqlandi: {}", title);

                } catch (Exception ex) {
                    log.warn("⚠️ OsonIsh kartasini saqlashda xatolik: {}", ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("❌ OsonIsh saytiga ulanishda xatolik: {}", e.getMessage());
        }
    }

    private String categorizeJob(String title, String text) {
        String combined = (title + " " + text).toLowerCase();
        if (combined.contains("sotuvchi") || combined.contains("kassir") || combined.contains("savdo")) return "Savdo / Sotuvchi";
        if (combined.contains("oshpaz") || combined.contains("pazanda") || combined.contains("somsa")) return "Pazanda / Oshpaz";
        if (combined.contains("haydovchi") || combined.contains("kuryer") || combined.contains("dostavka")) return "Haydovchi / Kuryer";
        if (combined.contains("farrosh") || combined.contains("uborka") || combined.contains("tozalash")) return "Farrosh / Tozalash";
        if (combined.contains("qurilish") || combined.contains("usta")) return "Qurilish";
        if (combined.contains("qorovul") || combined.contains("oxrana")) return "Qorovul / Xavfsizlik";
        if (combined.contains("dasturchi") || combined.contains("it") || combined.contains("java")) return "IT & Dasturlash";
        return "Boshqa ishlar";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}