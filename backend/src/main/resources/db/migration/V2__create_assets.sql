CREATE TABLE assets
(
    id            BIGSERIAL PRIMARY KEY,
    ticker        varchar(20)  NOT NULL UNIQUE,
    name          varchar(255) NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    currency      VARCHAR(10)  NOT NULL,
    current_price NUMERIC(19, 6)
);