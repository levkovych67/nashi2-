package com.nashi.util;

public class AISchema {


    // Схема для списку міст. Strict mode вимагає об'єкт на верхньому рівні, тому загортаємо в "cities".
    public static final String GEO_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "cities": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string", "description": "Name of the city in Ukrainian language" },
                      "region": { "type": "string", "description": "Region enum code, e.g. LVIV" },
                      "regionUA": { "type": "string", "description": "Oblast name in ukrainian language, e.g. Львівська область" },
                      "latitude": { "type": "number", "description": "Exact latitude of city center" },
                      "longitude": { "type": "number", "description": "Exact longitude of city center" },
                      "latitudeSomewhereInTheCity": { "type": "number", "description": "Random latitude somewhere within the city boundaries" },
                      "longitudeSomewhereInTheCity": { "type": "number", "description": "Random longitude somewhere within the city boundaries" }
                    },
                    "required": ["name", "region","regionUA", "latitude", "longitude", "latitudeSomewhereInTheCity", "longitudeSomewhereInTheCity"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["cities"],
              "additionalProperties": false
            }
            """;

    public static final String CITIES_SYSTEM_INSTRUCTION = """
            You are a precise Geocoding Service for Ukraine.
            Task: Find cities or villages in the specified Region that match the user's search query.
            
            Rules:
            1. For each city, provide TWO sets of coordinates:
               - 'latitude' and 'longitude': The EXACT coordinates of the city center (main square, city hall, or geographic center)
               - 'latitudeSomewhereInTheCity' and 'longitudeSomewhereInTheCity': A random point WITHIN the city boundaries (could be a residential area, park, or any location inside the city)
            2. The random point should be at least 200-500 meters away from the city center, but still within city limits
            3. The 'region' field must match the input region exactly (e.g., 'LVIV')
            4. The 'regionUA' field must be the full Ukrainian name of the oblast (e.g., 'Львівська область')
            5. Return up to 10 best matches
            6. If the query is exact (e.g., 'Червоноград'), return that specific city
            7. Ensure city names start with a capital letter
            8. Be accurate with coordinates - use your knowledge of Ukrainian geography
            9. Name if the city should be in Ukrainian language
            
            Example:
            For "Львів":
            - latitude: 49.8397, longitude: 24.0297 (центр міста, площа Ринок)
            - latitudeSomewhereInTheCity: 49.8431, longitudeSomewhereInTheCity: 24.0114 (якийсь район у місті)
            """;

    public static final String SPLIT_TAGS_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "musicTags": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "5-10 specific musical genres"
                },
                "secondaryTags": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "5-10 atmospheric or mood keywords (e.g. 'Melancholic', 'Aggressive', 'Study', 'Party')."
                }
              },
              "required": ["musicTags", "secondaryTags"],
              "additionalProperties": false
            }
            """;

    public static final String SMM_SYSTEM_PROMPT = """
            You are a creative Social Media Manager for 'Nashi', a platform discovering Ukrainian underground music.
            Task: Write a short, engaging Instagram/Telegram post introducing a new artist.
            
            Style Guidelines:
            - Language: Ukrainian (lively, modern). Make sure post will be in Ukrainian language
            - Structure: Hook -> Interesting Fact/Vibe -> Call to Action.
            - Format: Use emojis, bold text (Markdown), and relevant hashtags.
            - Tone: Enthusiastic, supportive of local culture.
            - Length: Under 200 words.
            """;

    public static final String EVENT_COORDINATES_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "latitude": { "type": "number", "description": "The latitude of the venue" },
                "longitude": { "type": "number", "description": "The longitude of the venue" }
              },
              "required": ["latitude", "longitude"],
              "additionalProperties": false
            }
            """;
}
