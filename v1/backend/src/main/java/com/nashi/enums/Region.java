package com.nashi.enums;

import lombok.Getter;

@Getter
public enum Region {
    // --- Autonomous Republic ---
    CRIMEA("Автономна Республіка Крим", "Autonomous Republic of Crimea"),

    // --- Oblasts (West) ---
    LVIV("Львівська область", "Lviv Oblast"),
    VOLYN("Волинська область", "Volyn Oblast"),
    RIVNE("Рівненська область", "Rivne Oblast"),
    TERNOPIL("Тернопільська область", "Ternopil Oblast"),
    IVANO_FRANKIVSK("Івано-Франківська область", "Ivano-Frankivsk Oblast"),
    ZAKARPATTIA("Закарпатська область", "Zakarpattia Oblast"),
    CHERNIVTSI("Чернівецька область", "Chernivtsi Oblast"),
    KHMELNYTSKYI("Хмельницька область", "Khmelnytskyi Oblast"),

    // --- Oblasts (North/Center) ---
    ZHYTOMYR("Житомирська область", "Zhytomyr Oblast"),
    KYIV("Київська область", "Kyiv Oblast"),
    CHERNIHIV("Чернігівська область", "Chernihiv Oblast"),
    SUMY("Сумська область", "Sumy Oblast"),
    VINNYTSIA("Вінницька область", "Vinnytsia Oblast"),
    CHERKASY("Черкаська область", "Cherkasy Oblast"),
    POLTAVA("Полтавська область", "Poltava Oblast"),

    // --- Oblasts (South) ---
    ODESA("Одеська область", "Odesa Oblast"),
    MYKOLAIV("Миколаївська область", "Mykolaiv Oblast"),
    KHERSON("Херсонська область", "Kherson Oblast"),

    // --- Oblasts (East/Center) ---
    // Note: Constitutionally these retain old names despite center city renaming
    KIROVOHRAD("Кіровоградська область", "Kirovohrad Oblast"), 
    DNIPROPETROVSK("Дніпропетровська область", "Dnipropetrovsk Oblast"),
    
    KHARKIV("Харківська область", "Kharkiv Oblast"),
    ZAPORIZHZHIA("Запорізька область", "Zaporizhzhia Oblast"),
    DONETSK("Донецька область", "Donetsk Oblast"),
    LUHANSK("Луганська область", "Luhansk Oblast");

    private final String uaName;
    private final String enName;

    Region(String uaName, String enName) {
        this.uaName = uaName;
        this.enName = enName;
    }
}