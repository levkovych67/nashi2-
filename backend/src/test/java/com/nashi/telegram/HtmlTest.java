package com.nashi.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlTest {

    @Test
    void htmlEscapes() {
        assertThat(Html.escape("<b>&")).isEqualTo("&lt;b&gt;&amp;");
        assertThat(Html.escape("say \"hi\"")).isEqualTo("say &quot;hi&quot;");
        assertThat(Html.escape(null)).isEmpty();
    }
}
