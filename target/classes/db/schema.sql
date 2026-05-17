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
  sort_order INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS products (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  price REAL NOT NULL,
  cost REAL DEFAULT 0,
  category_id INTEGER REFERENCES categories(id),
  stock INTEGER DEFAULT 0,
  active INTEGER DEFAULT 1
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

ALTER TABLE orders ADD COLUMN cash_amount REAL DEFAULT 0;
ALTER TABLE orders ADD COLUMN prepaid_amount REAL DEFAULT 0;

CREATE TABLE IF NOT EXISTS price_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id INTEGER REFERENCES products(id),
  old_price REAL NOT NULL,
  new_price REAL NOT NULL,
  changed_by INTEGER REFERENCES users(id),
  changed_at TEXT DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO users (name, pin, role)
VALUES ('manager', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', 'MANAGER');

INSERT OR IGNORE INTO users (name, pin, role)
VALUES ('barista', '0ffe1abd1a08215353c233d6e009613e95eec4253832a761af28ff37ac5a150c', 'BARISTA');
