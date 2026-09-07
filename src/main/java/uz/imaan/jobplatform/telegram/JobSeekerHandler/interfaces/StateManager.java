package uz.imaan.jobplatform.telegram.JobSeekerHandler.interfaces;



import uz.imaan.jobplatform.telegram.JobSeekerHandler.JobSeekerState;

import java.util.Map;

public interface StateManager {
    // Holat olish
    JobSeekerState getState(Long chatId);

    // Holat o'rnatish (agar state null bo'lsa, o'chiradi)
    void setState(Long chatId, JobSeekerState state);

    // Holatni o'chirish
    void removeState(Long chatId);

    // Oldingi holatni olish
    JobSeekerState getPreviousState(Long chatId);

    // Oldingi holatni o'rnatish
    void setPreviousState(Long chatId, JobSeekerState state);

    // Oldingi holatni o'chirish
    void removePreviousState(Long chatId);

    // Vaqtinchalik ma'lumot olish
    String getData(Long chatId, String key);

    // Vaqtinchalik ma'lumot saqlash
    void putData(Long chatId, String key, String value);

    // Foydalanuvchining barcha vaqtinchalik ma'lumotlarini olish
    Map<String, String> getAllData(Long chatId);

    // Vaqtinchalik ma'lumotni o'chirish
    void removeData(Long chatId, String key);

    // Foydalanuvchining barcha vaqtinchalik ma'lumotlarini tozalash
    void clearData(Long chatId);
}
