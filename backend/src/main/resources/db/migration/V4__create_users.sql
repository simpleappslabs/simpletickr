CREATE TABLE users (
    id       BIGSERIAL    PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE
);

INSERT INTO users (username) VALUES ('default');