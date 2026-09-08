package uz.imaan.jobplatform.jobseeker.service.interfaces;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.imaan.jobplatform.jobseeker.dto.FavoriteVacancyDto;


public interface FavoriteVacancyService {

    /**
     * Agar vakansiya saqlangan bo'lsa o'chiradi, saqlanmagan bo'lsa saqlaydi (Toggle)
     * @return true - saqlandi, false - o'chirildi
     */
    boolean toggleFavorite(Long userId, Long vacancyId);

    boolean isFavorite(Long userId, Long vacancyId);

    Page<FavoriteVacancyDto> getFavorites(Long userId, Pageable pageable);

}
