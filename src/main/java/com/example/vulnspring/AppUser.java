package com.example.vulnspring;

public record AppUser(
        int id,
        String username,
        String password,
        String email,
        String displayName,
        String bio,
        String role,
        String apiKey
) {
}
