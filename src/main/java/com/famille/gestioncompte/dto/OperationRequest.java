package com.famille.gestioncompte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OperationRequest(
        @NotBlank String libelle,
        @NotBlank String compte,
        @NotNull Double montant,
        @NotNull Boolean dejaDebite,
        String date
) {}
