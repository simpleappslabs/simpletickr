INSERT INTO accounts (user_id, name, account_type)
SELECT id, 'Test Brokerage', 'BROKERAGE' FROM users WHERE username = 'e2e-admin';
