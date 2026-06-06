INSERT INTO assets (ticker, name, type, currency) VALUES
    -- Stocks
    ('AAPL',  'Apple Inc.',                    'STOCK',  'USD'),
    ('MSFT',  'Microsoft Corporation',         'STOCK',  'USD'),
    ('GOOGL', 'Alphabet Inc.',                 'STOCK',  'USD'),
    ('AMZN',  'Amazon.com Inc.',               'STOCK',  'USD'),
    ('NVDA',  'NVIDIA Corporation',            'STOCK',  'USD'),
    ('META',  'Meta Platforms Inc.',           'STOCK',  'USD'),
    -- ETFs
    ('SPY',   'SPDR S&P 500 ETF Trust',        'ETF',    'USD'),
    ('QQQ',   'Invesco QQQ Trust',             'ETF',    'USD'),
    ('VTI',   'Vanguard Total Stock Market',   'ETF',    'USD'),
    ('VWCE',  'Vanguard FTSE All-World',       'ETF',    'EUR'),
    -- Crypto
    ('BTC',   'Bitcoin',                       'CRYPTO', 'USD'),
    ('ETH',   'Ethereum',                      'CRYPTO', 'USD')
ON CONFLICT (ticker) DO NOTHING;
