package uz.imaan.jobplatform.jobseeker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.imaan.jobplatform.jobseeker.dto.ApplyJobRequest;
import uz.imaan.jobplatform.jobseeker.dto.JobApplicationDto;
import uz.imaan.jobplatform.jobseeker.dto.UpdateApplicationStatusRequest;
import uz.imaan.jobplatform.jobseeker.service.impl.JobApplicationService;


import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService applicationService;

    // Faqat ish izlovchi ariza topshira oladi
    @PostMapping
    @PreAuthorize("hasRole('APPLICANT')")
    public ResponseEntity<JobApplicationDto> apply(@Valid @RequestBody ApplyJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.apply(request));
    }

    // O'z arizalarini ko'rish
    @GetMapping("/seeker/{seekerId}")
    @PreAuthorize("hasAnyRole('APPLICANT', 'ADMIN')")
    public ResponseEntity<List<JobApplicationDto>> getBySeeker(@PathVariable Long seekerId) {
        return ResponseEntity.ok(applicationService.getApplicationsBySeekerId(seekerId));
    }

    // Arizani qabul qilish yoki rad etish faqat EMPLOYER yoki ADMIN uchun
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('EMPLOYER', 'ADMIN')")
    public ResponseEntity<JobApplicationDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return ResponseEntity.ok(applicationService.updateStatus(id, request));
    }
}