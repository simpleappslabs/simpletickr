CREATE TABLE fx_rates
(
    base_currency  VARCHAR(3)     NOT NULL,
    quote_currency VARCHAR(3)     NOT NULL,
    date           DATE           NOT NULL,
    rate           NUMERIC(19, 8) NOT NULL,
    PRIMARY KEY (base_currency, quote_currency, date)
);

CREATE INDEX idx_fx_rates_pair_date ON fx_rates (base_currency, quote_currency, date DESC);
