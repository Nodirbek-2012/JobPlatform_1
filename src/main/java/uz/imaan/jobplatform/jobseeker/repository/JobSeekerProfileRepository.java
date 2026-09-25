package uz.imaan.jobplatform.jobseeker.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;


import java.util.List;
import java.util.Optional;

public interface JobSeekerProfileRepository extends JpaRepository<JobSeekerProfile, Long> {

    // 1. Telegram userId bo'yicha topish
    Optional<JobSeekerProfile> findByUserId(Long userId);

    // Yoki agar Entity'dagi maydon 'telegramId' bo'lsa:
    Optional<JobSeekerProfile> findByTelegramId(Long telegramId);

    // 2. Admin uchun: Telefon raqam bo'yicha topish
    Optional<JobSeekerProfile> findByPhoneNumber(String phoneNumber);

    // 3. Admin uchun: Ism-familiya bo'yicha izlash (Substring va Case-insensitive)
    List<JobSeekerProfile> findByFullNameContainingIgnoreCase(String fullName);

    List<JobSeekerProfile> findAllByCategoryIgnoreCase(String category);

    // 4. Admin uchun: Foydalanuvchi mavjudligini tekshirish
    boolean existsByUserId(Long userId);
    boolean existsByPhoneNumber(String phoneNumber);

    // 5. Admin uchun: Barcha foydalanuvchilarni sahifalab (Pagination) olish
    Page<JobSeekerProfile> findAll(Pageable pageable);

    // 6. Admin uchun: Universal qidiruv (ism yoki telefon bo'yicha bir vaqtning o'zida)
    @Query("SELECT j FROM JobSeekerProfile j WHERE " +
            "LOWER(j.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "j.phoneNumber LIKE CONCAT('%', :keyword, '%')")
    List<JobSeekerProfile> searchForAdmin(@Param("keyword") String keyword);

}

