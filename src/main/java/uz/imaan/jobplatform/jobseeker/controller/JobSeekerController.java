package uz.imaan.jobplatform.jobseeker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.imaan.jobplatform.jobseeker.dto.*;
import uz.imaan.jobplatform.jobseeker.entity.JobApplication;
import uz.imaan.jobplatform.jobseeker.service.interfaces.JobSeekerService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/job-seeker")
@RequiredArgsConstructor
public class JobSeekerController {

    private final JobSeekerService jobSeekerService;

    // ============================================
    // 1. PROFIL ENDPOINTS
    // ============================================

    @Operation(summary = "Ish izlovchi profilini olish",
            description = "Foydalanuvchi ID si bo'yicha profil ma'lumotlarini qaytaradi.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profil muvaffaqiyatli topildi"),
            @ApiResponse(responseCode = "403", description = "Ruxsat etilmagan"),
            @ApiResponse(responseCode = "404", description = "Foydalanuvchi profili topilmadi")
    })
    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('APPLICANT', 'JOB_SEEKER', 'ADMIN')")
    @PostAuthorize("hasRole('ADMIN') or returnObject.body.userId.toString() == authentication.name")
    public ResponseEntity<JobSeekerProfileDto> getProfile(
            @RequestHeader("X-User-Id") Long userId) {
        log.info("GET /api/v1/job-seeker/profile - userId: {}", userId);
        JobSeekerProfileDto profile = jobSeekerService.getProfileByUserId(userId);
        return ResponseEntity.ok(profile);
    }

    @Operation(summary = "Ish izlovchi profilini yangilash",
            description = "Rezyume, ko'nikmalar va tajribalarni yangilash.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profil muvaffaqiyatli yangilandi"),
            @ApiResponse(responseCode = "400", description = "Xato ma'lumot yuborildi"),
            @ApiResponse(responseCode = "403", description = "Birovning profilini yangilash taqiqlanadi"),
            @ApiResponse(responseCode = "404", description = "Foydalanuvchi profili topilmadi")
    })
    @PutMapping("/profile")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<JobSeekerProfileDto> updateProfile(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        log.info("PUT /api/v1/job-seeker/profile - userId: {}", userId);
        JobSeekerProfileDto updatedProfile = jobSeekerService.updateProfile(userId, request);
        return ResponseEntity.ok(updatedProfile);
    }

    // ============================================
    // 2. RESUME ENDPOINTS
    // ============================================

    @Operation(summary = "Yangi rezyume yaratish")
    @PostMapping("/resume")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<ResumeDto> createResume(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateResumeRequest request) {
        log.info("POST /api/v1/job-seeker/resume - userId: {}", userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobSeekerService.createResume(userId, request));
    }

    @Operation(summary = "Rezyumeni yangilash")
    @PutMapping("/resume/{resumeId}")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<ResumeDto> updateResume(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long resumeId,
            @Valid @RequestBody UpdateResumeRequest request) {
        log.info("PUT /api/v1/job-seeker/resume/{} - userId: {}", resumeId, userId);
        return ResponseEntity.ok(jobSeekerService.updateResume(userId, resumeId, request));
    }

    @Operation(summary = "Rezyumeni o'chirish")
    @DeleteMapping("/resume/{resumeId}")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<Void> deleteResume(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long resumeId) {
        log.info("DELETE /api/v1/job-seeker/resume/{} - userId: {}", resumeId, userId);
        jobSeekerService.deleteResume(userId, resumeId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Rezyumeni aktiv qilish")
    @PatchMapping("/resume/{resumeId}/activate")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<Void> setActiveResume(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long resumeId) {
        log.info("PATCH /api/v1/job-seeker/resume/{}/activate - userId: {}", resumeId, userId);
        jobSeekerService.setActiveResume(userId, resumeId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Barcha rezyumelar ro'yxati")
    @GetMapping("/resume")
    @PostAuthorize("hasAnyRole('EMPLOYER', 'ADMIN') or returnObject.body == null or #userId.toString() == authentication.name")
    public ResponseEntity<List<ResumeDto>> getMyResumes(
            @RequestHeader("X-User-Id") Long userId) {
        log.info("GET /api/v1/job-seeker/resume - userId: {}", userId);
        return ResponseEntity.ok(jobSeekerService.getMyResumes(userId));
    }

    @Operation(summary = "Aktiv rezyumeni olish")
    @GetMapping("/resume/active")
    @PreAuthorize("hasAnyRole('APPLICANT', 'JOB_SEEKER', 'EMPLOYER', 'ADMIN')")
    public ResponseEntity<ResumeDto> getActiveResume(
            @RequestHeader("X-User-Id") Long userId) {
        log.info("GET /api/v1/job-seeker/resume/active - userId: {}", userId);
        ResumeDto resume = jobSeekerService.getActiveResume(userId);
        return resume != null ? ResponseEntity.ok(resume) : ResponseEntity.noContent().build();
    }

    @Operation(summary = "Rezyume ID bo'yicha olish",
            description = "Rezyume ID si bo'yicha rezyume ma'lumotlarini qaytaradi.")
    @GetMapping("/resume/{resumeId}")
    @PreAuthorize("hasAnyRole('APPLICANT', 'JOB_SEEKER', 'EMPLOYER', 'ADMIN')")
    @PostAuthorize("hasAnyRole('EMPLOYER', 'ADMIN') or returnObject.body == null or #userId.toString() == authentication.name")
    public ResponseEntity<ResumeDto> getResumeById(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long resumeId) {
        log.info("GET /api/v1/job-seeker/resume/{} - userId: {}", resumeId, userId);
        return ResponseEntity.ok(jobSeekerService.getResumeById(userId, resumeId));
    }

    // ============================================
    // 3. JOB APPLICATION ENDPOINTS
    // ============================================

    @Operation(summary = "Vakansiyaga ariza topshirish")
    @PostMapping("/apply")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<JobApplication> applyForJob(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ApplyJobRequest request) {
        log.info("POST /api/v1/job-seeker/apply - userId: {}, jobId: {}", userId, request.jobId());
        JobApplication response = jobSeekerService.applyForJob(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Topshirilgan arizalar ro'yxatini olish (Paginatsiya bilan)")
    @GetMapping("/applications")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<Page<JobApplication>> getMyApplicationsWithPagination(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        log.info("GET /api/v1/job-seeker/applications - userId: {}, page: {}, size: {}",
                userId, pageable.getPageNumber(), pageable.getPageSize());
        Page<JobApplication> applications = jobSeekerService.getMyApplicationsWithPagination(userId, pageable);
        return ResponseEntity.ok(applications);
    }

    @Operation(summary = "Topshirilgan arizalar ro'yxati (DTO)")
    @GetMapping("/applications/list")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<List<JobApplicationDto>> getMyApplications(
            @RequestHeader("X-User-Id") Long userId) {
        log.info("GET /api/v1/job-seeker/applications/list - userId: {}", userId);
        List<JobApplicationDto> applications = jobSeekerService.getMyApplications(userId);
        return ResponseEntity.ok(applications);
    }

    @Operation(summary = "Qabul qilingan faol ishlarim")
    @GetMapping("/applications/active")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<List<JobApplication>> getMyActiveJobs(
            @RequestHeader("X-User-Id") Long userId) {
        log.info("GET /api/v1/job-seeker/applications/active - userId: {}", userId);
        List<JobApplication> activeJobs = jobSeekerService.getMyActiveJobs(userId);
        return ResponseEntity.ok(activeJobs);
    }

    @Operation(summary = "Topshirilgan arizani bekor qilish")
    @DeleteMapping("/applications/{applicationId}")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<Void> cancelApplication(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long applicationId) {
        log.info("DELETE /api/v1/job-seeker/applications/{} - userId: {}", applicationId, userId);
        jobSeekerService.cancelApplication(userId, applicationId);
        return ResponseEntity.noContent().build();
    }

    // ============================================
    // 4. SETTINGS ENDPOINTS
    // ============================================

    @Operation(summary = "Sozlamalarni yangilash")
    @PatchMapping("/settings")
    @PreAuthorize("hasRole('ADMIN') or ((hasRole('APPLICANT') or hasRole('JOB_SEEKER')) and #userId.toString() == authentication.name)")
    public ResponseEntity<Void> updateSettings(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody SettingsUpdateRequest request) {
        log.info("PATCH /api/v1/job-seeker/settings - userId: {}", userId);
        jobSeekerService.updateSettings(userId, request);
        return ResponseEntity.ok().build();
    }
}