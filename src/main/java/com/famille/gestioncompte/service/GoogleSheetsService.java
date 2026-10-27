package com.famille.gestioncompte.service;

import com.famille.gestioncompte.config.AppProperties;
import com.famille.gestioncompte.dto.MonthData;
import com.famille.gestioncompte.dto.OperationRequest;
import com.famille.gestioncompte.dto.UpdateOperationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class GoogleSheetsService {

    private final SheetReader reader;
    private final SheetWriter writer;
    private final AppProperties appProperties;

    public GoogleSheetsService(SheetReader reader, SheetWriter writer, AppProperties appProperties) {
        this.reader = reader;
        this.writer = writer;
        this.appProperties = appProperties;
    }

    // -------------------------------------------------------------------------
    // Délégation lecture (point d'entrée unique pour les controllers)
    // -------------------------------------------------------------------------

    public List<String> getMonthKeys() throws IOException {
        return reader.getMonthKeys();
    }

    public MonthData getMonthData(String monthKey) throws IOException {
        return reader.getMonthData(monthKey);
    }

    // -------------------------------------------------------------------------
    // Ajout d'une opération
    // -------------------------------------------------------------------------

    public boolean addOperation(String monthKey, OperationRequest request) throws IOException {
        validateCompte(request.compte());
        boolean created = false;
        Integer sheetId = reader.getSheetId(monthKey);
        if (sheetId == null) {
            createMonth(monthKey);
            created = true;
            sheetId = reader.resolveSheetId(monthKey);
        }
        List<List<Object>> values = reader.readAllRows(monthKey);
        boolean dejaDebite = Boolean.TRUE.equals(request.dejaDebite());

        if (dejaDebite) {
            int reelIdx = reader.findReelRow(values);
            if (reelIdx < 0) throw new IllegalStateException("Ligne 'Réel' introuvable dans l'onglet " + monthKey);
            insertIntoReelZone(sheetId, monthKey, values, reelIdx,
                    request.date(), request.libelle(), request.compte(), request.montant());
        } else {
            int mouvIdx = reader.findMouvAVenirRow(values);
            int recIdx  = reader.findRecurrentsRow(values);
            int prevIdx = reader.findPrevisionnelRow(values);
            int ligne;
            if (mouvIdx >= 0) {
                ligne = reader.findNextFreeAVenirRow(values, mouvIdx, recIdx);
                if (recIdx >= 0) {
                    int rowsToInsert = Math.max(0, ligne - recIdx + 1);
                    if (rowsToInsert > 0) writer.insertRowsBefore(sheetId, recIdx + 1, rowsToInsert);
                }
            } else {
                ligne = reader.findNextFreePrevRow(values, prevIdx);
                if (ligne == -1) throw new IllegalStateException("Zone prévisionnelle pleine");
            }
            writer.writeOperation(monthKey, ligne, null, request.libelle(), request.compte(), request.montant());
        }
        return created;
    }

    // -------------------------------------------------------------------------
    // Édition / suppression
    // -------------------------------------------------------------------------

    public void updateOperation(String monthKey, int ligne, UpdateOperationRequest req) throws IOException {
        validateCompte(req.compte());
        writer.writeOperation(monthKey, ligne, req.date(), req.libelle(), req.compte(), req.montant());
    }

    public void deleteOperation(String monthKey, int ligne) throws IOException {
        writer.deleteRow(reader.resolveSheetId(monthKey), ligne);
    }

    // -------------------------------------------------------------------------
    // Dépointage
    // -------------------------------------------------------------------------

    public void depointeOperation(String monthKey, int ligne) throws IOException {
        List<Object> row = reader.readRow(monthKey, ligne);
        String libelle       = row.size() > 1 ? row.get(1).toString() : "";
        Double montantC1 = row.size() > 2 ? reader.parseDouble(row.get(2).toString()) : null;
        Double montantC2 = row.size() > 3 ? reader.parseDouble(row.get(3).toString()) : null;
        String compte  = montantC1 != null ? appProperties.getComptes().getCompte1() : appProperties.getComptes().getCompte2();
        Double montant = montantC1 != null ? montantC1 : montantC2;

        // add d'abord (zone à venir, n'affecte pas la zone réelle), delete ensuite
        // → évite la perte définitive si l'add échoue
        addOperation(monthKey, new OperationRequest(libelle, compte, montant, false, null));
        writer.deleteRow(reader.resolveSheetId(monthKey), ligne);
    }

    // -------------------------------------------------------------------------
    // Pointage
    // -------------------------------------------------------------------------

    public void pointerOperation(String monthKey, int ligne, String date, String libelle,
                                 Double montantOverride, boolean deduireDesCourses) throws IOException {
        log.info("[POINTAGE] Début — mois={}, ligne={}, date={}, libelle={}, montantOverride={}, deduireDesCourses={}",
                monthKey, ligne, date, libelle, montantOverride, deduireDesCourses);

        int sheetId = reader.resolveSheetId(monthKey);
        List<List<Object>> values = reader.readAllRows(monthKey);

        List<Object> row = ligne <= values.size() ? values.get(ligne - 1) : List.of();
        if (row.isEmpty()) throw new IllegalArgumentException("Ligne introuvable : " + ligne);

        String libelleEffectif = (libelle != null && !libelle.isBlank())
                ? libelle
                : (row.size() > 1 ? row.get(1).toString() : "");
        Double montantC1 = row.size() > 2 ? reader.parseDouble(row.get(2).toString()) : null;
        Double montantC2 = row.size() > 3 ? reader.parseDouble(row.get(3).toString()) : null;
        String compte = montantC1 != null ? appProperties.getComptes().getCompte1() : appProperties.getComptes().getCompte2();
        Double montant = montantOverride != null ? montantOverride
                       : (montantC1 != null ? montantC1 : montantC2);

        log.info("[POINTAGE] Ligne source lue — libelleEffectif={}, compte={}, montantC1={}, montantC2={}, montantUtilise={}",
                libelleEffectif, compte, montantC1, montantC2, montant);

        int recIdx = reader.findRecurrentsRow(values);
        boolean isRecurrent = recIdx >= 0 && ligne > recIdx + 1;

        log.info("[POINTAGE] Zone détectée — recIdx={} (ligne sheet={}), isRecurrent={}",
                recIdx, recIdx >= 0 ? recIdx + 1 : -1, isRecurrent);

        int reelIdx = reader.findReelRow(values);
        if (reelIdx < 0) throw new IllegalStateException("Ligne 'Réel' introuvable dans l'onglet " + monthKey);

        ReelInsertResult result = insertIntoReelZone(sheetId, monthKey, values, reelIdx, date, libelleEffectif, compte, montant);
        int actualLigne = computeActualLigne(ligne, result.insertRow(), result.rowsToInsert());

        log.info("[POINTAGE] Décalage source — insertRow={}, rowsToInsert={}, actualLigne={}",
                result.insertRow(), result.rowsToInsert(), actualLigne);

        if (isRecurrent) {
            log.info("[POINTAGE] Récurrent → clearAmountCell sur ligne {}", actualLigne);
            writer.clearAmountCell(monthKey, actualLigne, compte);
        } else {
            log.info("[POINTAGE] À venir → deleteRow sur ligne {}", actualLigne);
            writer.deleteRow(sheetId, actualLigne);
            log.info("[POINTAGE] deleteRow terminé pour ligne {}", actualLigne);
        }

        if (deduireDesCourses && montant != null) {
            deduireDuBudgetCourses(monthKey, montant);
        }

        log.info("[POINTAGE] Fin — mois={}, opération terminée avec succès", monthKey);
    }

    // -------------------------------------------------------------------------
    // Création d'un mois
    // -------------------------------------------------------------------------

    public void createMonth(String monthKey) throws IOException {
        String prevMonthKey = reader.findPreviousMonth(monthKey);

        if (prevMonthKey == null) {
            createFromTemplate(monthKey);
            return;
        }

        List<List<Object>> sourceValues = reader.readAllRows(prevMonthKey);
        int reelIdx = reader.findReelRow(sourceValues);
        int mouvIdx = reader.findMouvAVenirRow(sourceValues);
        int recIdx  = reader.findRecurrentsRow(sourceValues);
        int prevIdx = reader.findPrevisionnelRow(sourceValues);

        writer.duplicateSheet(reader.resolveSheetId(prevMonthKey), monthKey);

        if (prevIdx >= 0) {
            String ccm    = reader.getCellValue(sourceValues, prevIdx, 2);
            String bourso = reader.getCellValue(sourceValues, prevIdx, 3);
            writer.writeSingleValue(monthKey, "C2",   ccm   != null ? ccm   : "0");
            writer.writeSingleValue(monthKey, "D2", bourso != null ? bourso : "0");
        }

        if (reelIdx >= 0) {
            int reelRow = reelIdx + 1;
            if (reelRow > SheetReader.REEL_OPS_FIRST_ROW) {
                writer.clearRange(monthKey, "A" + SheetReader.REEL_OPS_FIRST_ROW + ":D" + (reelRow - 1));
            }
        }

        if (mouvIdx >= 0 && recIdx >= 0) {
            int mouvOpsStart = mouvIdx + 3;
            int mouvOpsEnd   = recIdx;
            if (mouvOpsStart <= mouvOpsEnd) {
                writer.clearRange(monthKey, "A" + mouvOpsStart + ":D" + mouvOpsEnd);
            }
        }
    }

    /**
     * Calcule la ligne réelle de la source après d'éventuelles insertions.
     * - insertion triée (insertRow > 0) : 1 ligne insérée avant la source → +1
     * - insertion en fin (rowsToInsert > 0) : N lignes insérées avant la zone réelle → +N
     * - aucune insertion : ligne inchangée
     */
    static int computeActualLigne(int ligne, int insertRow, int rowsToInsert) {
        if (insertRow > 0) return ligne + 1;
        return ligne + rowsToInsert;
    }

    private void createFromTemplate(String monthKey) throws IOException {
        Integer templateId = reader.getSheetId(SheetReader.TEMPLATE_SHEET);
        if (templateId == null) throw new IllegalStateException("Onglet TEMPLATE introuvable");
        writer.duplicateSheet(templateId, monthKey);
    }

    // -------------------------------------------------------------------------
    // Insertion en zone réelle — logique partagée addOperation / pointerOperation
    // -------------------------------------------------------------------------

    private record ReelInsertResult(int insertRow, int rowsToInsert) {}

    private ReelInsertResult insertIntoReelZone(int sheetId, String monthKey, List<List<Object>> values,
            int reelIdx, String date, String libelle, String compte, Double montant) throws IOException {
        int reelRow = reelIdx + 1;
        int insertRow = reader.findSortedInsertRowForDate(values, reelIdx, date);
        int rowsToInsert = 0;
        if (insertRow > 0) {
            writer.insertRowsBefore(sheetId, insertRow, 1);
            if (insertRow == SheetReader.REEL_OPS_FIRST_ROW) {
                int ligneReelle = reader.findLastFilledReelRow(values, reelIdx);
                writer.updateReelFormulas(monthKey, reelRow + 1, ligneReelle);
            }
            writer.writeOperation(monthKey, insertRow, date, libelle, compte, montant);
        } else {
            int ligneReelle = reader.findLastFilledReelRow(values, reelIdx);
            rowsToInsert = Math.max(0, ligneReelle + 2 - reelRow);
            if (rowsToInsert > 0) {
                writer.insertRowsBefore(sheetId, reelRow, rowsToInsert);
                writer.updateReelFormulas(monthKey, reelRow + rowsToInsert, ligneReelle);
            }
            writer.writeOperation(monthKey, ligneReelle, date, libelle, compte, montant);
        }
        return new ReelInsertResult(insertRow, rowsToInsert);
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void validateCompte(String compte) {
        String c1 = appProperties.getComptes().getCompte1();
        String c2 = appProperties.getComptes().getCompte2();
        if (!c1.equals(compte) && !c2.equals(compte)) {
            throw new IllegalArgumentException(
                    "Compte inconnu : '" + compte + "'. Valeurs acceptées : " + c1 + ", " + c2);
        }
    }

    // -------------------------------------------------------------------------
    // Budget courses
    // -------------------------------------------------------------------------

    private void deduireDuBudgetCourses(String monthKey, Double montant) throws IOException {
        log.info("[COURSES] Déduction demandée — montant={}", montant);
        List<List<Object>> values = reader.readAllRows(monthKey);
        int recIdx = reader.findRecurrentsRow(values);
        if (recIdx < 0) {
            log.warn("[COURSES] Label 'Mouvements récurrents' introuvable, abandon");
            return;
        }

        int courseLigne = -1;
        for (int i = recIdx + 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.size() > 1 && appProperties.getLibelleBudgetCourses().equals(row.get(1).toString().trim())) {
                courseLigne = i + 1;
                break;
            }
        }
        if (courseLigne < 0) {
            log.warn("[COURSES] Ligne '{}' introuvable dans les récurrents, abandon", appProperties.getLibelleBudgetCourses());
            return;
        }

        List<Object> row = values.get(courseLigne - 1);
        Double valC = row.size() > 2 ? reader.parseDouble(row.get(2).toString()) : null;
        Double valD = row.size() > 3 ? reader.parseDouble(row.get(3).toString()) : null;

        String colonne;
        Double valeurActuelle;
        if (valC != null) {
            colonne = "C";
            valeurActuelle = valC;
        } else if (valD != null) {
            colonne = "D";
            valeurActuelle = valD;
        } else {
            log.warn("[COURSES] Ligne '{}' trouvée en ligne {} mais montant vide, abandon", appProperties.getLibelleBudgetCourses(), courseLigne);
            return;
        }

        double nouveauMontant = valeurActuelle - montant;
        log.info("[COURSES] Mise à jour ligne {} col {} : {} - {} = {}", courseLigne, colonne, valeurActuelle, montant, nouveauMontant);
        writer.writeSingleValue(monthKey, colonne + courseLigne, nouveauMontant);
        log.info("[COURSES] Mise à jour terminée");
    }
}
