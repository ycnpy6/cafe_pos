INSERT OR IGNORE INTO categories (id, name, sort_order) VALUES (1, 'Cafe', 1);
INSERT OR IGNORE INTO categories (id, name, sort_order) VALUES (2, 'The', 2);
INSERT OR IGNORE INTO categories (id, name, sort_order) VALUES (3, 'Snacks', 3);

INSERT OR IGNORE INTO products (id, name, price, cost, category_id, stock, active)
VALUES (1, 'Espresso', 120, 40, 1, 50, 1);
INSERT OR IGNORE INTO products (id, name, price, cost, category_id, stock, active)
VALUES (2, 'Cappuccino', 180, 60, 1, 40, 1);
INSERT OR IGNORE INTO products (id, name, price, cost, category_id, stock, active)
VALUES (3, 'The Vert', 150, 45, 2, 30, 1);
INSERT OR IGNORE INTO products (id, name, price, cost, category_id, stock, active)
VALUES (4, 'Croissant', 100, 30, 3, 25, 1);

INSERT OR IGNORE INTO tag_groups (id, name, multi_select) VALUES (1, 'Type de lait', 0);
INSERT OR IGNORE INTO tag_groups (id, name, multi_select) VALUES (2, 'Supplements', 1);

INSERT OR IGNORE INTO tags (id, group_id, name, price_modifier) VALUES (1, 1, 'Lait entier', 0);
INSERT OR IGNORE INTO tags (id, group_id, name, price_modifier) VALUES (2, 1, 'Lait ecreme', 0);
INSERT OR IGNORE INTO tags (id, group_id, name, price_modifier) VALUES (3, 1, 'Lait avoine', 40);
INSERT OR IGNORE INTO tags (id, group_id, name, price_modifier) VALUES (4, 2, 'Shot extra', 80);
INSERT OR IGNORE INTO tags (id, group_id, name, price_modifier) VALUES (5, 2, 'Caramel', 50);

INSERT OR IGNORE INTO product_tag_groups (product_id, group_id) VALUES (2, 1);
INSERT OR IGNORE INTO product_tag_groups (product_id, group_id) VALUES (2, 2);
