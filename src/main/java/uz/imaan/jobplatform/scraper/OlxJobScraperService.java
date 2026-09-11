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
public class OlxJobScraperService {

    private final JobVacancyRepository jobVacancyRepository;

    // OLX Toshkentdagi bo'sh ish o'rinlari sahifasi
    private static final String OLX_JOB_URL = "https://www.olx.uz/oz/rabota/";

    /**
     * Har 2 soatda avtomatik yangi e'lonlarni tekshirib bazaga soladi
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 */2 * * *")
    public void scrapeOlxJobs() {
        log.info("🚀 OLX dan vakansiyalarni tortish boshlandi...");

        try {
            // 1. Sahifani brauzer nomidan yuklab olish
            Document doc = Jsoup.connect(OLX_JOB_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            // 2. Har bir e'lon kartasini tanlab olish
            Elements jobCards = doc.select("[data-cy='l-card']");

            for (Element card : jobCards) {
                try {
                    // E'lon havolasi va ID si
                    Element linkElement = card.selectFirst("a");
                    if (linkElement == null) continue;

                    String jobUrl = "https://www.olx.uz" + linkElement.attr("href");
                    String externalId = card.attr("id"); // OLX e'lon ID si

                    // Agar bu e'lon bazamizda avvaldan bor bo'lsa, o'tkazib yuboramiz
                    if (externalId == null || externalId.isBlank() || jobVacancyRepository.existsByExternalId(externalId)) {
                        continue;
                    }

                    // Sarlavha
                    Element titleElement = card.selectFirst("h6, h4");
                    String title = titleElement != null ? titleElement.text().trim() : "Vakansiya";

                    // Maosh
                    Element priceElement = card.selectFirst("[data-testid='ad-price']");
                    String salary = priceElement != null ? priceElement.text().trim() : "Kelishilgan holda";

                    // Manzil (shahar/tuman)
                    Element locationElement = card.selectFirst("[data-testid='location-date']");
                    String location = locationElement != null ? locationElement.text().split("-")[0].trim() : "Toshkent";

                    // Yangi vakansiya ob'ektini yasash
                    JobVacancy vacancy = JobVacancy.builder()
                            .title(title)
                            .category(detectCategory(title)) // Sarlavhadan kategoriyani taxmin qilish
                            .salary(salary)
                            .type("To'liq bandlik")
                            .requirements("To'liq ma'lumot OLX e'lonida: " + jobUrl)
                            .phoneNumber("E'lon havolasi orqali ko'ring")
                            .companyName("OLX e'loni (" + location + ")")
                            .externalId(externalId)
                            .sourceUrl(jobUrl)
                            .isActive(true)
                            .build();

                    jobVacancyRepository.save(vacancy);
                    log.info("✅ Yangi vakansiya qo'shildi: {} | ID: {}", title, externalId);

                } catch (Exception e) {
                    log.warn("⚠️ E'lonni parse qilishda xatolik: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ OLX ga ulanishda xatolik: {}", e.getMessage());
        }
    }

    /**
     * Sarlavhadagi so'zlarga qarab kategoriyani avtomatik belgilash
     */
    private String detectCategory(String title) {
        String lower = title.toLowerCase();
        if (lower.contains("dasturchi") || lower.contains("it") || lower.contains("java") || lower.contains("python")) {
            return "IT & Dasturlash";
        }
        if (lower.contains("haydovchi") || lower.contains("taxi") || lower.contains("shofyor")) {
            return "Haydovchi / Kuryer";
        }
        if (lower.contains("kuryer") || lower.contains("yetkazib")) {
            return "Kuryer / Yetkazib berish";
        }
        if (lower.contains("sotuvchi") || lower.contains("kassir") || lower.contains("savdo") || lower.contains("konsultant")) {
            return "Savdo / Sotuvchi";
        }
        if (lower.contains("oshpaz") || lower.contains("pazanda") || lower.contains("somsa") || lower.contains("shashlik")) {
            return "Pazanda / Oshpaz";
        }
        if (lower.contains("farrosh") || lower.contains("tozalash") || lower.contains("uborsh")) {
            return "Farrosh / Tozalash";
        }
        if (lower.contains("qorovul") || lower.contains("oxrana") || lower.contains("xavfsizlik")) {
            return "Qorovul / Xavfsizlik";
        }
        if (lower.contains("qurilish") || lower.contains("usta") || lower.contains("santexnik") || lower.contains("montaj")) {
            return "Qurilish";
        }
        return "Barcha vakansiyalar";
    }
}