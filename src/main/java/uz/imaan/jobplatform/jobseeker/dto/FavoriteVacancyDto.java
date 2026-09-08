package uz.imaan.jobplatform.jobseeker.dto;

import java.time.LocalDateTime;

public record FavoriteVacancyDto(

        Long favoriteId,
        Long vacancyId,
        String title,
        String category,
        String type,
        String salary,
        String workHours,
        LocalDateTime savedAt
) {
}
