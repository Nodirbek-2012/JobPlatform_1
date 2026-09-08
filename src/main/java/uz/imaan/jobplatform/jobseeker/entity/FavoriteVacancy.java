package uz.imaan.jobplatform.jobseeker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import uz.imaan.jobplatform.employer.job.JobVacancy;


import java.time.LocalDateTime;

@Entity
@Table(name = "favorite_vacancies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_profile_vacancy", columnNames = {"profile_id", "vacancy_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class FavoriteVacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private JobSeekerProfile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vacancy_id", nullable = false)
    private JobVacancy vacancy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public FavoriteVacancy(JobSeekerProfile profile, JobVacancy vacancy) {
        this.profile = profile;
        this.vacancy = vacancy;
    }
}