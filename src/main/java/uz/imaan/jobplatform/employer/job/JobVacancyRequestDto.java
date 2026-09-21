package uz.imaan.jobplatform.employer.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobVacancyRequestDto {

    @NotNull(message = "Ish beruvchi IDsi bo'sh bo'lishi mumkin emas")
    private Long employerChatId;

    @NotBlank(message = "Vakansiya sarlavhasi bo'sh bo'lishi mumkin emas")
    @Size(min = 3, max = 150, message = "Sarlavha 3 dan 150 tagacha belgidan iborat bo'lishi kerak")
    private String title;

    @NotBlank(message = "Kategoriya tanlanishi shart")
    private String category;

    private String type;

    @NotBlank(message = "Maosh ma'lumoti kiritilishi shart")
    private String salary;

    private String workHours;
    private Integer workerCount;

    @Size(max = 2000, message = "Talablar 2000 ta belgidan oshmasligi kerak")
    private String requirements;

    private String phoneNumber;
    private String companyName;
}