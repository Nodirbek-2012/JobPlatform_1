package uz.imaan.jobplatform.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
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
public class HhJobService {

    private final JobVacancyRepository jobVacancyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ⭐️ O'zbekiston (area=2759) bo'yicha FAQAT qisman bandlik (employment=part) va smenali/moslashuvchan ishlar
    private static final String HH_API_URL = "https://api.hh.ru/vacancies?area=2759&employment=part&per_page=50";

    // Qo'shimcha soatlik/part-time kalit so'zlari
    private static final List<String> HOURLY_KEYWORDS = List.of(
            "soat", "soatlik", "soatbay", "smena", "part-time", "talaba",
            "yarim stavka", "kunlik", "0.5", "гибкий", "смена", "подработка"
    );

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 30 */2 * * *") // Har 2 soatda tekshiradi
    public void fetchHhJobs() {
        log.info("🚀 HeadHunter (hh.uz) dan SOATLIK va PART-TIME vakansiyalarni tortish boshlandi...");

        try {
            String appUserAgent = "JobPlatformBot/1.0 (support@imaan.uz)";

            Connection.Response response = Jsoup.connect(HH_API_URL)
                    .ignoreContentType(true)
                    .userAgent(appUserAgent)
                    .header("HH-User-Agent", appUserAgent)
                    .header("Accept", "application/json")
                    .header("Accept-Language", "uz-UZ,uz;q=0.9,ru;q=0.8")
                    .timeout(20000)
                    .execute();

            if (response.body() == null || response.body().isBlank()) return;

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.get("items");

            if (items == null || !items.isArray()) return;

            int savedCount = 0;

            for (JsonNode item : items) {
                String hhId = "hh_" + item.get("id").asText();

                // Bazada oldin saqlangan bo'lsa o'tkazib yuboramiz
                if (jobVacancyRepository.existsByExternalId(hhId)) {
                    continue;
                }

                String title = item.get("name").asText();
                String url = item.get("alternate_url").asText();

                // Ish grafigi va bandlik turini aniqlash (Masalan: Сменный график / Гибкий график)
                String scheduleName = item.has("schedule") && item.get("schedule").has("name")
                        ? item.get("schedule").get("name").asText()
                        : "Moslashuvchan grafik";

                // Tavsif snippetlarini yig'ish
                StringBuilder desc = new StringBuilder();
                if (item.has("snippet")) {
                    JsonNode snippet = item.get("snippet");
                    if (snippet.hasNonNull("requirement")) {
                        desc.append(snippet.get("requirement").asText().replaceAll("<[^>]*>", "")).append("\n");
                    }
                    if (snippet.hasNonNull("responsibility")) {
                        desc.append(snippet.get("responsibility").asText().replaceAll("<[^>]*>", ""));
                    }
                }

                String requirements = desc.length() > 0 ? desc.toString() : "Batafsil ma'lumot havola orqali";
                if (requirements.length() > 300) {
                    requirements = requirements.substring(0, 297) + "...";
                }

                // ⭐️ Qo'shimcha filtr: sarlavha yoki tavsifda soatlik/part-time xususiyatini tekshirish
                if (!isHourlyOrPartTimeJob(title, requirements, scheduleName)) {
                    continue;
                }

                String companyName = "HeadHunter e'loni";
                if (item.has("employer") && item.get("employer").has("name")) {
                    companyName = item.get("employer").get("name").asText() + " (hh.uz)";
                }

                // Maoshni hisoblash
                String salary = extractSalary(item);

                // Ish turini aniq ko'rsatish
                String jobType = "Soatlik / Part-time (" + scheduleName + ")";

                JobVacancy vacancy = JobVacancy.builder()
                        .title(title)
                        .category(detectCategory(title, requirements))
                        .salary(salary)
                        .type(jobType) // 👈 "To'liq bandlik" o'rniga aniq soatlik/part-time yoziladi
                        .requirements(requirements)
                        .phoneNumber("Havola orqali bog'laning")
                        .companyName(companyName)
                        .externalId(hhId)
                        .sourceUrl(url)
                        .isActive(true)
                        .notified(false)
                        .build();

                jobVacancyRepository.save(vacancy);
                savedCount++;
                log.info("✅ hh.uz [Soatlik/Part-time] vakansiya saqlandi: {}", title);
            }

            log.info("🎉 hh.uz dan jami {} ta soatlik/part-time ish saqlandi.", savedCount);

        } catch (Exception e) {
            log.error("❌ hh.uz API bilan ishlashda xatolik: {}", e.getMessage());
        }
    }

    private boolean isHourlyOrPartTimeJob(String title, String desc, String schedule) {
        String fullText = (title + " " + desc + " " + schedule).toLowerCase();
        return HOURLY_KEYWORDS.stream().anyMatch(fullText::contains);
    }

    private String extractSalary(JsonNode item) {
        if (!item.hasNonNull("salary")) {
            return "Kelishilgan holda";
        }
        JsonNode salNode = item.get("salary");
        String from = salNode.hasNonNull("from") ? salNode.get("from").asText() : "";
        String to = salNode.hasNonNull("to") ? salNode.get("to").asText() : "";
        String currency = salNode.hasNonNull("currency") ? salNode.get("currency").asText() : "UZS";

        if (!from.isEmpty() && !to.isEmpty()) {
            return from + " - " + to + " " + currency;
        } else if (!from.isEmpty()) {
            return from + " " + currency + " dan";
        } else if (!to.isEmpty()) {
            return to + " " + currency + " gacha";
        }
        return "Kelishilgan holda";
    }

    private String detectCategory(String title, String description) {
        String text = (title + " " + description).toLowerCase();

        if (text.contains("ofitsiant") || text.contains("ofitsant") || text.contains("barmen") ||
                text.contains("oshpaz") || text.contains("povar") || text.contains("somsa")) {
            return "Pazanda / Oshpaz";
        }
        if (text.contains("dasturchi") || text.contains("developer") || text.contains("programmer") ||
                text.contains("frontend") || text.contains("backend") || text.contains("java") ||
                text.contains("python") || text.matches(".*\\b(it)\\b.*")) {
            return "IT & Dasturlash";
        }
        if (text.contains("haydovchi") || text.contains("shofyor") || text.contains("voditel")) {
            return "Haydovchi / Kuryer";
        }
        if (text.contains("kuryer") || text.contains("dostavka") || text.contains("yetkazib")) {
            return "Kuryer / Yetkazib berish";
        }
        if (text.contains("sotuvchi") || text.contains("kassir") || text.contains("savdo") || text.contains("operator")) {
            return "Savdo / Sotuvchi";
        }
        if (text.contains("farrosh") || text.contains("tozalash") || text.contains("uborsh")) {
            return "Farrosh / Tozalash";
        }
        if (text.contains("qorovul") || text.contains("oxrana") || text.contains("xavfsizlik")) {
            return "Qorovul / Xavfsizlik";
        }
        if (text.contains("qurilish") || text.contains("usta") || text.contains("santexnik") || text.contains("montaj")) {
            return "Qurilish";
        }
        if (text.contains("o'qituvchi") || text.contains("repetitor") || text.contains("ustoz") || text.contains("tarbiyachi")) {
            return "Ta'lim / Repetitor";
        }

        return "Boshqa soatlik ishlar";
    }
}