package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryCleanerTest {

    private static final String LYNIV = "Оксана Ярославівна Линів (лат. Oksana Lyniv, нар. 6 січня 1978(19780106), "
            + "Броди, Львівська область, УРСР) — українська диригентка. Перша жінка, що диригувала на Байройтському "
            + "фестивалі.";

    @Test
    void removesBirthParenthesesAndGluedDates() {
        assertThat(SummaryCleaner.summary(LYNIV))
                .startsWith("Оксана Ярославівна Линів — українська диригентка.")
                .isEqualTo("Оксана Ярославівна Линів — українська диригентка. Перша жінка, що диригувала на "
                        + "Байройтському фестивалі.");
    }

    @Test
    void keepsParenthesesWithoutDates() {
        String intro = "«Океан Ельзи» (гурт) — український рок-гурт зі Львова.";

        assertThat(SummaryCleaner.summary(intro)).isEqualTo(intro);
    }

    @Test
    void handlesNestedParentheses() {
        assertThat(SummaryCleaner.summary(
                "Іван Петренко (справжнє ім'я — Петро Іваненко (за паспортом), 1950–2001) — співак."))
                .isEqualTo("Іван Петренко — співак.");
        assertThat(SummaryCleaner.summary("Гурт (назва (від слова «гурт»)) — колектив."))
                .isEqualTo("Гурт (назва (від слова «гурт»)) — колектив.");
    }

    @Test
    void summaryCutAtSentenceWithEllipsis() {
        String sentence = "Це речення про музиканта має рівно стільки слів, скільки треба для перевірки. ";
        String intro = sentence.repeat(10).trim();

        String summary = SummaryCleaner.summary(intro);

        assertThat(summary).hasSizeLessThanOrEqualTo(300).endsWith(".…");
        assertThat(summary.substring(0, summary.length() - 1)).isEqualTo(
                sentence.repeat(summary.length() / sentence.length()).trim());
    }

    @Test
    void summaryAndDescriptionKeepTextThatFits() {
        String exactly300 = "а".repeat(299) + ".";

        assertThat(SummaryCleaner.summary(exactly300)).isEqualTo(exactly300);
        assertThat(SummaryCleaner.description(exactly300)).isEqualTo(exactly300);
    }

    @Test
    void descriptionIsTheWholeCleanIntroUpTo2000() {
        String sentence = "Друге речення вступу статті про гурт, яке повторюється багато разів поспіль. ";
        String intro = "Гурт (засн. 1994) — український гурт. " + sentence.repeat(40);

        String description = SummaryCleaner.description(intro);

        assertThat(description).startsWith("Гурт — український гурт. Друге речення").endsWith(".…");
        assertThat(description.length()).isLessThanOrEqualTo(2000).isGreaterThan(1900);
    }

    @Test
    void stripsGluedEightDigitDatesAnywhere() {
        String intro = "Співачка (псевдонім Зірка) — виконавиця. Народилася 6 січня 1978(19780106) у Бродах, "
                + "дебютувала 1995(19950301) .";

        assertThat(SummaryCleaner.description(intro))
                .isEqualTo("Співачка — виконавиця. Народилася 6 січня 1978 у Бродах, дебютувала 1995.");
    }

    @Test
    void onlyTheFirstSentenceLosesItsParentheses() {
        assertThat(SummaryCleaner.summary("Співак (нар. 1970) — артист. Альбом (2001) вийшов у Києві."))
                .isEqualTo("Співак — артист. Альбом (2001) вийшов у Києві.");
    }

    @Test
    void removesStressMarksAndSpacesBeforePunctuation() {
        assertThat(SummaryCleaner.summary("Мико́ла Лисенко (1842–1912) , композитор ; диригент  ."))
                .isEqualTo("Микола Лисенко, композитор; диригент.");
    }

    @Test
    void keepsAnUnclosedParenthesis() {
        assertThat(SummaryCleaner.summary("Гурт (засн. 1994 — колектив")).isEqualTo("Гурт (засн. 1994 — колектив");
    }

    @Test
    void blankIsNull() {
        assertThat(SummaryCleaner.summary(null)).isNull();
        assertThat(SummaryCleaner.summary("   ")).isNull();
        assertThat(SummaryCleaner.description(null)).isNull();
    }
}
