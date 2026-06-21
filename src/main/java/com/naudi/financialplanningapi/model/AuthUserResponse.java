package com.naudi.financialplanningapi.model;

public record AuthUserResponse(
    boolean authenticated,
    boolean admin,
    boolean premium,
    boolean allowAdminEdit,
    boolean encryptionExempt,
    boolean termsAccepted,
    String requiredTermsVersion,
    String acceptedTermsVersion,
    String acceptedTermsAt,
    String userSub,
    String email,
    String name,
    String pictureUrl
) {
    public static AuthUserResponse unauthenticated() {
        return new AuthUserResponse(false, false, false, false, false, false, null, null, null, null, null, null, null);
    }
}