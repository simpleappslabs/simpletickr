CREATE TABLE user_settings
(
    id            BIGINT      NOT NULL DEFAULT 1,
    base_currency VARCHAR(3)  NOT NULL DEFAULT 'EUR',
    CONSTRAINT pk_user_settings PRIMARY KEY (id),
    CONSTRAINT single_row CHECK (id = 1)
);

INSERT INTO user_settings (id, base_currency) VALUES (1, 'EUR');
