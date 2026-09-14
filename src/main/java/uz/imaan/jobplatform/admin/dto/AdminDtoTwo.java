package uz.imaan.jobplatform.admin.dto;

import lombok.Builder;
import uz.imaan.jobplatform.admin.category.Role;

@Builder
public record AdminDtoTwo(
        Long id,
        Long telegramId,
        String username,
        String name,
        String phoneNumber,
        Role role,
        Boolean isActive
) {}