package com.famille.gestioncompte.exception;

public class MonthNotFoundException extends RuntimeException {
    public MonthNotFoundException(String monthKey) {
        super("Mois introuvable : " + monthKey);
    }
}
