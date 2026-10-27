package com.famille.gestioncompte.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleSheetsServiceTest {

    // =========================================================================
    // computeActualLigne
    // =========================================================================

    @Nested
    class ComputeActualLigneTest {

        @Test
        void insertionTriee_decaleDeUn() {
            // 1 ligne insérée à insertRow=5, la source (ligne=10) se retrouve en 11
            assertThat(GoogleSheetsService.computeActualLigne(10, 5, 0)).isEqualTo(11);
        }

        @Test
        void insertionTriee_rowsToInsertIgnore() {
            // insertRow > 0 prime sur rowsToInsert — toujours +1
            assertThat(GoogleSheetsService.computeActualLigne(10, 5, 3)).isEqualTo(11);
        }

        @Test
        void insertionEnFin_avecRowsToInsert_decale() {
            // 3 lignes insérées avant la zone réelle, la source décale de 3
            assertThat(GoogleSheetsService.computeActualLigne(15, 0, 3)).isEqualTo(18);
        }

        @Test
        void insertionEnFin_avecUneLigne_decaleDeUn() {
            assertThat(GoogleSheetsService.computeActualLigne(8, 0, 1)).isEqualTo(9);
        }

        @Test
        void aucuneInsertion_ligneInchangee() {
            // insertRow=0 et rowsToInsert=0 : source reste à sa place
            assertThat(GoogleSheetsService.computeActualLigne(7, 0, 0)).isEqualTo(7);
        }
    }
}
