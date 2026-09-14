package uz.imaan.jobplatform.admin.mapper;

import org.springframework.stereotype.Component;
import uz.imaan.jobplatform.admin.dto.AdminDtoTwo;
import uz.imaan.jobplatform.admin.entity.Admin;

@Component
public class AdminMapper {

    public AdminDtoTwo toDto(Admin entity) {
        if (entity == null) return null;
        return AdminDtoTwo.builder()
                .id(entity.getId())
                .telegramId(entity.getTelegramId())
                .username(entity.getUsername())
                .name(entity.getName())
                .phoneNumber(entity.getPhoneNumber())
                .role(entity.getRole())
                .isActive(entity.getIsActive())
                .build();
    }

    public Admin toEntity(AdminDtoTwo dto) {
        if (dto == null) return null;
        return Admin.builder()
                .id(dto.id())
                .telegramId(dto.telegramId())
                .username(dto.username())
                .name(dto.name())
                .phoneNumber(dto.phoneNumber())
                .role(dto.role())
                .isActive(dto.isActive() != null ? dto.isActive() : true)
                .build();
    }
}