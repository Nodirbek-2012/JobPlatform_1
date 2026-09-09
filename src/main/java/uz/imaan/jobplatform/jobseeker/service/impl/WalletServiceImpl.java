package uz.imaan.jobplatform.jobseeker.service.impl;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.imaan.jobplatform.jobseeker.dto.BankCardRequest;
import uz.imaan.jobplatform.jobseeker.entity.BankCard;
import uz.imaan.jobplatform.jobseeker.entity.JobSeekerProfile;
import uz.imaan.jobplatform.jobseeker.entity.WalletTransaction;
import uz.imaan.jobplatform.jobseeker.repository.BankCardRepository;
import uz.imaan.jobplatform.jobseeker.repository.JobSeekerProfileRepository;
import uz.imaan.jobplatform.jobseeker.repository.WalletTransactionRepository;
import uz.imaan.jobplatform.jobseeker.service.interfaces.WalletService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WalletServiceImpl implements WalletService {


    private final JobSeekerProfileRepository profileRepository;
    private final BankCardRepository bankCardRepository;
    private final WalletTransactionRepository transactionRepository;

    // ============================================
    // KARTA SERVICES
    // ============================================

    @Override
    @Transactional
    public BankCard addBankCard(Long userId, BankCardRequest request) {
        log.info("💳 Karta qo'shish: userId={}", userId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        // 1. Karta raqamini tekshirish
        if (request.cardNumber() == null || request.cardNumber().length() != 16) {
            throw new IllegalArgumentException("Karta raqami 16 ta raqamdan iborat bo'lishi kerak!");
        }

        if (!request.cardNumber().matches("\\d+")) {
            throw new IllegalArgumentException("Karta raqami faqat raqamlardan iborat bo'lishi kerak!");
        }

        // 2. Karta oldin ro'yxatdan o'tmaganligini tekshirish
        if (bankCardRepository.existsByCardNumber(request.cardNumber())) {
            throw new RuntimeException("Bu karta allaqachon ro'yxatdan o'tgan!");
        }



        // 4. Kartani saqlash
        BankCard bankCard = BankCard.builder()
                .cardNumber(request.cardNumber())
                .cardHolderName(request.cardHolderName().toUpperCase())
                .jobSeeker(profile)
                .isActive(true)
                .build();

        BankCard savedCard = bankCardRepository.save(bankCard);
        log.info("✅ Karta qo'shildi: cardId={}", savedCard.getId());

        return savedCard;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankCard> getMyCards(Long userId) {
        log.info("📋 Kartalar ro'yxati: userId={}", userId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        return bankCardRepository.findByJobSeekerId(profile.getId());
    }

    @Override
    public void validateExpireDate(String expireDate) {
        if (expireDate == null || expireDate.isEmpty()) {
            throw new IllegalArgumentException("Amal qilish muddati kiritilishi shart!");
        }

        if (!expireDate.matches("^(0[1-9]|1[0-2])/([0-9]{2})$")) {
            throw new IllegalArgumentException("Noto'g'ri format! Format: MM/YY (masalan: 12/26)");
        }

        try {
            String[] parts = expireDate.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);
            int fullYear = 2000 + year;

            LocalDate now = LocalDate.now();
            if (fullYear < now.getYear()) {
                throw new IllegalArgumentException("Karta muddati o'tgan! Yil: " + fullYear);
            }
            if (fullYear == now.getYear() && month < now.getMonthValue()) {
                throw new IllegalArgumentException("Karta muddati o'tgan! Oy: " + month);
            }

            log.info("✅ Karta muddati to'g'ri: {}/{}", month, fullYear);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Noto'g'ri ma'lumot!");
        }
    }

    @Override
    public boolean isCardExpired(String expireDate) {
        try {
            String[] parts = expireDate.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);
            int fullYear = 2000 + year;

            LocalDate now = LocalDate.now();
            return fullYear < now.getYear() || (fullYear == now.getYear() && month < now.getMonthValue());

        } catch (Exception e) {
            return true;
        }
    }

    // ============================================
    // TRANZAKSIYA SERVICES
    // ============================================

    @Override
    @Transactional(readOnly = true)
    public List<WalletTransaction> getTransactionHistory(Long userId) {
        log.info("📋 Tranzaksiyalar tarixi: userId={}", userId);

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        return transactionRepository.findByJobSeekerIdOrderByCreatedAtDesc(profile.getId());
    }

    @Override
    @Transactional
    @PreAuthorize("#userId == authentication.principal.userId")
    public String withdrawMoney(Long userId, BigDecimal amount) {
        log.info("💸 Pul yechish: userId={}, amount={}", userId, amount);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Miqdor 0 dan katta bo'lishi kerak!");
        }

        JobSeekerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));

        // Kartalar mavjudligini tekshirish
        List<BankCard> cards = bankCardRepository.findByJobSeekerId(profile.getId());
        if (cards.isEmpty()) {
            throw new IllegalStateException("Pul yechish uchun avval karta qo'shing!");
        }


        // Balansni tekshirish
        if (profile.getWalletBalance() == null || profile.getWalletBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Hisobda yetarli mablag' mavjud emas! Balans: " + profile.getWalletBalance());
        }

        // Pulni yechish
        profile.setWalletBalance(profile.getWalletBalance().subtract(amount));
        profileRepository.save(profile);

        // Tranzaksiyani saqlash
        WalletTransaction transaction = WalletTransaction.builder()
                .jobSeekerId(profile.getId())
                .amount(amount)
                .type(WalletTransaction.TransactionType.WITHDRAW)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
        log.info("✅ Pul yechildi: amount={}", amount);

        return "Pul yechish muvaffaqiyatli! Yechilgan summa: " + amount;
    }

    // ============================================
    // PROFIL SERVICES (Wallet uchun)
    // ============================================

    @Override
    @Transactional(readOnly = true)
    public JobSeekerProfile getProfileByUserId(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Ishchi profili topilmadi: " + userId));
    }


}
