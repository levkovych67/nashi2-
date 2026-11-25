package com.nashi.service.impl;


import com.nashi.domain.Artist;
import com.nashi.domain.Event;
import com.nashi.enums.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;

import static com.nashi.util.AISchema.*;


@Service
@Slf4j
public class AiRequestService {

    private final ChatModel chatModel;

    private final OpenAiChatOptions geoOptions;
    private final OpenAiChatOptions tagsOptions;
    private final OpenAiChatOptions socialMediaOptions;
    private final OpenAiChatOptions eventGeoOptions;

    public AiRequestService(ChatModel chatModel) {
        this.chatModel = chatModel;

        this.geoOptions = OpenAiChatOptions.builder()
                .model("gpt-4o") // gpt-4o-mini дешевший і чудово справляється з географією
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_SCHEMA)
                        .jsonSchema(ResponseFormat.JsonSchema.builder()
                                .name("CityLookupResponse")
                                .schema(GEO_SCHEMA)
                                .strict(true)
                                .build())
                        .build())
                .temperature(0.1) // Низька температура для точності координат
                .build();

        this.socialMediaOptions = OpenAiChatOptions.builder()
                .model("gpt-4o") // або gpt-4o-mini
                .temperature(0.4) // Більше креативу
                .build();

        this.eventGeoOptions = OpenAiChatOptions.builder()
                .model("gpt-4o") // gpt-4o handles specific venue locations better
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_SCHEMA)
                        .jsonSchema(ResponseFormat.JsonSchema.builder()
                                .name("EventCoordinatesResponse") // Unique name for this schema
                                .schema(EVENT_COORDINATES_SCHEMA) // The constant from AISchema
                                .strict(true)
                                .build())
                        .build())
                .temperature(0.3) // Keep temp low for factual coordinates
                .build();

        this.tagsOptions = OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_SCHEMA)
                        .jsonSchema(ResponseFormat.JsonSchema.builder()
                                .name("MusicAndVibeTags")
                                .schema(SPLIT_TAGS_SCHEMA)
                                .strict(true)
                                .build())
                        .build())
                .temperature(0.5)
                .build();
    }


    public String searchCities(Region region, String query) {
        String userPrompt = String.format("Oblast :%s. Region: %s. Search Query: %s", region.getUaName(), region.name(), query);
        Prompt prompt = new Prompt(CITIES_SYSTEM_INSTRUCTION + "\nInput: " + userPrompt, geoOptions);
        ChatResponse chatResponse = chatModel.call(prompt);
        String jsonResult = chatResponse.getResult().getOutput().getText();
        log.info("AI Geocoding Response: " + jsonResult);
        return jsonResult;
    }

    public String generateArtistTags(String name, String style, String bio) {
        String userPrompt = String.format(
                "Analyze this artist.\nName: %s\nStyle provided: %s\nBio: %s\n\nTask: Extract specific genres into 'musicTags' and atmospheric keywords into 'secondaryTags'.Make sure that tags will be in english",
                name, style, bio
        );

        try {
            Prompt prompt = new Prompt(userPrompt, tagsOptions);
            ChatResponse response = chatModel.call(prompt);
            return response.getResult().getOutput().getText();

        } catch (Exception e) {
            log.error("AI Tag generation failed for artist: " + name, e);
            return "";
        }
    }

    public String generateSocialMediaPost(Artist artist) {
        if (artist == null) {
            return "Error: Artist object is null.";
        }

        // 2. Формуємо красивий контекст з об'єкта
        String musicTags = (artist.getMusicTags() != null)
                ? String.join(", ", artist.getMusicTags())
                : "N/A";

        String vibeTags = (artist.getSecondaryTags() != null)
                ? String.join(", ", artist.getSecondaryTags())
                : "N/A";

        String regionName = (artist.getRegion() != null) ? artist.getRegion().name() : "Ukraine";

        // Створюємо читабельний опис для AI
        String artistContext = String.format("""
                Artist Name: %s
                Style: %s
                Region: %s
                Music Genres (Tags): %s
                Vibe/Mood: %s
                Biography Highlights: %s
                """,
                artist.getName(),
                artist.getStyle(),
                regionName,
                musicTags,
                vibeTags,
                // Обрізаємо біографію, якщо вона дуже довга, щоб зекономити токени
                artist.getBiography() != null ? artist.getBiography() : "No biography provided."
        );

        try {
            Prompt prompt = new Prompt(SMM_SYSTEM_PROMPT + "\n\nArtist Data:\n" + artistContext, socialMediaOptions);
            ChatResponse response = chatModel.call(prompt);

            String postText = response.getResult().getOutput().getText();
            log.info("Generated SMM Post for {}: {}", artist.getName(), postText);

            return postText;

        } catch (Exception e) {
            log.error("Failed to generate SMM post for artist: " + artist.getName(), e);
            return "Не вдалося згенерувати пост. Спробуйте пізніше.";
        }
    }

    public String getEventCoordinates(Event event) {
        if (event == null) {
            log.error("Cannot get coordinates: Event is null");
            return null;
        }

        String regionName = (event.getRegion() != null) ? event.getRegion().name() : "Ukraine";

        String userPrompt = String.format(
                """
                Geocode the following location in Ukraine.
                
                Priority for coordinates:
                1. Exact location of the 'Venue'.
                2. If the venue is not found or does not have a fixed location, return the geometric center of the 'City'.
                
                Location Details:
                - Venue: %s
                - City: %s
                - Region: %s
                """,
                event.getVenue(),
                event.getCity(),
                regionName
        );

        try {
            Prompt prompt = new Prompt(userPrompt, eventGeoOptions);
            ChatResponse response = chatModel.call(prompt);

            return response.getResult().getOutput().getText();

        } catch (Exception e) {
            log.error("AI Event Geocoding failed for event: " + event.getTitle(), e);
            return null; // or return new EventCoordinatesDTO(0.0, 0.0);
        }
    }

    // ... inside AiRequestService

    public String generateEventSocialMediaPost(Event event) {
        if (event == null) return "Error: Event is null.";

        String regionName = (event.getRegion() != null) ? event.getRegion().name() : "Ukraine";

        String eventContext = String.format("""
                Event Title: %s
                Artist/Organizer: %s
                Date: %s
                City: %s (%s)
                Venue: %s
                Description: %s
                Ticket URL: %s
                """,
                event.getTitle(),
                event.getArtistName() != null ? event.getArtistName() : "Unknown",
                event.getDateTime(),
                event.getCity(), regionName,
                event.getVenue(),
                event.getDescription(),
                event.getTicketUrl()
        );

        // Reuse socialMediaOptions or create specific event options
        try {
            String systemPrompt = "You are an SMM manager for a Ukrainian music platform. " +
                    "Write a short, engaging Telegram post inviting people to this event. " +
                    "Use emojis. Include the date and location clearly.";

            Prompt prompt = new Prompt(systemPrompt + "\n\nEvent Data:\n" + eventContext, socialMediaOptions);
            ChatResponse response = chatModel.call(prompt);

            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("Failed to generate SMM post for event: " + event.getTitle(), e);
            return "Запрошуємо на " + event.getTitle() + "! Деталі скоро."; // Fallback
        }
    }
}