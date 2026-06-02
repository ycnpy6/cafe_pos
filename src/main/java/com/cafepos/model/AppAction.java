package com.cafepos.model;

public enum AppAction {
    OPEN_POS("open.pos", "Ouvrir la caisse", UserRole.BARISTA, false),
    OPEN_DASHBOARD("open.dashboard", "Acces dashboard", UserRole.BARISTA, false),
    OPEN_STOCK("open.stock", "Acces stock", UserRole.MANAGER, true),
    OPEN_CLIENTS("open.clients", "Acces clients", UserRole.BARISTA, false),
    OPEN_REPORTS("open.reports", "Acces rapports", UserRole.MANAGER, true),
    OPEN_SETTINGS("open.settings", "Acces parametres", UserRole.MANAGER, true),
    BACK_TO_POS("back.pos", "Retour vers POS", UserRole.BARISTA, false),
    EDIT_PRODUCT_PRICE("edit.product.price", "Modifier prix produit", UserRole.MANAGER, true),
    EDIT_PRODUCT_COST("edit.product.cost", "Modifier cout produit", UserRole.MANAGER, true),
    EDIT_RECIPE("edit.recipe", "Modifier recette", UserRole.MANAGER, true),
    EDIT_INGREDIENTS("edit.ingredients", "Modifier ingredients", UserRole.MANAGER, true),
    ADJUST_STOCK("adjust.stock", "Ajuster stock", UserRole.MANAGER, true),
    PURCHASE_INGREDIENT("purchase.ingredient", "Achat ingredient", UserRole.MANAGER, true),
    WITHDRAW_CASH("withdraw.cash", "Sortie caisse", UserRole.MANAGER, true),
    EDIT_SUPPLEMENT_PRICE("edit.supplement.price", "Modifier prix supplement", UserRole.MANAGER, true),
    EDIT_CATEGORY("edit.category", "Modifier categories", UserRole.MANAGER, true),
    MANAGE_PRODUCTS("manage.products", "Gerer produits", UserRole.MANAGER, true);

    private final String key;
    private final String label;
    private final UserRole defaultRole;
    private final boolean defaultPinRequired;

    AppAction(String key, String label, UserRole defaultRole, boolean defaultPinRequired) {
        this.key = key;
        this.label = label;
        this.defaultRole = defaultRole;
        this.defaultPinRequired = defaultPinRequired;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public UserRole getDefaultRole() {
        return defaultRole;
    }

    public boolean isDefaultPinRequired() {
        return defaultPinRequired;
    }
}
