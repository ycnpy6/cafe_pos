CREATE TABLE IF NOT EXISTS app_meta (
  key TEXT PRIMARY KEY,
  value TEXT
);

CREATE TABLE IF NOT EXISTS app_settings (
  key TEXT PRIMARY KEY,
  value TEXT
);

CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  pin TEXT NOT NULL,
  role TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  color TEXT,
  sort_order INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS products (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  price REAL NOT NULL,
  cost REAL DEFAULT 0,
  category_id INTEGER REFERENCES categories(id),
  stock INTEGER DEFAULT 0,
  active INTEGER DEFAULT 1,
  is_prepared INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tag_groups (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  multi_select INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tags (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  group_id INTEGER REFERENCES tag_groups(id),
  name TEXT NOT NULL,
  price_modifier REAL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS product_tag_groups (
  product_id INTEGER REFERENCES products(id),
  group_id INTEGER REFERENCES tag_groups(id),
  PRIMARY KEY (product_id, group_id)
);

CREATE TABLE IF NOT EXISTS customers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  card_uid TEXT UNIQUE NOT NULL,
  balance REAL DEFAULT 0,
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS account_transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  customer_id INTEGER REFERENCES customers(id),
  amount REAL NOT NULL,
  description TEXT,
  user_id INTEGER REFERENCES users(id),
  created_at TEXT DEFAULT (datetime('now'))
);

ALTER TABLE account_transactions ADD COLUMN balance_after REAL DEFAULT 0;
ALTER TABLE account_transactions ADD COLUMN order_id INTEGER REFERENCES orders(id);

CREATE TABLE IF NOT EXISTS work_periods (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  opened_at TEXT NOT NULL,
  closed_at TEXT,
  opened_by INTEGER REFERENCES users(id),
  closed_by INTEGER REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS orders (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  customer_id INTEGER REFERENCES customers(id),
  payment_type TEXT NOT NULL,
  total REAL NOT NULL,
  user_id INTEGER REFERENCES users(id),
  work_period_id INTEGER REFERENCES work_periods(id),
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS order_lines (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id INTEGER REFERENCES orders(id),
  product_id INTEGER REFERENCES products(id),
  quantity INTEGER DEFAULT 1,
  unit_price REAL NOT NULL,
  line_total REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS order_line_tags (
  line_id INTEGER REFERENCES order_lines(id),
  tag_id INTEGER REFERENCES tags(id),
  PRIMARY KEY (line_id, tag_id)
);

CREATE TABLE IF NOT EXISTS stock_movements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id INTEGER REFERENCES products(id),
  quantity INTEGER NOT NULL,
  reason TEXT,
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS ingredients (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  unit TEXT NOT NULL DEFAULT 'UNIT',
  unit_base TEXT DEFAULT 'UNIT',
  unit_factor REAL NOT NULL DEFAULT 1,
  package_size REAL NOT NULL DEFAULT 1,
  package_price REAL NOT NULL DEFAULT 0,
  stock_quantity REAL NOT NULL DEFAULT 0,
  min_quantity REAL NOT NULL DEFAULT 0,
  stock_base_quantity REAL NOT NULL DEFAULT 0,
  min_base_quantity REAL NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE ingredients ADD COLUMN unit_base TEXT DEFAULT 'UNIT';
ALTER TABLE ingredients ADD COLUMN unit_factor REAL NOT NULL DEFAULT 1;
ALTER TABLE ingredients ADD COLUMN stock_base_quantity REAL NOT NULL DEFAULT 0;
ALTER TABLE ingredients ADD COLUMN min_base_quantity REAL NOT NULL DEFAULT 0;

UPDATE ingredients
SET unit_factor = CASE UPPER(COALESCE(unit, 'UNIT'))
    WHEN 'KG' THEN 1000
    WHEN 'L' THEN 1000
    ELSE 1
END
WHERE unit_factor IS NULL OR unit_factor <= 0;

UPDATE ingredients
SET unit_base = CASE UPPER(COALESCE(unit, 'UNIT'))
    WHEN 'KG' THEN 'G'
    WHEN 'L' THEN 'ML'
    ELSE UPPER(COALESCE(unit, 'UNIT'))
END
WHERE unit_base IS NULL OR TRIM(unit_base) = '';

UPDATE ingredients
SET stock_base_quantity = stock_quantity * COALESCE(unit_factor, 1)
WHERE stock_base_quantity IS NULL OR stock_base_quantity = 0;

UPDATE ingredients
SET min_base_quantity = min_quantity * COALESCE(unit_factor, 1)
WHERE min_base_quantity IS NULL OR min_base_quantity = 0;

CREATE TABLE IF NOT EXISTS product_ingredients (
  product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  ingredient_id INTEGER NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  quantity REAL NOT NULL,
  PRIMARY KEY (product_id, ingredient_id)
);

CREATE TABLE IF NOT EXISTS ingredient_movements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ingredient_id INTEGER NOT NULL REFERENCES ingredients(id),
  quantity REAL NOT NULL,
  reason TEXT NOT NULL,
  unit_cost REAL NOT NULL DEFAULT 0,
  total_cost REAL NOT NULL DEFAULT 0,
  work_period_id INTEGER REFERENCES work_periods(id),
  order_id INTEGER REFERENCES orders(id),
  user_id INTEGER REFERENCES users(id),
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_ingredient_movements_created_at
  ON ingredient_movements(created_at);

CREATE INDEX IF NOT EXISTS idx_ingredient_movements_order_id
  ON ingredient_movements(order_id);

CREATE TABLE IF NOT EXISTS cash_movements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  movement_type TEXT NOT NULL,
  category TEXT NOT NULL,
  amount REAL NOT NULL,
  description TEXT,
  work_period_id INTEGER REFERENCES work_periods(id),
  ingredient_id INTEGER REFERENCES ingredients(id),
  user_id INTEGER REFERENCES users(id),
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS cash_withdrawals (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reason TEXT NOT NULL,
  amount REAL NOT NULL,
  user_id INTEGER REFERENCES users(id),
  work_period_id INTEGER REFERENCES work_periods(id),
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS expenses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  type TEXT NOT NULL,
  description TEXT,
  amount REAL NOT NULL,
  work_period_id INTEGER REFERENCES work_periods(id),
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_cash_movements_created_at
  ON cash_movements(created_at);

CREATE INDEX IF NOT EXISTS idx_cash_movements_category
  ON cash_movements(category);

CREATE TABLE IF NOT EXISTS eod_reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  work_period_id INTEGER REFERENCES work_periods(id),
  total_sales REAL NOT NULL,
  order_count INTEGER NOT NULL,
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS print_queue (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id INTEGER REFERENCES orders(id),
  ticket_type TEXT DEFAULT 'RECEIPT',
  payload TEXT NOT NULL,
  status TEXT DEFAULT 'PENDING',
  attempts INTEGER DEFAULT 0,
  created_at TEXT DEFAULT (datetime('now')),
  printed_at TEXT
);

CREATE TABLE IF NOT EXISTS refunds (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  original_order_id INTEGER REFERENCES orders(id),
  reason TEXT,
  refund_method TEXT NOT NULL,
  total REAL NOT NULL,
  user_id INTEGER REFERENCES users(id),
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS refund_lines (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  refund_id INTEGER REFERENCES refunds(id),
  order_line_id INTEGER REFERENCES order_lines(id),
  quantity INTEGER NOT NULL,
  line_total REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS waiting_orders (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  customer_id INTEGER REFERENCES customers(id),
  customer_name TEXT,
  customer_card_uid TEXT,
  discount_percent REAL DEFAULT 0,
  discount_amount REAL DEFAULT 0,
  tva_percent REAL DEFAULT 0,
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS waiting_order_lines (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  waiting_order_id INTEGER NOT NULL REFERENCES waiting_orders(id) ON DELETE CASCADE,
  product_id INTEGER NOT NULL REFERENCES products(id),
  quantity INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS waiting_order_line_tags (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  waiting_line_id INTEGER NOT NULL REFERENCES waiting_order_lines(id) ON DELETE CASCADE,
  tag_id INTEGER NOT NULL REFERENCES tags(id)
);

CREATE INDEX IF NOT EXISTS idx_waiting_order_lines_waiting_order_id
  ON waiting_order_lines(waiting_order_id);

CREATE INDEX IF NOT EXISTS idx_waiting_order_line_tags_waiting_line_id
  ON waiting_order_line_tags(waiting_line_id);

ALTER TABLE orders ADD COLUMN cash_amount REAL DEFAULT 0;
ALTER TABLE orders ADD COLUMN prepaid_amount REAL DEFAULT 0;
ALTER TABLE orders ADD COLUMN discount_percent REAL DEFAULT 0;
ALTER TABLE orders ADD COLUMN discount_amount REAL DEFAULT 0;
ALTER TABLE products ADD COLUMN is_prepared INTEGER DEFAULT 0;
ALTER TABLE categories ADD COLUMN color TEXT;

CREATE TABLE IF NOT EXISTS price_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id INTEGER REFERENCES products(id),
  old_price REAL NOT NULL,
  new_price REAL NOT NULL,
  changed_by INTEGER REFERENCES users(id),
  changed_at TEXT DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO users (name, pin, role)
VALUES ('manager', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'MANAGER');

UPDATE users
SET pin = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92'
WHERE name = 'manager' AND role = 'MANAGER';

INSERT OR IGNORE INTO users (name, pin, role)
VALUES ('barista', '0ffe1abd1a08215353c233d6e009613e95eec4253832a761af28ff37ac5a150c', 'BARISTA');
