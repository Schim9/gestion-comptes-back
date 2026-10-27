package com.famille.gestioncompte.service;

import com.famille.gestioncompte.config.AppProperties;
import com.famille.gestioncompte.config.GoogleSheetsProperties;
import com.famille.gestioncompte.dto.MonthData;
import com.google.api.services.sheets.v4.Sheets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SheetReaderTest {

    @Mock
    private Sheets sheets;

    @Mock
    private GoogleSheetsProperties properties;

    private SheetReader reader;

    @BeforeEach
    void setUp() {
        reader = new SheetReader(sheets, properties, new AppProperties());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static List<List<Object>> grid(int rowCount) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            rows.add(new ArrayList<>(Arrays.asList("", "", "", "")));
        }
        return rows;
    }

    private static void setCell(List<List<Object>> values, int rowIdx, int colIdx, String value) {
        values.get(rowIdx).set(colIdx, value);
    }

    // =========================================================================
    // normalize
    // =========================================================================

    @Nested
    class NormalizeTest {

        @Test
        void lowercase() {
            assertThat(reader.normalize("RÉEL")).isEqualTo("reel");
        }

        @Test
        void accents_e() {
            assertThat(reader.normalize("éèê")).isEqualTo("eee");
        }

        @Test
        void accents_a_i_o_u() {
            assertThat(reader.normalize("àâîôû")).isEqualTo("aaiou");
        }

        @Test
        void trim() {
            assertThat(reader.normalize("  Réel  ")).isEqualTo("reel");
        }

        @Test
        void labels_reels() {
            assertThat(reader.normalize("Réel")).isEqualTo("reel");
            assertThat(reader.normalize("Mouvement à venir")).isEqualTo("mouvement a venir");
            assertThat(reader.normalize("Mouvements récurrents")).isEqualTo("mouvements recurrents");
            assertThat(reader.normalize("Prévisionnel fin de mois")).isEqualTo("previsionnel fin de mois");
        }
    }

    // =========================================================================
    // monthSortKey
    // =========================================================================

    @Nested
    class MonthSortKeyTest {

        @Test
        void janvier_2026() {
            assertThat(reader.monthSortKey("Janvier2026")).isEqualTo(202601);
        }

        @Test
        void decembre_2025() {
            assertThat(reader.monthSortKey("Decembre2025")).isEqualTo(202512);
        }

        @Test
        void octobre_2026() {
            assertThat(reader.monthSortKey("Octobre2026")).isEqualTo(202610);
        }

        @Test
        void ordre_dans_annee() {
            assertThat(reader.monthSortKey("Octobre2026"))
                    .isGreaterThan(reader.monthSortKey("Janvier2026"));
        }

        @Test
        void ordre_inter_annee() {
            assertThat(reader.monthSortKey("Janvier2027"))
                    .isGreaterThan(reader.monthSortKey("Decembre2026"));
        }

        @Test
        void cle_inconnue() {
            assertThat(reader.monthSortKey("TEMPLATE")).isEqualTo(Integer.MAX_VALUE);
            assertThat(reader.monthSortKey("")).isEqualTo(Integer.MAX_VALUE);
        }
    }

    // =========================================================================
    // parseDouble
    // =========================================================================

    @Nested
    class ParseDoubleTest {

        @Test
        void null_retourne_null() {
            assertThat(reader.parseDouble(null)).isNull();
        }

        @Test
        void vide_retourne_null() {
            assertThat(reader.parseDouble("")).isNull();
            assertThat(reader.parseDouble("  ")).isNull();
        }

        @Test
        void point_decimal() {
            assertThat(reader.parseDouble("150.5")).isEqualTo(150.5);
        }

        @Test
        void virgule_decimal() {
            assertThat(reader.parseDouble("150,5")).isEqualTo(150.5);
        }

        @Test
        void negatif() {
            assertThat(reader.parseDouble("-50.0")).isEqualTo(-50.0);
        }

        @Test
        void invalide_retourne_null() {
            assertThat(reader.parseDouble("abc")).isNull();
        }
    }

    // =========================================================================
    // findRowByLabel
    // =========================================================================

    @Nested
    class FindRowByLabelTest {

        @Test
        void label_present() {
            List<List<Object>> values = grid(6);
            setCell(values, 4, 1, "Réel");
            assertThat(reader.findRowByLabel(values, "Réel")).isEqualTo(4);
        }

        @Test
        void label_avec_accent_normalise() {
            List<List<Object>> values = grid(6);
            setCell(values, 3, 1, "Mouvement à venir");
            assertThat(reader.findRowByLabel(values, "Mouvement à venir")).isEqualTo(3);
        }

        @Test
        void label_absent() {
            List<List<Object>> values = grid(6);
            assertThat(reader.findRowByLabel(values, "Réel")).isEqualTo(-1);
        }
    }

    // =========================================================================
    // findLastFilledReelRow
    // =========================================================================

    @Nested
    class FindLastFilledReelRowTest {

        @Test
        void zone_vide_retourne_premiere_ligne_reel() {
            List<List<Object>> values = grid(8);
            assertThat(reader.findLastFilledReelRow(values, 5)).isEqualTo(4);
        }

        @Test
        void une_operation() {
            List<List<Object>> values = grid(8);
            setCell(values, 3, 1, "EDF");
            setCell(values, 3, 2, "80.0");
            assertThat(reader.findLastFilledReelRow(values, 5)).isEqualTo(5);
        }

        @Test
        void deux_operations() {
            List<List<Object>> values = grid(10);
            setCell(values, 3, 1, "EDF");
            setCell(values, 3, 2, "80.0");
            setCell(values, 4, 1, "Salaire");
            setCell(values, 4, 3, "2500.0");
            assertThat(reader.findLastFilledReelRow(values, 7)).isEqualTo(6);
        }
    }

    // =========================================================================
    // findSortedInsertRowForDate
    // =========================================================================

    @Nested
    class FindSortedInsertRowForDateTest {

        private List<List<Object>> valuesAvec3Ops() {
            List<List<Object>> values = grid(10);
            setCell(values, 3, 0, "5");
            setCell(values, 3, 1, "EDF");
            setCell(values, 4, 0, "10");
            setCell(values, 4, 1, "Loyer");
            setCell(values, 5, 0, "20");
            setCell(values, 5, 1, "Salaire");
            return values;
        }

        @Test
        void date_null_retourne_append() {
            assertThat(reader.findSortedInsertRowForDate(valuesAvec3Ops(), 8, null)).isEqualTo(-1);
        }

        @Test
        void date_vide_retourne_append() {
            assertThat(reader.findSortedInsertRowForDate(valuesAvec3Ops(), 8, "")).isEqualTo(-1);
        }

        @Test
        void date_invalide_retourne_append() {
            assertThat(reader.findSortedInsertRowForDate(valuesAvec3Ops(), 8, "abc")).isEqualTo(-1);
        }

        @Test
        void date_avant_premiere_op_insere_en_tete() {
            assertThat(reader.findSortedInsertRowForDate(valuesAvec3Ops(), 8, "3")).isEqualTo(4);
        }

        @Test
        void date_entre_deux_ops() {
            assertThat(reader.findSortedInsertRowForDate(valuesAvec3Ops(), 8, "7")).isEqualTo(5);
        }

        @Test
        void date_apres_derniere_op_retourne_append() {
            assertThat(reader.findSortedInsertRowForDate(valuesAvec3Ops(), 8, "25")).isEqualTo(-1);
        }

        @Test
        void date_egale_a_existante_insere_apres() {
            assertThat(reader.findSortedInsertRowForDate(valuesAvec3Ops(), 8, "10")).isEqualTo(6);
        }

        @Test
        void zone_vide_retourne_append() {
            List<List<Object>> values = grid(6);
            assertThat(reader.findSortedInsertRowForDate(values, 3, "15")).isEqualTo(-1);
        }
    }

    // =========================================================================
    // parseOperation
    // =========================================================================

    @Nested
    class ParseOperationTest {

        @Test
        void libelle_vide_retourne_null() {
            List<List<Object>> values = grid(5);
            setCell(values, 2, 2, "100.0");
            assertThat(reader.parseOperation(values, 2, true, null)).isNull();
        }

        @Test
        void libelle_label_reel_retourne_null() {
            List<List<Object>> values = grid(5);
            setCell(values, 2, 1, "Réel");
            assertThat(reader.parseOperation(values, 2, false, null)).isNull();
        }

        @Test
        void libelle_mouvement_a_venir_retourne_null() {
            List<List<Object>> values = grid(5);
            setCell(values, 2, 1, "Mouvement à venir");
            assertThat(reader.parseOperation(values, 2, false, "a_venir")).isNull();
        }

        @Test
        void libelle_mouvements_recurrents_retourne_null() {
            List<List<Object>> values = grid(5);
            setCell(values, 2, 1, "Mouvements récurrents");
            assertThat(reader.parseOperation(values, 2, false, "recurrent")).isNull();
        }

        @Test
        void libelle_previsionnel_retourne_null() {
            List<List<Object>> values = grid(5);
            setCell(values, 2, 1, "Prévisionnel fin de mois");
            assertThat(reader.parseOperation(values, 2, false, null)).isNull();
        }

        @Test
        void operation_ccm_avec_date() {
            List<List<Object>> values = grid(5);
            setCell(values, 2, 0, "5");
            setCell(values, 2, 1, "EDF");
            setCell(values, 2, 2, "80.0");

            MonthData.Operation op = reader.parseOperation(values, 2, true, null);

            assertThat(op).isNotNull();
            assertThat(op.ligne()).isEqualTo(3);
            assertThat(op.date()).isEqualTo("5");
            assertThat(op.libelle()).isEqualTo("EDF");
            assertThat(op.compte()).isEqualTo("CCM");
            assertThat(op.montant()).isEqualTo(80.0);
            assertThat(op.type()).isNull();
        }

        @Test
        void operation_boursorama_sans_date() {
            List<List<Object>> values = grid(5);
            setCell(values, 3, 0, "10");
            setCell(values, 3, 1, "Salaire");
            setCell(values, 3, 3, "2500.0");

            MonthData.Operation op = reader.parseOperation(values, 3, false, "a_venir");

            assertThat(op).isNotNull();
            assertThat(op.ligne()).isEqualTo(4);
            assertThat(op.date()).isNull();
            assertThat(op.libelle()).isEqualTo("Salaire");
            assertThat(op.compte()).isEqualTo("Boursorama");
            assertThat(op.montant()).isEqualTo(2500.0);
            assertThat(op.type()).isEqualTo("a_venir");
        }

        @Test
        void sans_montant_retourne_null() {
            List<List<Object>> values = grid(5);
            setCell(values, 2, 1, "Note sans montant");
            assertThat(reader.parseOperation(values, 2, true, null)).isNull();
        }
    }
}
