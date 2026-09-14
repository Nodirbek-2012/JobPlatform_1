package uz.imaan.jobplatform.employer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.imaan.jobplatform.employer.dto.EmployerCreateDTO;
import uz.imaan.jobplatform.employer.dto.EmployerResponseDTO;
import uz.imaan.jobplatform.employer.dto.EmployerUpdateDTO;
import uz.imaan.jobplatform.employer.entity.EmployerEntity;
import uz.imaan.jobplatform.employer.job.JobStore;
import uz.imaan.jobplatform.employer.mapper.EmployerMapper;
import uz.imaan.jobplatform.employer.repository.EmployerRepository;
import uz.imaan.jobplatform.employer.service.interfacee.EmployerService;
import uz.imaan.jobplatform.employer.state.EmployerState;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployerServiceImpl implements EmployerService {

    private final EmployerRepository repository;
    private final EmployerMapper mapper;
    private final JobStore jobStore;

    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{7}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+998[0-9]{9}$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[0-9]+$");

    // ─────────────────────────── TELEGRAM FLOW ───────────────────────────

    @Override
    public SendMessage handleEmployer(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        EmployerState currentState = jobStore.getState(chatId);

        if ("Employer (Ish beruvchi)".equals(text)) {
            jobStore.clear(chatId);
            jobStore.setState(chatId, EmployerState.EMPLOYER_MAIN_MENU);
            return createEmployerMainMenu(chatId);
        }

        if ("➕ Yangi e'lon yaratish".equals(text)) {
            jobStore.clear(chatId);
            jobStore.setState(chatId, EmployerState.WAITING_FOR_TITLE);
            return createMessage(chatId, "📝 *Ish sarlavhasini (lavozimni) kiriting:*\n\n_Misol:_ Java Backend Dasturchi");
        }

        if ("📋 Mening vakansiyalarim".equals(text)) {
            List<EmployerResponseDTO> vacancies = getByEmployerChatId(chatId);
            if (vacancies.isEmpty()) {
                return createMessage(chatId, "📭 Sizda hozircha aktiv e'lonlar mavjud emas.");
            }
            StringBuilder sb = new StringBuilder("📋 *Sizning e'lonlaringiz:*\n\n");
            for (int i = 0; i < vacancies.size(); i++) {
                EmployerResponseDTO v = vacancies.get(i);
                sb.append(i + 1).append(". 📌 *").append(v.getTitle() != null ? v.getTitle() : "Noma'lum").append("*\n")
                        .append("   📂 Kategoriya: ").append(v.getCategory()).append("\n")
                        .append("   💰 Maosh: ").append(v.getSalary()).append(" so'm\n")
                        .append("   🟢 Holati: ").append(v.getStatus()).append("\n\n");
            }
            return createMessage(chatId, sb.toString());
        }

        if (currentState == null) {
            currentState = EmployerState.EMPLOYER_MAIN_MENU;
            jobStore.setState(chatId, currentState);
        }

        switch (currentState) {
            case WAITING_FOR_TITLE -> {
                if (text == null || text.trim().length() < 3) {
                    return createMessage(chatId, "❌ Sarlavha kamida 3 ta belgidan iborat bo'lishi kerak. Qaytadan kiriting:");
                }
                jobStore.getBuilder(chatId).title(text.trim());
                jobStore.setState(chatId, EmployerState.WAITING_FOR_CATEGORY);
                return createCategoryMenu(chatId);
            }
            case WAITING_FOR_CATEGORY -> {
                jobStore.getBuilder(chatId).category(text.trim());
                jobStore.setState(chatId, EmployerState.WAITING_FOR_PASSPORT);
                return createMessage(chatId, "🪪 *Pasport seria va raqamini kiriting:*\n\n💡 _Misol:_ `AA1234567`");
            }
            case WAITING_FOR_PASSPORT -> {
                if (text == null || !PASSPORT_PATTERN.matcher(text.trim()).matches()) {
                    return createMessage(chatId, "❌ *Pasport formati noto'g'ri!*\n\n" +
                            "Iltimos, 2 ta katta lotin harfi va 7 ta raqam kiriting.\n💡 _Misol:_ `AA1234567`");
                }
                jobStore.getBuilder(chatId).passportSeriesNumber(text.trim());
                jobStore.setState(chatId, EmployerState.WAITING_FOR_PHONE);
                return createMessage(chatId, "📞 *Telefon raqamingizni yuboring:*\n\n💡 _Misol:_ `+998901234567`");
            }
            case WAITING_FOR_PHONE -> {
                if (text == null || !PHONE_PATTERN.matcher(text.trim()).matches()) {
                    return createMessage(chatId, "❌ *Telefon raqam formati noto'g'ri!*\n\n" +
                            "Iltimos, +998 bilan boshlanuvchi 9 ta raqam kiriting.\n💡 _Misol:_ `+998901234567`");
                }
                jobStore.getBuilder(chatId).phoneNumber(text.trim());
                jobStore.setState(chatId, EmployerState.WAITING_FOR_SALARY);
                return createMessage(chatId, "💰 *Maosh miqdorini raqamlarda kiriting:*\n\n_Misol:_ 8000000");
            }
            case WAITING_FOR_SALARY -> {
                if (text == null || !NUMBER_PATTERN.matcher(text.trim()).matches()) {
                    return createMessage(chatId, "❌ Maosh faqat musbat raqamlardan iborat bo'lishi kerak. Qaytadan kiriting:");
                }
                jobStore.getBuilder(chatId).salary(text.trim());
                jobStore.setState(chatId, EmployerState.WAITING_FOR_WORK_HOURS);
                return createMessage(chatId, "⏰ *Ish soatini raqamda kiriting:*\n\n_Misol:_ 8");
            }
            case WAITING_FOR_WORK_HOURS -> {
                if (text == null || !NUMBER_PATTERN.matcher(text.trim()).matches()) {
                    return createMessage(chatId, "❌ Ish soati faqat raqam bo'lishi kerak. Qaytadan kiriting:");
                }
                jobStore.getBuilder(chatId).workHours(text.trim());
                jobStore.setState(chatId, EmployerState.WAITING_FOR_REQUIREMENTS);
                return createMessage(chatId, "📋 *Talablarni kiriting:*\n\n_Misol:_ 1 yil tajriba, Ingliz tili");
            }
            case WAITING_FOR_REQUIREMENTS -> {
                EmployerCreateDTO draft = jobStore.getBuilder(chatId)
                        .requirements(text != null ? text.trim() : "")
                        .employerChatId(chatId)
                        .jobType("To'liq bandlik")
                        .workerCount(1)
                        .build();

                createJob(draft);
                jobStore.clear(chatId);
                jobStore.setState(chatId, EmployerState.EMPLOYER_MAIN_MENU);

                return createMessage(chatId, "🎉 *E'loningiz muvaffaqiyatli e'lon qilindi!*\n\n" +
                        "📌 *Nomi:* " + draft.getTitle() + "\n" +
                        "📂 *Kategoriya:* " + draft.getCategory() + "\n" +
                        "🪪 *Pasport:* " + draft.getPassportSeriesNumber() + "\n" +
                        "📞 *Tel:* " + draft.getPhoneNumber() + "\n" +
                        "💰 *Maosh:* " + draft.getSalary() + "\n" +
                        "⏰ *Ish vaqti:* " + draft.getWorkHours() + "\n" +
                        "📋 *Talablar:* " + draft.getRequirements());
            }
            default -> {
                return createEmployerMainMenu(chatId);
            }
        }
    }

    private SendMessage createEmployerMainMenu(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText("🏢 *Ish beruvchi boshqaruv paneli:*\nKerakli bo'limni tanlang:");
        sendMessage.setParseMode("Markdown");

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("➕ Yangi e'lon yaratish"));
        row1.add(new KeyboardButton("📋 Mening vakansiyalarim"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("⚙️ Sozlamalar"));
        row2.add(new KeyboardButton("🏠 Asosiy menyu"));

        keyboard.add(row1);
        keyboard.add(row2);

        markup.setKeyboard(keyboard);
        sendMessage.setReplyMarkup(markup);
        return sendMessage;
    }

    private SendMessage createCategoryMenu(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText("📂 *Vakansiya kategoriyasini tanlang:*");

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        String[] categories = {
                "💻 IT & Dasturlash", "🏗 Qurilish & Ta'mirlash",
                "🏭 Ishlab chiqarish", "🛒 Savdo & Sotuv",
                "🍽 Restoran & Kafelar", "🚗 Transport & Haydovchi",
                "📚 Ta'lim & Repetitorlik", "🏥 Tibbiyot & Dorixona",
                "💰 Moliya & Buxgalteriya", "🛠 Xizmat ko'rsatish"
        };

        for (int i = 0; i < categories.length; i += 2) {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(categories[i]));
            if (i + 1 < categories.length) {
                row.add(new KeyboardButton(categories[i + 1]));
            }
            keyboard.add(row);
        }

        markup.setKeyboard(keyboard);
        sendMessage.setReplyMarkup(markup);
        return sendMessage;
    }

    private SendMessage createMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(text);
        sendMessage.setParseMode("Markdown");
        return sendMessage;
    }

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