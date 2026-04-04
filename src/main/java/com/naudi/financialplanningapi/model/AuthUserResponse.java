package com.naudi.financialplanningapi.model;

public record AuthUserResponse(
    boolean authenticated,
    String email,
    String name,
    String pictureUrl
) {
    public static AuthUserResponse unauthenticated() {
        return new AuthUserResponse(false, null, null, null);
    }
}