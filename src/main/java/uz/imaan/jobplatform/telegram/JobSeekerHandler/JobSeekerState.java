package uz.imaan.jobplatform.telegram.JobSeekerHandler;

public enum JobSeekerState {
    NONE,

    // Ro'yxatdan o'tish holatlari
    WAITING_FOR_LANGUAGE,
    WAITING_FOR_NAME,
    WAITING_FOR_PASSPORT,
    WAITING_FOR_PHONE,
    WAITING_FOR_JOB_TYPE,
    WAITING_FOR_CATEGORY,
    WAITING_FOR_CUSTOM_CATEGORY,
    WAITING_FOR_EXPERIENCE,
    WAITING_FOR_PROFESSION,

    // Asosiy menyular va navigatsiya
    MAIN_MENU,
    JOB_SEARCH,
    VIEW_JOB_DETAILS,
    ACTIVE_JOBS,
    PROFILE_MENU,
    APPLICATIONS,
    WALLET_MENU,
    SETTINGS_MENU,
    APPLY_COMMENT,

    // Kartalar va portfolio
    WAITING_FOR_CARD_NUMBER,
    WAITING_FOR_CARD_HOLDER,
    WAITING_FOR_PORTFOLIO,
    WAITING_FOR_PHOTO,

    // ⭐️ PROFILNI TAHRIRLASH HOLATLARI
    EDIT_MENU,                      // Tahrirlash tanlov menyusi
    WAITING_FOR_EDIT_NAME,          // Ismni tahrirlash (mavjud edi)
    WAITING_FOR_EDIT_PASSPORT,      // Pasportni tahrirlash
    WAITING_FOR_EDIT_EXPERIENCE,    // Ish tajribasini tahrirlash
    WAITING_FOR_EDIT_PHONE          // Telefon raqamni tahrirlash
}