package uz.imaan.jobplatform.employer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.imaan.jobplatform.employer.dto.EmployerCreateDTO;
import uz.imaan.jobplatform.employer.dto.EmployerResponseDTO;
import uz.imaan.jobplatform.employer.dto.EmployerUpdateDTO;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;
import uz.imaan.jobplatform.employer.mapper.EmployerMapper;
import uz.imaan.jobplatform.employer.repository.EmployerRepository;
import uz.imaan.jobplatform.employer.service.interfacee.EmployerService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployerServiceImpl implements EmployerService {

    private final EmployerRepository repository;
    private final EmployerMapper mapper;

    // ─────────────────────────── CRUD ───────────────────────────

    @Override
    @Transactional
    public EmployerResponseDTO createJob(EmployerCreateDTO dto) {
        EmployerEntity entity = mapper.toEntity(dto);
        entity.setCreatedAt(LocalDateTime.now());
        if (entity.getStatus() == null) entity.setStatus("ACTIVE");

        EmployerEntity saved = repository.save(entity);
        log.info("Created vacancy id={}, title={}", saved.getId(), saved.getTitle());
        return mapper.toResponseDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployerResponseDTO getById(Long id) {
        return mapper.toResponseDTO(repository.findById(id)
                .orElseThrow(() -> new RuntimeException("E'lon topilmadi: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployerResponseDTO> getAll() {
        return repository.findAll().stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployerResponseDTO> getByEmployerChatId(Long chatId) {
        return repository.findByEmployerChatId(chatId)
                .map(mapper::toResponseDTO)
                .map(List::of)
                .orElseGet(List::of);
    }

    @Override
    @Transactional
    public EmployerResponseDTO updateJob(Long id, EmployerUpdateDTO dto) {
        EmployerEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("E'lon topilmadi: " + id));

        mapper.updateEntityFromDto(dto, entity);

        return mapper.toResponseDTO(repository.save(entity));
    }

    @Override
    @Transactional
    public void deleteJob(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("E'lon topilmadi: " + id);
        }
        repository.deleteById(id);
        log.info("Deleted vacancy id={}", id);
    }
}