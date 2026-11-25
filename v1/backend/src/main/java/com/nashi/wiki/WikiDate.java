package com.nashi.wiki;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Wikidata date cut to the precision it really has.
 *
 * @param iso  "1814-03-09", "1814-03" or "1814"
 * @param year the year as a number
 */
public record WikiDate(String iso, int year) {

    private static final String JULIAN = "http://www.wikidata.org/entity/Q1985786";
    private static final Pattern TIME = Pattern.compile("^\\+(\\d{1,9})-(\\d{2})-(\\d{2})T.*");

    /** Returns null for precision below a year, a negative (BCE) time or a time that cannot be read. */
    public static WikiDate of(String wikidataTime, int precision) {
        return of(wikidataTime, precision, null);
    }

    /**
     * Same, but a day-precision date in the Julian calendar (Q1985786) is converted to Gregorian. Month and year
     * precision stay as given.
     */
    public static WikiDate of(String wikidataTime, int precision, String calendarModel) {
        if (wikidataTime == null || precision < 9) {
            return null;
        }
        Matcher m = TIME.matcher(wikidataTime);
        if (!m.matches()) {
            return null;
        }
        int year;
        try {
            year = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        String yearText = String.format("%04d", year);
        if (precision == 9) {
            return new WikiDate(yearText, year);
        }
        if (precision == 10) {
            return new WikiDate(yearText + "-" + m.group(2), year);
        }
        int month = Integer.parseInt(m.group(2));
        int day = Integer.parseInt(m.group(3));
        if (JULIAN.equals(calendarModel) && month >= 1 && month <= 12 && day >= 1) {
            int[] g = julianToGregorian(year, month, day);
            return new WikiDate(String.format("%04d-%02d-%02d", g[0], g[1], g[2]), g[0]);
        }
        return new WikiDate(yearText + "-" + m.group(2) + "-" + m.group(3), year);
    }

    /** Julian calendar date to Gregorian, through the Julian Day Number. Returns {year, month, day}. */
    static int[] julianToGregorian(int y, int m, int d) {
        int a = (14 - m) / 12;
        int y2 = y + 4800 - a;
        int m2 = m + 12 * a - 3;
        int jdn = d + (153 * m2 + 2) / 5 + 365 * y2 + y2 / 4 - 32083;
        int f = jdn + 1401 + (((4 * jdn + 274277) / 146097) * 3) / 4 - 38;
        int e = 4 * f + 3;
        int g = (e % 1461) / 4;
        int h = 5 * g + 2;
        int day = (h % 153) / 5 + 1;
        int month = ((h / 153 + 2) % 12) + 1;
        int year = e / 1461 - 4716 + (12 + 2 - month) / 12;
        return new int[]{year, month, day};
    }
}
