package uz.imaan.jobplatform.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uz.imaan.jobplatform.employer.job.JobVacancy;
import uz.imaan.jobplatform.employer.job.JobVacancyRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobAggregatorService {

    private final JobVacancyRepository jobVacancyRepository;

    /**
     * Barcha manbalardan (HH, OLX, IshGo, Telegram, OsonIsh) bazaga yig'ilgan
     * eng yangi soatlik va part-time ishlarni oladi
     */
    public List<ScrapedJobItem> getAllHourlyJobs() {
        // Bazadan aktiv va turi soatlik/part-time bo'lgan e'lonlarni saralab olamiz
        List<JobVacancy> vacancies = jobVacancyRepository.findAll().stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .filter(v -> v.getType() != null &&
                        (v.getType().toLowerCase().contains("soatlik") ||
                                v.getType().toLowerCase().contains("part-time") ||
                                v.getType().toLowerCase().contains("part_time") ||
                                v.getType().toLowerCase().contains("yarim")))
                .sorted((a, b) -> b.getId().compareTo(a.getId())) // Eng yangilari tepada
                .limit(25)
                .toList();

        return vacancies.stream()
                .map(this::toScrapedJobItem)
                .toList();
    }

    private ScrapedJobItem toScrapedJobItem(JobVacancy v) {
        String source = "BOSHQA";
        if (v.getExternalId() != null) {
            if (v.getExternalId().startsWith("hh_")) source = "HH.UZ";
            else if (v.getExternalId().startsWith("ishgo_")) source = "ISHGO";
            else if (v.getExternalId().startsWith("tg_")) source = "TELEGRAM";
            else if (v.getExternalId().startsWith("osonish_") || v.getExternalId().startsWith("oson_")) source = "OSONISH"; // 👈 OsonIsh manbasi qo'shildi
            else source = "OLX";
        }

        return new ScrapedJobItem(
                v.getTitle(),
                v.getCompanyName() != null ? v.getCompanyName() : "Kompaniya e'loni",
                v.getSalary() != null ? v.getSalary() : "Kelishilgan holda",
                v.getType() != null ? v.getType() : "SOATLIK",
                "O'zbekiston",
                v.getSourceUrl() != null ? v.getSourceUrl() : "",
                source
        );
    }
}