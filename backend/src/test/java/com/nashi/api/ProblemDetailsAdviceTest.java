package com.nashi.api;

import com.mongodb.MongoSocketReadTimeoutException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemDetailsAdviceTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ProblemDetailsAdvice())
            .build();

    @Test
    void validationFailedHasCodeAndErrors() throws Exception {
        problem(mvc.perform(get("/test/validation")), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.title").value("Некоректні дані"))
                .andExpect(jsonPath("$.detail").value("Перевірте поля: summary"))
                .andExpect(jsonPath("$.instance").value("/test/validation"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("summary"))
                .andExpect(jsonPath("$.errors[0].message").value("Мінімум 30 символів"));
    }

    @Test
    void rateLimitedSetsRetryAfter() throws Exception {
        problem(mvc.perform(get("/test/rate-limited")), 429, "RATE_LIMITED")
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.title").value("Забагато запитів"));
    }

    @Test
    void unknownExceptionHidesMessage() throws Exception {
        problem(mvc.perform(get("/test/boom")), 500, "INTERNAL")
                .andExpect(jsonPath("$.title").value("Внутрішня помилка"))
                .andExpect(content().string(not(containsString("secret-detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void databaseUnavailableIs503WithoutExceptionText() throws Exception {
        for (String path : List.of("/test/db-down", "/test/mongo-timeout", "/test/wrapped-timeout")) {
            problem(mvc.perform(get(path)), 503, "UNAVAILABLE")
                    .andExpect(jsonPath("$.title").value("Сервіс тимчасово недоступний"))
                    .andExpect(jsonPath("$.detail").value("Сервіс тимчасово недоступний. Спробуйте пізніше"))
                    .andExpect(content().string(not(containsString("secret-host"))))
                    .andExpect(content().string(not(containsString("Exception"))));
        }
    }

    @Test
    void apiUnavailableExceptionIs503WithItsDetail() throws Exception {
        problem(mvc.perform(get("/test/busy")), 503, "UNAVAILABLE")
                .andExpect(jsonPath("$.detail").value("Зайнято"));
    }

    @Test
    void maxUploadSizeIs413() throws Exception {
        problem(mvc.perform(get("/test/max-upload")), 413, "PAYLOAD_TOO_LARGE")
                .andExpect(jsonPath("$.title").value("Завеликий запит"));
    }

    @Test
    void payloadTooLargeExceptionIs413() throws Exception {
        problem(mvc.perform(get("/test/too-large")), 413, "PAYLOAD_TOO_LARGE");
    }

    @Test
    void notFoundIs404() throws Exception {
        problem(mvc.perform(get("/test/not-found")), 404, "NOT_FOUND")
                .andExpect(jsonPath("$.title").value("Не знайдено"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void unsupportedMediaIs415WithField() throws Exception {
        problem(mvc.perform(get("/test/unsupported-media")), 415, "UNSUPPORTED_MEDIA_TYPE")
                .andExpect(jsonPath("$.errors[0].field").value("images[2]"));
    }

    @Test
    void invalidBodyListsFields() throws Exception {
        problem(mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"summary\":\"short\"}")), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'summary')].message").value("Мінімум 30 символів"));
    }

    @Test
    void invalidParameterListsParameterName() throws Exception {
        problem(mvc.perform(get("/test/params").param("limit", "0")), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("limit"))
                .andExpect(jsonPath("$.errors[0].message").value("Щонайменше 1"));
    }

    @Test
    void malformedParameterIs400() throws Exception {
        problem(mvc.perform(get("/test/params").param("limit", "abc")), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void missingParameterIs400() throws Exception {
        problem(mvc.perform(get("/test/params")), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void unreadableJsonIs400WithoutParserDetails() throws Exception {
        problem(mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\": secret-detail")),
                400, "VALIDATION_FAILED")
                .andExpect(content().string(not(containsString("secret-detail"))));
    }

    @Test
    void unsupportedContentTypeIs415() throws Exception {
        problem(mvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("x")), 415,
                "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unknownPathIs404() throws Exception {
        problem(mvc.perform(get("/test/no-such-path")), 404, "NOT_FOUND");
    }

    private static ResultActions problem(ResultActions result, int status, String code) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    record Body(@NotBlank String name, @Size(min = 30, message = "Мінімум 30 символів") String summary) {
    }

    @RestController
    @RequestMapping("/test")
    static class ThrowingController {

        @GetMapping("/validation")
        void validation() {
            throw new ValidationFailedException(List.of(new FieldViolation("summary", "Мінімум 30 символів")));
        }

        @GetMapping("/rate-limited")
        void rateLimited() {
            throw new RateLimitedException(7);
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret-detail");
        }

        @GetMapping("/db-down")
        void dbDown() {
            throw new DataAccessResourceFailureException("Timed out connecting to secret-host:27017");
        }

        @GetMapping("/mongo-timeout")
        void mongoTimeout() {
            throw new MongoTimeoutException("Timed out after 30000 ms waiting for secret-host:27017");
        }

        @GetMapping("/wrapped-timeout")
        void wrappedTimeout() {
            throw new UncategorizedMongoDbException("secret-host",
                    new MongoSocketReadTimeoutException("secret-host", new ServerAddress("secret-host"), null));
        }

        @GetMapping("/busy")
        void busy() {
            throw new UnavailableException("Зайнято");
        }

        @GetMapping("/max-upload")
        void maxUpload() {
            throw new MaxUploadSizeExceededException(55L * 1024 * 1024);
        }

        @GetMapping("/too-large")
        void tooLarge() {
            throw new PayloadTooLargeException();
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("musician");
        }

        @GetMapping("/unsupported-media")
        void unsupportedMedia() {
            throw new UnsupportedMediaException("images[2]");
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/params")
        void params(@RequestParam("limit") @Min(value = 1, message = "Щонайменше 1") int limit) {
        }
    }
}
