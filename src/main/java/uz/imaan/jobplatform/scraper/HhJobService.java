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

@Slf4j
@Service
@RequiredArgsConstructor
public class HhJobService {

    private final JobVacancyRepository jobVacancyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // O'zbekiston hududi kodi (area=2759)
    private static final String HH_API_URL = "https://api.hh.ru/vacancies?area=2759&per_page=20";

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 30 */3 * * *")
    public void fetchHhJobs() {
        log.info("🚀 HeadHunter (hh.uz) dan yangi vakansiyalarni tortish boshlandi...");

        try {
            // ⭐️ HH API talab qiladigan rasmiy dastur formati (403 xatosini oldini oladi)
            String appUserAgent = "JobPlatformBot/1.0 (support@imaan.uz)";

            Connection.Response response = Jsoup.connect(HH_API_URL)
                    .ignoreContentType(true)
                    .userAgent(appUserAgent)
                    .header("HH-User-Agent", appUserAgent)
                    .header("Accept", "application/json")
                    .header("Accept-Language", "ru-RU,ru;q=0.9,uz;q=0.8,en;q=0.7")
                    .timeout(20000)
                    .execute();

            if (response.body() == null || response.body().isBlank()) return;

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.get("items");

            if (items == null || !items.isArray()) return;

            for (JsonNode item : items) {
                String hhId = "hh_" + item.get("id").asText();

                if (jobVacancyRepository.existsByExternalId(hhId)) {
                    continue;
                }

                String title = item.get("name").asText();
                String url = item.get("alternate_url").asText();

                String companyName = "HeadHunter e'loni";
                if (item.has("employer") && item.get("employer").has("name")) {
                    companyName = item.get("employer").get("name").asText() + " (hh.uz)";
                }

                String salary = "Kelishilgan holda";
                if (item.hasNonNull("salary")) {
                    JsonNode salNode = item.get("salary");
                    String from = salNode.hasNonNull("from") ? salNode.get("from").asText() : "";
                    String to = salNode.hasNonNull("to") ? salNode.get("to").asText() : "";
                    String currency = salNode.hasNonNull("currency") ? salNode.get("currency").asText() : "";

                    if (!from.isEmpty() && !to.isEmpty()) {
                        salary = from + " - " + to + " " + currency;
                    } else if (!from.isEmpty()) {
                        salary = from + " " + currency + " dan";
                    } else if (!to.isEmpty()) {
                        salary = to + " " + currency + " gacha";
                    }
                }

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

                JobVacancy vacancy = JobVacancy.builder()
                        .title(title)
                        .category(detectCategory(title, requirements))
                        .salary(salary)
                        .type("To'liq bandlik")
                        .requirements(requirements)
                        .phoneNumber("Havola orqali bog'laning")
                        .companyName(companyName)
                        .externalId(hhId)
                        .sourceUrl(url)
                        .isActive(true)
                        .notified(false) // ⭐️ JobAlertService yangi e'lon sifatida aniqlashi uchun
                        .build();

                jobVacancyRepository.save(vacancy);
                log.info("✅ hh.uz vakansiyasi saqlandi: {}", title);
            }

        } catch (Exception e) {
            log.error("❌ hh.uz API bilan ishlashda xatolik: {}", e.getMessage());
        }
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

        return "Barcha vakansiyalar";
    }
}