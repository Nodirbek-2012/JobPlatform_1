package uz.imaan.jobplatform.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.imaan.jobplatform.admin.dto.AdminDtoTwo;
import uz.imaan.jobplatform.admin.dto.AdminStatsDto;
import uz.imaan.jobplatform.admin.dto.BlockUserDto;
import uz.imaan.jobplatform.admin.service.AdminService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admins")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "Управление администраторами и пользователями")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    @Operation(summary = "Статистика платформы")
    public ResponseEntity<AdminStatsDto> getStats() {
        return ResponseEntity.ok(adminService.getDetailedStats());
    }

    @GetMapping
    @Operation(summary = "Список всех админов")
    public ResponseEntity<List<AdminDtoTwo>> getAll() {
        return ResponseEntity.ok(adminService.getAllAdmins());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Админ по ID")
    public ResponseEntity<AdminDtoTwo> getById(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getAdminById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Создать админа")
    public ResponseEntity<AdminDtoTwo> create(@RequestBody @Valid AdminDtoTwo dto) {
        return ResponseEntity.ok(adminService.createAdmin(dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Удалить админа")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminService.deleteAdmin(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/block")
    @Operation(summary = "Заблокировать пользователя")
    public ResponseEntity<Void> block(@RequestBody @Valid BlockUserDto dto) {
        adminService.blockUser(dto);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/unblock/{userId}")
    @Operation(summary = "Разблокировать пользователя")
    public ResponseEntity<Void> unblock(@PathVariable Long userId) {
        adminService.unblockUser(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/workers/{userId}")
    @Operation(summary = "Инфо о рабочем")
    public ResponseEntity<String> workerDetails(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.getWorkerDetails(userId));
    }
}