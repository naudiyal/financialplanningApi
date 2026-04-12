package com.naudi.financialplanningapi.model;

public record AuthUserResponse(
    boolean authenticated,
    boolean admin,
    String email,
    String name,
    String pictureUrl
) {
    public static AuthUserResponse unauthenticated() {
        return new AuthUserResponse(false, false, null, null, null);
    }
}