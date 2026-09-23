package uz.imaan.jobplatform.scraper;

import java.util.List;

public class JobFilterUtil {

    // Qat'iy kalit so'zlar: faqat soatlik va part-time e'lonlarni ushlaydi
    private static final List<String> HOURLY_KEYWORDS = List.of(
            "soatlik", "soatiga", "soatbay", "/soat", "soat ish",
            "part-time", "part time", "yarim stavka", "0.5 stavka",
            "smena", "smenali", "talabalar uchun", "kunlik ish", "kunlik"
    );

    // To'liq stavkali (full-time) ishlarni chetlab o'tish uchun filtr
    private static final List<String> EXCLUDE_KEYWORDS = List.of(
            "to'liq stavka", "full-time", "full time", "1.0 stavka", "faqat to'liq"
    );

    public static boolean isHourlyOrPartTime(String title, String description) {
        String fullText = (title + " " + (description != null ? description : "")).toLowerCase();

        // Agar e'lon aniq full-time bo'lsa, uni rad etamiz
        boolean hasExclude = EXCLUDE_KEYWORDS.stream().anyMatch(fullText::contains);
        if (hasExclude && !fullText.contains("part-time") && !fullText.contains("soatlik")) {
            return false;
        }

        // Soatlik yoki part-time so'zlari borligini tekshiramiz
        return HOURLY_KEYWORDS.stream().anyMatch(fullText::contains);
    }
}