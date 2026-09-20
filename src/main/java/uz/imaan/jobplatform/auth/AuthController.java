package uz.imaan.jobplatform.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.imaan.jobplatform.config.JwtService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> loginWithTelegram(@Valid @RequestBody AuthDto.TelegramLoginRequest request) {
        // Telegram ID va rolni JWT ga joylab token qaytaramiz
        String token = jwtService.generateToken(request.telegramId(), request.role().toUpperCase());
        return ResponseEntity.ok(new AuthDto.AuthResponse(token, "Bearer", request.telegramId(), request.role().toUpperCase()));
    }
}