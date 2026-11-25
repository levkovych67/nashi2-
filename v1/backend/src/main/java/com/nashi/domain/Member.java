package com.nashi.domain;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {
    // No @Id needed, as this is just part of the JSON structure
    private String firstName;
    private String lastName;
    private String city;
    private String role;
}