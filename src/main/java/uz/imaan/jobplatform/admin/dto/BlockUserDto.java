package uz.imaan.jobplatform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BlockUserDto(
        @NotNull Long userId,
        @NotBlank String reason
) {}
