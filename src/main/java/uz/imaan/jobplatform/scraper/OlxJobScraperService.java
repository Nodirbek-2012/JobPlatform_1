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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OlxJobScraperService {

    private final JobVacancyRepository jobVacancyRepository;

    private static final String OLX_JOB_URL = "https://www.olx.uz/oz/rabota/";
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+?998[\\s-]?)?\\(?\\d{2}\\)?[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}");

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 */2 * * *")
    public void scrapeOlxJobs() {
        log.info("🚀 OLX dan vakansiyalarni to'liq ma'lumotlari bilan tortish boshlandi...");

        try {
            Document doc = Jsoup.connect(OLX_JOB_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            Elements jobCards = doc.select("[data-cy='l-card']");

            for (Element card : jobCards) {
                try {
                    Element linkElement = card.selectFirst("a");
                    if (linkElement == null) continue;

                    String href = linkElement.attr("href");
                    String jobUrl = href.startsWith("http") ? href : "https://www.olx.uz" + href;
                    String externalId = card.attr("id");

                    if (externalId == null || externalId.isBlank() || jobVacancyRepository.existsByExternalId(externalId)) {
                        continue;
                    }

                    // Sarlavha
                    Element titleElement = card.selectFirst("h6, h4");
                    String title = titleElement != null ? titleElement.text().trim() : "Vakansiya";

                    // Maosh
                    Element priceElement = card.selectFirst("[data-testid='ad-price']");
                    String salary = priceElement != null ? priceElement.text().trim() : "Kelishilgan holda";

                    // Shahar
                    Element locationElement = card.selectFirst("[data-testid='location-date']");
                    String location = locationElement != null ? locationElement.text().split("-")[0].trim() : "Toshkent";

                    // ⭐️ E'lonning ichki sahifasiga kirib haqiqiy ma'lumot va telefonni olish
                    String fullDescription = "Batafsil ma'lumot ko'rsatilmagan";
                    String extractedPhone = "E'lon havolasi orqali bog'laning";

                    try {
                        Document detailDoc = Jsoup.connect(jobUrl)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                .timeout(7000)
                                .get();

                        Element descElement = detailDoc.selectFirst("[data-cy='ad_description']");
                        if (descElement != null) {
                            fullDescription = descElement.text().trim();

                            // Matn ichidan telefon raqamni qidirish
                            Matcher matcher = PHONE_PATTERN.matcher(fullDescription);
                            if (matcher.find()) {
                                extractedPhone = matcher.group();
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("⚠️ Ichki sahifani ochishda xatolik ({}): {}", jobUrl, ex.getMessage());
                    }

                    // Matn juda uzun bo'lib Telegram limitidan (1024 belgi) oshib ketmasligi uchun qisqartirish
                    if (fullDescription.length() > 300) {
                        fullDescription = fullDescription.substring(0, 297) + "...";
                    }

                    JobVacancy vacancy = JobVacancy.builder()
                            .title(title)
                            .category(detectCategory(title, fullDescription))
                            .salary(salary)
                            .type("To'liq bandlik")
                            .requirements(fullDescription)
                            .phoneNumber(extractedPhone)
                            .companyName("OLX e'loni (" + location + ")")
                            .externalId(externalId)
                            .sourceUrl(jobUrl)
                            .isActive(true)
                            .build();

                    jobVacancyRepository.save(vacancy);
                    log.info("✅ Yangi vakansiya qo'shildi: {} | Tel: {}", title, extractedPhone);

                    // OLX serveri so'rovlar ko'pligidan bloklamasligi uchun kichik pauza (1 soniya)
                    Thread.sleep(1000);

                } catch (Exception e) {
                    log.warn("⚠️ E'lonni tahlil qilishda xatolik: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ OLX ga ulanishda xatolik: {}", e.getMessage());
        }
    }

    /**
     * Sarlavha va tavsifga qarab kategoriyani xatosiz aniqlash
     */
    private String detectCategory(String title, String description) {
        String text = (title + " " + description).toLowerCase();

        // 1. Pazanda / Oshpaz / Restoran (Ofitsant shu yerga tushadi)
        if (text.contains("ofitsiant") || text.contains("ofitsant") || text.contains("barmen") ||
                text.contains("oshpaz") || text.contains("povar") || text.contains("idish yuvuvchi") ||
                text.contains("somsa") || text.contains("shashlik") || text.contains("fast food") || text.contains("qandolat")) {
            return "Pazanda / Oshpaz";
        }

        // 2. IT & Dasturlash (Faqat mustaqil so'z sifatida tekshiriladi)
        if (text.contains("dasturchi") || text.contains("developer") || text.contains("programmer") ||
                text.contains("frontend") || text.contains("backend") || text.contains("java") ||
                text.contains("python") || text.contains("php") || text.contains("flutter") ||
                text.contains(" ict ") || text.matches(".*\\b(it)\\b.*")) {
            return "IT & Dasturlash";
        }

        // 3. Haydovchi / Kuryer
        if (text.contains("haydovchi") || text.contains("shofyor") || text.contains("voditel") || text.contains("taxi")) {
            return "Haydovchi / Kuryer";
        }

        // 4. Kuryer / Yetkazib berish
        if (text.contains("kuryer") || text.contains("dostavka") || text.contains("yetkazib")) {
            return "Kuryer / Yetkazib berish";
        }

        // 5. Savdo / Sotuvchi
        if (text.contains("sotuvchi") || text.contains("kassir") || text.contains("savdo") ||
                text.contains("magazin") || text.contains("konsultant") || text.contains("operator")) {
            return "Savdo / Sotuvchi";
        }

        // 6. Farrosh / Tozalash
        if (text.contains("farrosh") || text.contains("tozalash") || text.contains("uborsh")) {
            return "Farrosh / Tozalash";
        }

        // 7. Qorovul / Xavfsizlik
        if (text.contains("qorovul") || text.contains("oxrana") || text.contains("xavfsizlik") || text.contains("soqchi")) {
            return "Qorovul / Xavfsizlik";
        }

        // 8. Qurilish
        if (text.contains("qurilish") || text.contains("usta") || text.contains("santexnik") ||
                text.contains("montaj") || text.contains("svarchik") || text.contains("gipsokarton")) {
            return "Qurilish";
        }

        // 9. Ta'lim / Repetitor
        if (text.contains("o'qituvchi") || text.contains("repetitor") || text.contains("ustoz") ||
                text.contains("tarbiyachi") || text.contains("bog'cha")) {
            return "Ta'lim / Repetitor";
        }

        return "Barcha vakansiyalar";
    }
}