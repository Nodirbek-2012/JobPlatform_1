package uz.imaan.jobplatform.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AuthDto {
    public record TelegramLoginRequest(
            @NotNull(message = "Telegram ID bo'sh bo'lishi mumkin emas")
            Long telegramId,
            @NotBlank(message = "Rol kiritilishi shart (APPLICANT, EMPLOYER, ADMIN)")
            String role
    ) {}

    public record AuthResponse(
            String token,
            String tokenType,
            Long telegramId,
            String role
    ) {}
}