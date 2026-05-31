-- CATEGORIES
INSERT OR IGNORE INTO categories (id, name, color, sort_order) VALUES
(1, 'Hot Beverages',  '#6B2D1A', 1),
(2, 'Cold Beverages', '#1A4A6B', 2),
(3, 'Sweets',         '#A0522D', 3),
(4, 'Salties',        '#7A4A1A', 4),
(5, 'Cards',          '#2E5A2E', 5),
(6, 'Additions',      '#4A3A6B', 6);

-- HOT BEVERAGES (category_id=1, is_prepared=1)
INSERT OR IGNORE INTO products (name, price, cost, category_id, stock, active, is_prepared) VALUES
('Macchiato',          0, 0, 1, 0, 1, 1),
('Drip Coffee',        0, 0, 1, 0, 1, 1),
('Hot Chocolate',      0, 0, 1, 0, 1, 1),
('Espresso',           0, 0, 1, 0, 1, 1),
('Mocha',              0, 0, 1, 0, 1, 1),
('Double Espresso',    0, 0, 1, 0, 1, 1),
('Vienna Coffee',      0, 0, 1, 0, 1, 1),
('Hot Tea',            0, 0, 1, 0, 1, 1),
('Dalgona Coffee',     0, 0, 1, 0, 1, 1),
('Latte',              0, 0, 1, 0, 1, 1),
('Hot Milk',           0, 0, 1, 0, 1, 1),
('Chocolate Latte',    0, 0, 1, 0, 1, 1),
('Chocolate Milk',     0, 0, 1, 0, 1, 1),
('Cappuccino',         0, 0, 1, 0, 1, 1);

-- COLD BEVERAGES (category_id=2, is_prepared=1)
INSERT OR IGNORE INTO products (name, price, cost, category_id, stock, active, is_prepared) VALUES
('Frappuccino Vanilla',          0, 0, 2, 0, 1, 1),
('Banana Juice',                 0, 0, 2, 0, 1, 1),
('Iced Espresso',                0, 0, 2, 0, 1, 1),
('Frappuccino Caramel',          0, 0, 2, 0, 1, 1),
('Chocolate Milkshake',          0, 0, 2, 0, 1, 1),
('Iced Latte',                   0, 0, 2, 0, 1, 1),
('Frappuccino Banana',           0, 0, 2, 0, 1, 1),
('Vanilla Milkshake',            0, 0, 2, 0, 1, 1),
('Iced Chocolate Latte',         0, 0, 2, 0, 1, 1),
('Caramel Milkshake',            0, 0, 2, 0, 1, 1),
('Banana Milkshake',             0, 0, 2, 0, 1, 1),
('Iced Tea',                     0, 0, 2, 0, 1, 1),
('Juice',                        0, 0, 2, 0, 1, 0),
('Banana Chocolate Milkshake',   0, 0, 2, 0, 1, 1),
('Orange Juice',                 0, 0, 2, 0, 1, 0),
('Frappuccino Coffee',           0, 0, 2, 0, 1, 1),
('Lemonade',                     0, 0, 2, 0, 1, 0);

-- SWEETS (category_id=3, is_prepared=0)
INSERT OR IGNORE INTO products (name, price, cost, category_id, stock, active, is_prepared) VALUES
('Br Speculoos',          0, 0, 3, 0, 1, 0),
('Br Caramelo',           0, 0, 3, 0, 1, 0),
('Nutella Cookie',        0, 0, 3, 0, 1, 0),
('Chocolate Cookie',      0, 0, 3, 0, 1, 0),
('Br Bueno',              0, 0, 3, 0, 1, 0),
('Br Simple',             0, 0, 3, 0, 1, 0),
('Salted Caramel',        0, 0, 3, 0, 1, 0),
('Cookies Bueno',         0, 0, 3, 0, 1, 0),
('Br Ferrero',            0, 0, 3, 0, 1, 0),
('Br Pistache',           0, 0, 3, 0, 1, 0),
('Salbuz',                0, 0, 3, 0, 1, 0),
('Kinder Cookie',         0, 0, 3, 0, 1, 0),
('Pain au Chocolat',      0, 0, 3, 0, 1, 0),
('Br Oreo',               0, 0, 3, 0, 1, 0),
('Zlabiya',               0, 0, 3, 0, 1, 0),
('Lemon Bar',             0, 0, 3, 0, 1, 0),
('Croissant',             0, 0, 3, 0, 1, 0),
('Classic Cookies',       0, 0, 3, 0, 1, 0),
('Brownies',              0, 0, 3, 0, 1, 0),
('Dark Chocolate Cookie', 0, 0, 3, 0, 1, 0),
('FM''s Cookies',         0, 0, 3, 0, 1, 0),
('Cheese Cake',           0, 0, 3, 0, 1, 0),
('Donut',                 0, 0, 3, 0, 1, 0),
('Donut Gourmand',        0, 0, 3, 0, 1, 0),
('Donut Smile',           0, 0, 3, 0, 1, 0);

-- SALTIES (category_id=4, is_prepared=0)
INSERT OR IGNORE INTO products (name, price, cost, category_id, stock, active, is_prepared) VALUES
('Mini Pizza',    0, 0, 4, 0, 1, 0),
('Mini Burger',   0, 0, 4, 0, 1, 0),
('Club Sandwich', 0, 0, 4, 0, 1, 0),
('Mini Tacos',    0, 0, 4, 0, 1, 0),
('Bagels',        0, 0, 4, 0, 1, 0),
('Mini Sandwich', 0, 0, 4, 0, 1, 0),
('Pop Corn',      0, 0, 4, 0, 1, 0);

-- CARDS (category_id=5): no seed products

-- ADDITIONS supplement groups
INSERT OR IGNORE INTO tag_groups (id, name, multi_select) VALUES
(1, 'Additions', 1),
(2, 'Type de lait', 0),
(3, 'Sucre', 0),
(4, 'Taille', 0);

-- Additions tags
INSERT OR IGNORE INTO tags (group_id, name, price_modifier) VALUES
(1, 'Chocolate',            0),
(1, 'Hazelnut Syrup',       0),
(1, 'Iced',                 0),
(1, 'Salted Caramel Syrup', 0),
(1, 'Vanilla Syrup',        0),
(1, 'Milk',                 0),
(1, 'Caramel Syrup',        0),
(1, 'Money Back Espece',    0);

-- Type de lait tags
INSERT OR IGNORE INTO tags (group_id, name, price_modifier) VALUES
(2, 'Lait entier',    0),
(2, 'Lait demi',      0),
(2, 'Lait d''avoine', 0),
(2, 'Lait de soja',   0),
(2, 'Sans lait',      0);

-- Sucre tags
INSERT OR IGNORE INTO tags (group_id, name, price_modifier) VALUES
(3, 'Sans sucre', 0),
(3, '1 sucre',    0),
(3, '2 sucres',   0);

-- Taille tags
INSERT OR IGNORE INTO tags (group_id, name, price_modifier) VALUES
(4, 'Small',  0),
(4, 'Medium', 0),
(4, 'Large',  0);

-- Default app settings and admin PIN (1234)
INSERT OR IGNORE INTO app_settings (key, value) VALUES
('admin_pin_hash', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4'),
('app_name', 'Common Grounds'),
('theme', 'light'),
('brand_primary', '#6B2D1A'),
('brand_bg', '#F5ECD7'),
('tva_percent', '0'),
('opening_fund', '0'),
('session_timeout_min', '120'),
('low_stock_threshold', '5'),
('language', 'fr');

-- Default users
INSERT OR IGNORE INTO users (name, pin, role) VALUES
('Manager', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', 'MANAGER'),
('Barista', '', 'BARISTA');
