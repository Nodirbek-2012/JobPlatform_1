package uz.imaan.jobplatform.telegram.JobSeekerHandler.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.JobSeekerState;
import uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces.StateManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class StateManagerImpl implements StateManager {

    // Foydalanuvchi holatlari
    private final Map<Long, JobSeekerState> states = new ConcurrentHashMap<>();

    // Foydalanuvchi oldingi holatlari (orqaga qaytish uchun)
    private final Map<Long, JobSeekerState> previousStates = new ConcurrentHashMap<>();

    // Foydalanuvchi vaqtinchalik ma'lumotlari (registratsiya, karta va h.k.)
    private final Map<Long, Map<String, String>> data = new ConcurrentHashMap<>();

    @Override
    public JobSeekerState getState(Long chatId) {
        return states.getOrDefault(chatId, JobSeekerState.NONE);
    }

    @Override
    public void setState(Long chatId, JobSeekerState state) {
        if (state == null) {
            states.remove(chatId);
        } else {
            states.put(chatId, state);
        }
        log.debug("State updated: chatId={}, state={}", chatId, state);
    }

    @Override
    public void removeState(Long chatId) {
        states.remove(chatId);
        log.debug("State removed: chatId={}", chatId);
    }

    @Override
    public JobSeekerState getPreviousState(Long chatId) {
        return previousStates.get(chatId);
    }

    @Override
    public void setPreviousState(Long chatId, JobSeekerState state) {
        if (state == null) {
            previousStates.remove(chatId);
        } else {
            previousStates.put(chatId, state);
        }
        log.debug("Previous state updated: chatId={}, previousState={}", chatId, state);
    }

    @Override
    public void removePreviousState(Long chatId) {
        previousStates.remove(chatId);
    }

    @Override
    public String getData(Long chatId, String key) {
        Map<String, String> userData = data.get(chatId);
        if (userData == null) return null;
        return userData.get(key);
    }

    @Override
    public void putData(Long chatId, String key, String value) {
        data.putIfAbsent(chatId, new ConcurrentHashMap<>());
        if (value == null) {
            data.get(chatId).remove(key);
        } else {
            data.get(chatId).put(key, value);
        }
        log.debug("Data stored: chatId={}, key={}, value={}", chatId, key, value);
    }

    @Override
    public Map<String, String> getAllData(Long chatId) {
        data.putIfAbsent(chatId, new ConcurrentHashMap<>());
        return data.get(chatId);
    }

    @Override
    public void removeData(Long chatId, String key) {
        Map<String, String> userData = data.get(chatId);
        if (userData != null) {
            userData.remove(key);
        }
    }

    @Override
    public void clearData(Long chatId) {
        data.remove(chatId);
        log.debug("Data cleared: chatId={}", chatId);
    }

}
