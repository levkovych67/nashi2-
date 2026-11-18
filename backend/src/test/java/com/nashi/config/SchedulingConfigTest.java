package com.nashi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    void schedulingIsOnByDefault() {
        runner.run(context -> assertThat(context).hasBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }

    @Test
    void schedulingCanBeSwitchedOff() {
        runner.withPropertyValues("nashi.scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }
}
