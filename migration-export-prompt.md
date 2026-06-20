# Legacy POS → Common Grounds POS — CSV Export Prompt

Paste this prompt to an AI or your DB admin tool to generate the export queries.

---

I have a legacy POS database (MySQL/MariaDB). Write SQL SELECT queries — one per table — that export the data below as CSV-ready result sets. Use English column aliases matching the target schema. The legacy DB uses French column names.

For each query, if a column name does not exist in your DB, replace it with `NULL AS column_name` and tell me what the real column name is.

---

## 1. `categorie` → `categories`

```sql
SELECT
  id_categorie  AS id,
  designation   AS name,
  NULL          AS color,
  NULL          AS icon_code,
  ordre         AS sort_order
FROM categorie;
```

---

## 2. `article` → `products`

```sql
SELECT
  id_article    AS id,
  designation   AS name,
  prix_vente    AS price,
  prix_achat    AS cost,
  id_categorie  AS category_id,
  stock_actuel  AS stock,
  actif         AS active,
  code_barre    AS barcode,
  reference     AS reference,
  id_famille    AS family_id
FROM article;
```

---

## 3. `clients` → `customers`

```sql
SELECT
  id_client     AS id,
  nom           AS name,
  NULL          AS card_uid,
  solde         AS balance,
  actif         AS active,
  telephone     AS phone,
  email         AS email,
  adresse       AS address,
  date_creation AS created_at
FROM clients;
```

---

## 4. `carte_client` → `customers.card_uid` (update pass)

```sql
SELECT
  id_client     AS customer_id,
  num_carte     AS card_uid
FROM carte_client
WHERE etat_carte = 1;
```

---

## 5. `composant` → `ingredients`

```sql
SELECT
  id_composant  AS id,
  designation   AS name,
  unite         AS unit,
  stock_actuel  AS stock_quantity,
  stock_minimum AS min_quantity,
  prix_revient  AS package_price,
  actif         AS active
FROM composant;
```

---

## 6. `composition_article` → `product_ingredients`

```sql
SELECT
  id_article    AS product_id,
  id_composant  AS ingredient_id,
  quantite      AS quantity,
  unite         AS unit
FROM composition_article;
```

---

## 7. `transactiona` → `account_transactions` (top-up history)

```sql
SELECT
  id_transaction   AS id,
  id_client        AS customer_id,
  montant          AS amount,
  libelle          AS description,
  id_user          AS user_id,
  date_transaction AS created_at,
  solde_apres      AS balance_after
FROM transactiona
WHERE id_type_transaction IN (/* replace with top-up type IDs from type_transaction table */);
```

---

## SQLite ALTER TABLE — run these on the new DB before importing

```sql
ALTER TABLE products   ADD COLUMN barcode    TEXT;
ALTER TABLE products   ADD COLUMN reference  TEXT;
ALTER TABLE customers  ADD COLUMN phone      TEXT;
ALTER TABLE customers  ADD COLUMN email      TEXT;
ALTER TABLE customers  ADD COLUMN address    TEXT;
```

---

## Export rules

- One CSV file per query, named after the target table (e.g. `categories.csv`)
- Encoding: **UTF-8**
- Separator: **comma**
- First row: column header aliases (as written above)
- NULL values: empty string (not the word "NULL")
