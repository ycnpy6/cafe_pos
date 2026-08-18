package com.cafepos.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Arrondi monetaire a 2 decimales. Les totaux/soldes sont stockes en
 * `double`; sans ce garde-fou aux points de calcul (total commande, solde
 * client, remboursement), les erreurs binaires (0.1 + 0.2 != 0.3) s'accumulent
 * et finissent par decaler un solde ou un rapport de quelques centimes.
 * N'elimine pas le risque de fond (une vraie migration vers des centimes
 * entiers le ferait), mais borne la derive a chaque frontiere arithmetique.
 */
public final class Money {
    private Money() {
    }

    public static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
