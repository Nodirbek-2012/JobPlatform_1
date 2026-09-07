package uz.imaan.jobplatform.employer.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import uz.imaan.jobplatform.employer.dto.EmployerCreateDTO;
import uz.imaan.jobplatform.employer.dto.EmployerResponseDTO;
import uz.imaan.jobplatform.employer.dto.EmployerUpdateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.imaan.jobplatform.employer.service.interfacee.EmployerService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/employer")
@RequiredArgsConstructor
public class
EmployerController {

    private final EmployerService service;

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<EmployerResponseDTO> create(@RequestBody EmployerCreateDTO dto) {
        return ResponseEntity.ok(service.createJob(dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<List<EmployerResponseDTO>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<EmployerResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/my-jobs/{chatId}")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<List<EmployerResponseDTO>> getByEmployerChatId(@PathVariable Long chatId) {
        return ResponseEntity.ok(service.getByEmployerChatId(chatId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteJob(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<EmployerResponseDTO> update(@PathVariable Long id, @RequestBody EmployerUpdateDTO dto) {
        return ResponseEntity.ok(service.updateJob(id, dto));
    }
}
