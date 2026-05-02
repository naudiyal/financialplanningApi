CREATE TABLE IF NOT EXISTS app_user_terms_acceptance (
    user_sub VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    display_name VARCHAR(255),
    terms_version VARCHAR(64) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(64),
    user_agent TEXT,
    CONSTRAINT app_user_terms_acceptance_pkey PRIMARY KEY (user_sub, terms_version)
);

CREATE INDEX IF NOT EXISTS idx_app_user_terms_acceptance_email
    ON app_user_terms_acceptance (email);

CREATE INDEX IF NOT EXISTS idx_app_user_terms_acceptance_accepted_at
    ON app_user_terms_acceptance (accepted_at DESC);