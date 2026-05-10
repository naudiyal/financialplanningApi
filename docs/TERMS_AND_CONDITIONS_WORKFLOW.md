# Terms And Conditions Workflow

This document describes where the Terms and Conditions text lives, how acceptance is enforced, and what must be updated when the legal copy changes.

## Where The Text Lives

The visible Terms and Conditions text is currently rendered by the frontend in `FinancialPlanningUI/src/App.tsx`.

The backend does not store the legal text body. It only stores:

- the currently required version via `app.terms.current-version`
- the user's acceptance metadata in `app_user_terms_acceptance`

## Current Backend Version Flag

The required version is configured in:

- `FinancialPlanningApi/src/main/resources/application.properties`

Property:

```properties
app.terms.current-version=${APP_TERMS_CURRENT_VERSION:2026-05-02-v2}
```

## Acceptance Storage

Acceptance records are stored in the `app_user_terms_acceptance` table. The table stores audit metadata such as:

- `user_sub`
- `email`
- `display_name`
- `terms_version`
- `accepted_at`
- `ip_address`
- `user_agent`

The table does not store the Terms and Conditions text.

## How To Change The Terms

1. Update the displayed legal copy in `FinancialPlanningUI/src/App.tsx`.
2. Bump `app.terms.current-version` in `FinancialPlanningApi/src/main/resources/application.properties`.
3. Deploy both the UI and API.
4. After deployment, users must accept the new version before accessing plan data again.

## Related Behavior

- Terms acceptance is required before plan loading and tracker access.
- First-time encrypted setup includes an Exit path that signs the user out (no server-side Terms reset).
- Exiting first-time setup does not force re-acceptance unless the required Terms version changes.
