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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OlxJobScraperService {

    private final JobVacancyRepository jobVacancyRepository;

    // ⭐️ OLX ning aynan qisman bandlik (part-time / soatlik) bo'limi
    private static final String OLX_PART_TIME_URL = "https://www.olx.uz/oz/rabota/chastichnaya-zanyatost/";
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+?998[\\s-]?)?\\(?\\d{2}\\)?[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}");

    // Soatlik va smenali ishlarni aniqlash filtri
    private static final List<String> HOURLY_KEYWORDS = List.of(
            "soat", "soatlik", "soatbay", "kunlik", "smena", "smenali",
            "part-time", "part time", "talaba", "yarim stavka", "0.5",
            "подработка", "смена", "часовая", "гибкий"
    );

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 */2 * * *") // Har 2 soatda avtomatik ishga tushadi
    public void scrapeOlxJobs() {
        log.info("🚀 OLX dan SOATLIK va PART-TIME vakansiyalarni tortish boshlandi...");

        try {
            Document doc = Jsoup.connect(OLX_PART_TIME_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .timeout(12000)
                    .get();

            Elements jobCards = doc.select("[data-cy='l-card']");
            int savedCount = 0;

            for (Element card : jobCards) {
                try {
                    Element linkElement = card.selectFirst("a");
                    if (linkElement == null) continue;

                    String href = linkElement.attr("href");
                    String jobUrl = href.startsWith("http") ? href : "https://www.olx.uz" + href;
                    String externalId = card.attr("id");

                    // Bazada mavjud bo'lsa o'tkazib yuboramiz
                    if (externalId == null || externalId.isBlank() || jobVacancyRepository.existsByExternalId(externalId)) {
                        continue;
                    }

                    // Sarlavha
                    Element titleElement = card.selectFirst("h6, h4");
                    String title = titleElement != null ? titleElement.text().trim() : "Vakansiya";

                    // Maosh
                    Element priceElement = card.selectFirst("[data-testid='ad-price']");
                    String salary = priceElement != null ? priceElement.text().trim() : "Kelishilgan holda";

                    // Hudud
                    Element locationElement = card.selectFirst("[data-testid='location-date']");
                    String location = locationElement != null ? locationElement.text().split("-")[0].trim() : "O'zbekiston";

                    // E'lonning ichki sahifasini ochib tavsif va raqamni olish
                    String fullDescription = "Batafsil ma'lumot ko'rsatilmagan";
                    String extractedPhone = "E'lon havolasi orqali bog'laning";

                    try {
                        Document detailDoc = Jsoup.connect(jobUrl)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                .timeout(8000)
                                .get();

                        Element descElement = detailDoc.selectFirst("[data-cy='ad_description']");
                        if (descElement != null) {
                            fullDescription = descElement.text().trim();

                            Matcher matcher = PHONE_PATTERN.matcher(fullDescription);
                            if (matcher.find()) {
                                extractedPhone = matcher.group();
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("⚠️ OLX ichki sahifasini ochishda ogohlantirish ({}): {}", jobUrl, ex.getMessage());
                    }

                    // ⭐️ Qat'iy filtr: Agar e'lon matni soatlik yoki part-time talablariga mos kelmasa, saqlamaymiz
                    if (!isHourlyOrPartTime(title, fullDescription)) {
                        continue;
                    }

                    // Matnni qisqartirish (Telegram limitiga moslash)
                    if (fullDescription.length() > 300) {
                        fullDescription = fullDescription.substring(0, 297) + "...";
                    }

                    JobVacancy vacancy = JobVacancy.builder()
                            .title(title)
                            .category(detectCategory(title, fullDescription))
                            .salary(salary)
                            .type("Soatlik / Part-time") // 👈 Endi aniq part-time sifatida belgilanadi
                            .requirements(fullDescription)
                            .phoneNumber(extractedPhone)
                            .companyName("OLX e'loni (" + location + ")")
                            .externalId(externalId)
                            .sourceUrl(jobUrl)
                            .isActive(true)
                            .notified(false)
                            .build();

                    jobVacancyRepository.save(vacancy);
                    savedCount++;
                    log.info("✅ OLX [Soatlik] vakansiya qo'shildi: {} | Tel: {}", title, extractedPhone);

                    // OLX tomonidan IP bloklanishini oldini olish uchun 1 soniya kutish
                    Thread.sleep(1000);

                } catch (Exception e) {
                    log.warn("⚠️ OLX e'lonini tahlil qilishda xatolik: {}", e.getMessage());
                }
            }

            log.info("🎉 OLX dan jami {} ta soatlik/part-time e'lon saqlandi.", savedCount);

        } catch (Exception e) {
            log.error("❌ OLX ga ulanishda xatolik: {}", e.getMessage());
        }
    }

    /**
     * E'lonning soatlik yoki qisman bandlik ekanligini tasdiqlash
     */
    private boolean isHourlyOrPartTime(String title, String description) {
        String text = (title + " " + description).toLowerCase();
        return HOURLY_KEYWORDS.stream().anyMatch(text::contains);
    }

    /**
     * Sarlavha va tavsifga qarab mos kategoriyani tanlash
     */
    private String detectCategory(String title, String description) {
        String text = (title + " " + description).toLowerCase();

        if (text.contains("ofitsiant") || text.contains("ofitsant") || text.contains("barmen") ||
                text.contains("oshpaz") || text.contains("povar") || text.contains("idish yuvuvchi") ||
                text.contains("somsa") || text.contains("shashlik") || text.contains("fast food") || text.contains("qandolat")) {
            return "Pazanda / Oshpaz";
        }
        if (text.contains("dasturchi") || text.contains("developer") || text.contains("programmer") ||
                text.contains("frontend") || text.contains("backend") || text.contains("java") ||
                text.contains("python") || text.contains("php") || text.contains("flutter") ||
                text.contains(" ict ") || text.matches(".*\\b(it)\\b.*")) {
            return "IT & Dasturlash";
        }
        if (text.contains("haydovchi") || text.contains("shofyor") || text.contains("voditel") || text.contains("taxi")) {
            return "Haydovchi / Kuryer";
        }
        if (text.contains("kuryer") || text.contains("dostavka") || text.contains("yetkazib")) {
            return "Kuryer / Yetkazib berish";
        }
        if (text.contains("sotuvchi") || text.contains("kassir") || text.contains("savdo") ||
                text.contains("magazin") || text.contains("konsultant") || text.contains("operator")) {
            return "Savdo / Sotuvchi";
        }
        if (text.contains("farrosh") || text.contains("tozalash") || text.contains("uborsh")) {
            return "Farrosh / Tozalash";
        }
        if (text.contains("qorovul") || text.contains("oxrana") || text.contains("xavfsizlik") || text.contains("soqchi")) {
            return "Qorovul / Xavfsizlik";
        }
        if (text.contains("qurilish") || text.contains("usta") || text.contains("santexnik") ||
                text.contains("montaj") || text.contains("svarchik") || text.contains("gipsokarton")) {
            return "Qurilish";
        }
        if (text.contains("o'qituvchi") || text.contains("repetitor") || text.contains("ustoz") ||
                text.contains("tarbiyachi") || text.contains("bog'cha")) {
            return "Ta'lim / Repetitor";
        }

        return "Boshqa soatlik ishlar";
    }
}