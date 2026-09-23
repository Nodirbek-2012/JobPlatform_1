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

    // O'zbekiston (area=2759) bo'yicha qisman bandlik
    private static final String HH_API_URL = "https://api.hh.ru/vacancies?area=2759&employment=part&per_page=20";

    private static final List<String> HOURLY_KEYWORDS = List.of(
            "soat", "soatlik", "soatbay", "smena", "part-time", "talaba",
            "yarim stavka", "kunlik", "0.5", "гибкий", "смена", "подработка"
    );

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 30 */2 * * *")
    public void fetchHhJobs() {
        log.info("🚀 HeadHunter (hh.uz) dan vakansiyalarni tortish boshlandi...");

        try {
            // HH API talab qiladigan standart format: DasturNomi/Versiya (kontakt_email)
            String appUserAgent = "JobFinderApp/1.0 (jobfinderuz@gmail.com)";

            Connection.Response response = Jsoup.connect(HH_API_URL)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true) // ⭐️ 403 xatosini o'qib olish uchun
                    .userAgent(appUserAgent)
                    .header("HH-User-Agent", appUserAgent)
                    .header("Accept", "application/json")
                    .timeout(15000)
                    .execute();

            if (response.statusCode() != 200) {
                log.warn("⚠️ HH API ruxsat bermadi (Status: {}). Javob: {}",
                        response.statusCode(), response.body());
                return;
            }

            if (response.body() == null || response.body().isBlank()) return;

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.get("items");

            if (items == null || !items.isArray()) return;

            int savedCount = 0;

            for (JsonNode item : items) {
                String hhId = "hh_" + item.get("id").asText();

                if (jobVacancyRepository.existsByExternalId(hhId)) {
                    continue;
                }

                String title = item.get("name").asText();
                String url = item.get("alternate_url").asText();

                String scheduleName = item.has("schedule") && item.get("schedule").has("name")
                        ? item.get("schedule").get("name").asText()
                        : "Moslashuvchan grafik";

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

                if (!isHourlyOrPartTimeJob(title, requirements, scheduleName)) {
                    continue;
                }

                String companyName = "HeadHunter e'loni";
                if (item.has("employer") && item.get("employer").has("name")) {
                    companyName = item.get("employer").get("name").asText() + " (hh.uz)";
                }

                JobVacancy vacancy = JobVacancy.builder()
                        .title(title)
                        .category(detectCategory(title, requirements))
                        .salary(extractSalary(item))
                        .type("Soatlik / Part-time")
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
            }

            log.info("✅ HeadHunter dan {} ta yangi e'lon saqlandi.", savedCount);

        } catch (Exception e) {
            log.error("❌ hh.uz bilan ishlashda xatolik: {}", e.getMessage());
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
                text.contains("oshpaz") || text.contains("povar")) {
            return "Pazanda / Oshpaz";
        }
        if (text.contains("dasturchi") || text.contains("developer") || text.contains("java") ||
                text.contains("python") || text.matches(".*\\b(it)\\b.*")) {
            return "IT & Dasturlash";
        }
        if (text.contains("haydovchi") || text.contains("kuryer") || text.contains("dostavka")) {
            return "Haydovchi / Kuryer";
        }
        if (text.contains("sotuvchi") || text.contains("kassir") || text.contains("operator")) {
            return "Savdo / Sotuvchi";
        }
        if (text.contains("farrosh") || text.contains("tozalash")) {
            return "Farrosh / Tozalash";
        }
        if (text.contains("qurilish") || text.contains("usta")) {
            return "Qurilish";
        }

        return "Boshqa soatlik ishlar";
    }
}