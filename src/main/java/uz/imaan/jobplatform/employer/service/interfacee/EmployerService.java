package uz.imaan.jobplatform.employer.service.interfacee;

import uz.imaan.jobplatform.employer.dto.EmployerCreateDTO;
import uz.imaan.jobplatform.employer.dto.EmployerResponseDTO;
import uz.imaan.jobplatform.employer.dto.EmployerUpdateDTO;

import java.util.List;

public interface EmployerService {

    EmployerResponseDTO createJob(EmployerCreateDTO dto);

    EmployerResponseDTO getById(Long id);

    List<EmployerResponseDTO> getAll();

    List<EmployerResponseDTO> getByEmployerChatId(Long chatId);

    EmployerResponseDTO updateJob(Long id, EmployerUpdateDTO dto);

    void deleteJob(Long id);
}