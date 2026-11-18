package com.nashi.health;

import com.mongodb.MongoTimeoutException;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(mongo)).build();

    @Test
    void upWhenMongoAnswersPing() throws Exception {
        when(mongo.executeCommand(anyString())).thenReturn(new Document("ok", 1.0));

        mvc.perform(get("/api/v2/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    @Test
    void downWithoutExceptionTextWhenMongoFails() throws Exception {
        when(mongo.executeCommand(anyString())).thenThrow(new MongoTimeoutException("secret-detail"));

        mvc.perform(get("/api/v2/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"DOWN\"}", JsonCompareMode.STRICT))
                .andExpect(content().string(not(containsString("secret-detail"))));
    }

    @Test
    void downWhenPingIsNotOk() throws Exception {
        when(mongo.executeCommand(anyString())).thenReturn(new Document("ok", 0.0));

        mvc.perform(get("/api/v2/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", JsonCompareMode.STRICT));
    }
}
