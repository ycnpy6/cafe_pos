package com.cafepos.model;

public enum AppAction {
    OPEN_POS("open.pos", "Ouvrir la caisse", UserRole.BARISTA, false),
    OPEN_DASHBOARD("open.dashboard", "Acces dashboard", UserRole.BARISTA, false),
    // Le barista consulte le stock et fait des ajustements simples (casse,
    // recomptage) sans manager ; prix/couts/recettes/achats restent geres
    // par les actions MANAGER individuelles ci-dessous, chacune verifiee a
    // son propre point d'entree dans StockController.
    OPEN_STOCK("open.stock", "Acces stock", UserRole.BARISTA, false),
    OPEN_CLIENTS("open.clients", "Acces clients", UserRole.BARISTA, false),
    OPEN_REPORTS("open.reports", "Acces rapports", UserRole.MANAGER, false),
    OPEN_SETTINGS("open.settings", "Acces parametres", UserRole.MANAGER, false),
    BACK_TO_POS("back.pos", "Retour vers POS", UserRole.BARISTA, false),
    // PIN non force par defaut sur les actions manager : un manager deja
    // authentifie (session normale ou etape PIN recente, fenetre de grace
    // de 5 min via AdminSessionManager) n'a pas a resaisir son code a
    // chaque action. Reactivable action par action depuis Reglages.
    EDIT_PRODUCT_PRICE("edit.product.price", "Modifier prix produit", UserRole.MANAGER, false),
    EDIT_PRODUCT_COST("edit.product.cost", "Modifier cout produit", UserRole.MANAGER, false),
    EDIT_RECIPE("edit.recipe", "Modifier recette", UserRole.MANAGER, false),
    EDIT_INGREDIENTS("edit.ingredients", "Modifier ingredients", UserRole.MANAGER, false),
    ADJUST_STOCK("adjust.stock", "Ajuster stock", UserRole.BARISTA, false),
    PURCHASE_INGREDIENT("purchase.ingredient", "Achat ingredient", UserRole.MANAGER, false),
    WITHDRAW_CASH("withdraw.cash", "Sortie caisse", UserRole.MANAGER, false),
    EDIT_SUPPLEMENT_PRICE("edit.supplement.price", "Modifier prix supplement", UserRole.MANAGER, false),
    EDIT_CATEGORY("edit.category", "Modifier categories", UserRole.MANAGER, false),
    MANAGE_PRODUCTS("manage.products", "Gerer produits", UserRole.MANAGER, false);

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
