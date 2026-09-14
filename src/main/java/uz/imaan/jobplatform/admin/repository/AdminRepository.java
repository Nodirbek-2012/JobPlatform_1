package uz.imaan.jobplatform.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.imaan.jobplatform.admin.entity.Admin;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByTelegramId(Long telegramId);

    Optional<Admin> findByPhoneNumber(String phoneNumber);

    boolean existsByTelegramId(Long telegramId);
}