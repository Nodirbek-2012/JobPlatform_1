package uz.imaan.jobplatform.scraper;

public record ScrapedJobItem(
        String title,          // Masalan: "Kassir (smena 4 soat)"
        String company,        // Kompaniya yoki kanal nomi
        String salary,         // "30 000 so'm/soat" yoki "Kelishilgan"
        String jobType,        // "SOATLIK", "PART_TIME", "KUNLIK"
        String location,       // Toshkent, Chilonzor
        String originalUrl,    // E'longa to'g'ridan-to'g'ri havola
        String source          // "HH.UZ", "OLX", "ISHGO", "TELEGRAM"
) {}