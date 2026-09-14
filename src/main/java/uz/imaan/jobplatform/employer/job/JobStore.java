package uz.imaan.jobplatform.employer.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.imaan.jobplatform.employer.dto.EmployerCreateDTO;
import uz.imaan.jobplatform.employer.state.EmployerState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class JobStore {

    private final JobVacancyRepository vacancyRepository;
    private final Map<Long, EmployerState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, EmployerCreateDTO> drafts = new ConcurrentHashMap<>();

    public EmployerState getState(Long chatId) {
        return userStates.getOrDefault(chatId, EmployerState.NONE);
    }

    public void setState(Long chatId, EmployerState state) {
        userStates.put(chatId, state);
    }

    /** Возвращает черновик вакансии для указанного чата. */
    public EmployerCreateDTO getDraft(Long chatId) {
        return drafts.computeIfAbsent(chatId, k -> new EmployerCreateDTO());
    }

    public void addVacancy(JobVacancy vacancy) {
        vacancyRepository.save(vacancy);
    }

    public List<JobVacancy> getAllVacancies() {
        return vacancyRepository.findAll();
    }

    public List<JobVacancy> getVacanciesByEmployer(Long employerChatId) {
        return vacancyRepository.findByEmployerChatId(employerChatId);
    }

    public void deleteVacancy(Long id) {
        vacancyRepository.deleteById(id);
    }

    public void clear(Long chatId) {
        userStates.remove(chatId);
        drafts.remove(chatId);
    }
}