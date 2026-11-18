package com.nashi.geo;

/** The 27 regions of the contract (docs/02-contracts.md, section 2), in contract order. */
public enum Region {
    CRIMEA("Автономна Республіка Крим"),
    VINNYTSIA("Вінницька область"),
    VOLYN("Волинська область"),
    DNIPROPETROVSK("Дніпропетровська область"),
    DONETSK("Донецька область"),
    ZHYTOMYR("Житомирська область"),
    ZAKARPATTIA("Закарпатська область"),
    ZAPORIZHZHIA("Запорізька область"),
    IVANO_FRANKIVSK("Івано-Франківська область"),
    KYIV("Київська область"),
    KIROVOHRAD("Кіровоградська область"),
    LUHANSK("Луганська область"),
    LVIV("Львівська область"),
    MYKOLAIV("Миколаївська область"),
    ODESA("Одеська область"),
    POLTAVA("Полтавська область"),
    RIVNE("Рівненська область"),
    SUMY("Сумська область"),
    TERNOPIL("Тернопільська область"),
    KHARKIV("Харківська область"),
    KHERSON("Херсонська область"),
    KHMELNYTSKYI("Хмельницька область"),
    CHERKASY("Черкаська область"),
    CHERNIVTSI("Чернівецька область"),
    CHERNIHIV("Чернігівська область"),
    KYIV_CITY("Київ"),
    SEVASTOPOL_CITY("Севастополь");

    private final String uaName;

    Region(String uaName) {
        this.uaName = uaName;
    }

    public String uaName() {
        return uaName;
    }
}
