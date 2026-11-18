package com.nashi.admin;

import com.nashi.admin.AdminCommand.Action;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCommandTest {

    @Test
    void parsesAllCommandsWithAndWithoutBotSuffix() {
        assertThat(AdminCommand.parse("/hide@nashi_bot okean-elzy"))
                .contains(new AdminCommand(Action.HIDE, "okean-elzy"));
        assertThat(AdminCommand.parse("/show Q2017541")).contains(new AdminCommand(Action.SHOW, "Q2017541"));
        assertThat(AdminCommand.parse("/stats")).contains(new AdminCommand(Action.STATS, ""));
        assertThat(AdminCommand.parse("/wiki_import@nashi_bot")).contains(new AdminCommand(Action.WIKI_IMPORT, ""));
        assertThat(AdminCommand.parse("/audio_resolve")).contains(new AdminCommand(Action.AUDIO_RESOLVE, ""));
        assertThat(AdminCommand.parse(" /ping ")).contains(new AdminCommand(Action.PING, ""));
    }

    @Test
    void bareHideHasEmptyArgumentAndUsage() {
        AdminCommand command = AdminCommand.parse("/hide").orElseThrow();
        assertThat(command.argument()).isEmpty();
        assertThat(command.usage()).isEqualTo("Використання: /hide <slug або Q-id>");
    }

    @Test
    void otherTextIsNotACommand() {
        assertThat(AdminCommand.parse("/hidex a")).isEmpty();
        assertThat(AdminCommand.parse("/foo")).isEmpty();
        assertThat(AdminCommand.parse("hide a")).isEmpty();
        assertThat(AdminCommand.parse(null)).isEmpty();
    }
}
