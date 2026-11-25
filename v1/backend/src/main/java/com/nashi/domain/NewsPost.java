package com.nashi.domain;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "news_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsPost {

    @Id
    private String id;

    private String content; 
    private String imageKey;
    
    private String audioKey;

    private String artistId;
    private String artistSlug;
    private String artistName;

    @CreatedDate
    private LocalDateTime createdAt;
}