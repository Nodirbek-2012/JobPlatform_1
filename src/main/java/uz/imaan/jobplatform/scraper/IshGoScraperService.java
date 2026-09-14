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

@Slf4j
@Service
@RequiredArgsConstructor
public class IshGoScraperService {

    private final JobVacancyRepository jobVacancyRepository;
    private static final String ISHGO_URL = "https://ishgo.uz/";

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 */4 * * *")
    public void scrapeIshGoJobs() {
        log.info("🚀 IshGo.uz dan vakansiyalarni tortish boshlandi...");

        try {
            Document doc = Jsoup.connect(ISHGO_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .timeout(12000)
                    .get();

            // IshGo saytidagi vakansiya havolalari va kartalari
            Elements cards = doc.select("a[href*='/vacancy/'], a[href*='/job/'], div[class*='vacancy'], div[class*='card']");

            for (Element card : cards) {
                try {
                    String href = card.is("a") ? card.attr("href") : (card.selectFirst("a") != null ? card.selectFirst("a").attr("href") : "");
                    if (href.isBlank()) continue;

                    String fullUrl = href.startsWith("http") ? href : "https://ishgo.uz" + href;
                    String externalId = "ishgo_" + href.replaceAll("[^0-9a-zA-Z_-]", "");

                    if (externalId.length() < 8 || jobVacancyRepository.existsByExternalId(externalId)) {
                        continue;
                    }

                    Element titleElem = card.selectFirst("h3, h4, h5, [class*='title']");
                    String title = titleElem != null ? titleElem.text().trim() : "Vakansiya";
                    if (title.length() < 3) continue;

                    Element compElem = card.selectFirst("[class*='company'], [class*='employer']");
                    String company = compElem != null ? compElem.text().trim() + " (IshGo)" : "IshGo.uz e'loni";

                    Element salaryElem = card.selectFirst("[class*='salary'], [class*='price']");
                    String salary = salaryElem != null ? salaryElem.text().trim() : "Kelishilgan holda";

                    JobVacancy vacancy = JobVacancy.builder()
                            .title(title)
                            .category(detectCategory(title))
                            .salary(salary)
                            .type("To'liq bandlik")
                            .requirements("To'liq ma'lumot bilan IshGo saytida tanishing.")
                            .phoneNumber("Havola orqali bog'laning")
                            .companyName(company)
                            .externalId(externalId)
                            .sourceUrl(fullUrl)
                            .isActive(true)
                            .build();

                    jobVacancyRepository.save(vacancy);
                    log.info("✅ IshGo vakansiyasi qo'shildi: {}", title);

                } catch (Exception ex) {
                    log.debug("⚠️ IshGo kartasini tahlil qilishda eslatma: {}", ex.getMessage());
                }
            }

        } catch (Exception e) {
            log.warn("⚠️ IshGo.uz saytiga ulanishda ogohlantirish: {}", e.getMessage());
        }
    }

    private String detectCategory(String text) {
        String lower = text.toLowerCase();

        if (lower.contains("ofitsiant") || lower.contains("ofitsant") || lower.contains("oshpaz") || lower.contains("barmen")) {
            return "Pazanda / Oshpaz";
        }
        if (lower.contains("dasturchi") || lower.contains("developer") || lower.contains("java") ||
                lower.contains("python") || lower.matches(".*\\b(it)\\b.*")) {
            return "IT & Dasturlash";
        }
        if (lower.contains("haydovchi") || lower.contains("shofyor")) {
            return "Haydovchi / Kuryer";
        }
        if (lower.contains("kuryer") || lower.contains("dostavka")) {
            return "Kuryer / Yetkazib berish";
        }
        if (lower.contains("sotuvchi") || lower.contains("kassir") || lower.contains("savdo")) {
            return "Savdo / Sotuvchi";
        }
        if (lower.contains("farrosh") || lower.contains("tozalash")) {
            return "Farrosh / Tozalash";
        }
        if (lower.contains("qorovul") || lower.contains("oxrana")) {
            return "Qorovul / Xavfsizlik";
        }
        if (lower.contains("qurilish") || lower.contains("usta")) {
            return "Qurilish";
        }
        if (lower.contains("o'qituvchi") || lower.contains("repetitor") || lower.contains("tarbiyachi")) {
            return "Ta'lim / Repetitor";
        }

        return "Barcha vakansiyalar";
    }
}