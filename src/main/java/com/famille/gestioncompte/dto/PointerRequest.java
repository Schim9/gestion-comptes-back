package com.famille.gestioncompte.dto;

public record PointerRequest(
        String date,
        String libelle,
        String montant,
        boolean deduireDesCourses
) {
    public Double montantAsDouble() {
        if (montant == null || montant.isBlank()) return null;
        try {
            return Double.parseDouble(montant.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Montant invalide : " + montant);
        }
    }
}
