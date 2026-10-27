package com.famille.gestioncompte.dto;

import java.util.List;

public record MonthData(
        boolean exists,
        String monthKey,
        Double soldeInitialCCM,
        Double soldeInitialBoursorama,
        Double soldeReelCCM,
        Double soldeReelBoursorama,
        Double soldePrevCCM,
        Double soldePrevBoursorama,
        List<Operation> operationsReelles,
        List<Operation> operationsPrevisionnelles
) {
    /**
     * type : null = réelle, "a_venir" = mouvement à venir, "recurrent" = mouvement récurrent
     */
    public record Operation(
            int ligne,
            String date,
            String libelle,
            String compte,
            Double montant,
            String type
    ) {}
}
