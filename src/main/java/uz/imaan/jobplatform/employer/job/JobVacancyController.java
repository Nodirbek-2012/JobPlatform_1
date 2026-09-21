package uz.imaan.jobplatform.employer.job;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/v1/vacancies")
@RequiredArgsConstructor
public class JobVacancyController {

    private final JobVacancyService jobVacancyService;

    // Yangi vakansiya yaratish faqat ish beruvchi yoki admin uchun
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYER', 'ADMIN')")
    public ResponseEntity<JobVacancyResponseDto> create(@Valid @RequestBody JobVacancyRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobVacancyService.create(dto));
    }

    // Vakansiyani ko'rish barcha roldagilar (APPLICANT, EMPLOYER, ADMIN) uchun ochiq
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('APPLICANT', 'EMPLOYER', 'ADMIN')")
    public ResponseEntity<JobVacancyResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(jobVacancyService.getById(id));
    }

    // Barcha vakansiyalar ro'yxatini ko'rish
    @GetMapping
    @PreAuthorize("hasAnyRole('APPLICANT', 'EMPLOYER', 'ADMIN')")
    public ResponseEntity<Page<JobVacancyResponseDto>> getAll(Pageable pageable) {
        return ResponseEntity.ok(jobVacancyService.getAllActive(pageable));
    }

    // Vakansiyani yangilash faqat ish beruvchi yoki admin uchun
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYER', 'ADMIN')")
    public ResponseEntity<JobVacancyResponseDto> update(@PathVariable Long id,
                                                        @Valid @RequestBody JobVacancyRequestDto dto) {
        return ResponseEntity.ok(jobVacancyService.update(id, dto));
    }

    // Vakansiyani butunlay o'chirish faqat ADMIN uchun
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        jobVacancyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}