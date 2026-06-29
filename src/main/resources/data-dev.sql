-- Seed data para perfil dev — corre solo cuando spring.profiles.active=dev
-- (activado por application-dev.properties: spring.sql.init.platform=dev, mode=always).
-- Idempotente: ON CONFLICT DO NOTHING + setval al final para alinear secuencias.
--
-- Password para ambos usuarios seed: "password" (bcrypt-10).

-- 3 categorías
INSERT INTO categories (id, name, description) VALUES
    (1, 'Cartas TCG', 'Cartas coleccionables: Pokémon, Magic, Yu-Gi-Oh y más.'),
    (2, 'Figuras',    'Figuras coleccionables: Funko Pop, estatuas, modelos y más.'),
    (3, 'Comics',     'Cómics Marvel, DC, manga y novelas gráficas.')
ON CONFLICT (name) DO NOTHING;

-- 2 usuarios (1 USER + 1 SELLER verified)
INSERT INTO users (id, name, email, password_hash, avatar_url, reputation, is_verified_seller, role, created_at) VALUES
    (1, 'Ada Lovelace', 'ada@yala.pe',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        NULL, 0.0, FALSE, 'USER', NOW()),
    (2, 'Sebas Vendedor', 'sebas@yala.pe',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        NULL, 4.8, TRUE, 'SELLER', NOW())
ON CONFLICT (email) DO NOTHING;

-- 2 listings publicadas por el seller (id=2)
INSERT INTO listings (id, title, description, mode, fixed_price, condition, status, created_at, seller_id, category_id) VALUES
    (1,
        'Funko Pop - Pikachu Glow in the Dark',
        'Funko Pop edición limitada de Pikachu que brilla en la oscuridad. Caja sellada.',
        'FIXED', 250.00, 'Near Mint', 'ACTIVE', NOW(), 2, 2),
    (2,
        'Charizard 1st Edition PSA 9',
        'Carta Charizard del Base Set original (1st edition) certificada PSA 9 — Mint.',
        'AUCTION', NULL, 'PSA 9 — Mint', 'ACTIVE', NOW(), 2, 1)
ON CONFLICT (id) DO NOTHING;

-- 1 active auction sobre el listing #2 (Charizard)
INSERT INTO auctions (id, version, starting_price, current_price, started_at, ends_at, status, listing_id, winner_id) VALUES
    (1, 0, 1000.00, 1000.00, NOW(), NOW() + INTERVAL '7 days', 'ACTIVE', 2, NULL)
ON CONFLICT (id) DO NOTHING;

-- Alinear secuencias para que próximas inserciones via JPA usen IDs > los seedeados
SELECT setval(pg_get_serial_sequence('categories', 'id'), (SELECT COALESCE(MAX(id), 1) FROM categories));
SELECT setval(pg_get_serial_sequence('users',      'id'), (SELECT COALESCE(MAX(id), 1) FROM users));
SELECT setval(pg_get_serial_sequence('listings',   'id'), (SELECT COALESCE(MAX(id), 1) FROM listings));
SELECT setval(pg_get_serial_sequence('auctions',   'id'), (SELECT COALESCE(MAX(id), 1) FROM auctions));
