package com.famille.gestioncompte.service;

import com.famille.gestioncompte.config.AppProperties;
import com.famille.gestioncompte.exception.MonthNotFoundException;
import com.famille.gestioncompte.config.GoogleSheetsProperties;
import com.famille.gestioncompte.dto.MonthData;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class SheetReader {

    static final int REEL_OPS_FIRST_ROW = 4;
    static final String TEMPLATE_SHEET = "TEMPLATE";

    private static final Map<String, Integer> MONTH_ORDER = Map.ofEntries(
            Map.entry("Janvier", 1), Map.entry("Fevrier", 2), Map.entry("Mars", 3),
            Map.entry("Avril", 4), Map.entry("Mai", 5), Map.entry("Juin", 6),
            Map.entry("Juillet", 7), Map.entry("Aout", 8), Map.entry("Septembre", 9),
            Map.entry("Octobre", 10), Map.entry("Novembre", 11), Map.entry("Decembre", 12)
    );

    private final Sheets sheets;
    private final GoogleSheetsProperties properties;
    private final AppProperties appProperties;

    public SheetReader(Sheets sheets, GoogleSheetsProperties properties, AppProperties appProperties) {
        this.sheets = sheets;
        this.properties = properties;
        this.appProperties = appProperties;
    }

    // -------------------------------------------------------------------------
    // API — lecture
    // -------------------------------------------------------------------------

    public void checkConnectivity() throws IOException {
        sheets.spreadsheets()
                .get(properties.getSpreadsheetId())
                .setFields("spreadsheetId")
                .execute();
    }

    public List<String> getMonthKeys() throws IOException {
        Spreadsheet spreadsheet = sheets.spreadsheets()
                .get(properties.getSpreadsheetId())
                .execute();
        return spreadsheet.getSheets().stream()
                .map(s -> s.getProperties().getTitle())
                .filter(title -> !TEMPLATE_SHEET.equals(title))
                .sorted(Comparator.comparingInt(this::monthSortKey))
                .toList();
    }

    public boolean sheetExists(String monthKey) throws IOException {
        return getSheetId(monthKey) != null;
    }

    public MonthData getMonthData(String monthKey) throws IOException {
        if (getSheetId(monthKey) == null) throw new MonthNotFoundException(monthKey);
        List<List<Object>> values = readAllRows(monthKey);

        Double soldeInitialCCM       = parseDouble(getCellValue(values, 1, 2));
        Double soldeInitialBoursorama = parseDouble(getCellValue(values, 1, 3));

        int reelIdx = findReelRow(values);
        int prevIdx = findPrevisionnelRow(values);
        int mouvIdx = findMouvAVenirRow(values);
        int recIdx  = findRecurrentsRow(values);

        Double soldeReelCCM        = reelIdx >= 0 ? parseDouble(getCellValue(values, reelIdx, 2)) : null;
        Double soldeReelBoursorama = reelIdx >= 0 ? parseDouble(getCellValue(values, reelIdx, 3)) : null;
        Double soldePrevCCM        = prevIdx >= 0 ? parseDouble(getCellValue(values, prevIdx, 2)) : null;
        Double soldePrevBoursorama = prevIdx >= 0 ? parseDouble(getCellValue(values, prevIdx, 3)) : null;

        List<MonthData.Operation> reelles = new ArrayList<>();
        int reelEnd = reelIdx >= 0 ? reelIdx : values.size();
        for (int i = REEL_OPS_FIRST_ROW - 1; i < reelEnd; i++) {
            MonthData.Operation op = parseOperation(values, i, true, null);
            if (op != null) reelles.add(op);
        }

        List<MonthData.Operation> previsionnelles = new ArrayList<>();
        if (mouvIdx >= 0 && recIdx >= 0) {
            for (int i = mouvIdx + 2; i < recIdx; i++) {
                MonthData.Operation op = parseOperation(values, i, false, "a_venir");
                if (op != null) previsionnelles.add(op);
            }
            for (int i = recIdx + 1; i < values.size(); i++) {
                MonthData.Operation op = parseOperation(values, i, false, "recurrent");
                if (op != null) previsionnelles.add(op);
            }
        } else if (prevIdx >= 0) {
            for (int i = prevIdx + 1; i < values.size(); i++) {
                MonthData.Operation op = parseOperation(values, i, false, "a_venir");
                if (op != null) previsionnelles.add(op);
            }
        }

        return new MonthData(true, monthKey,
                soldeInitialCCM, soldeInitialBoursorama,
                soldeReelCCM, soldeReelBoursorama,
                soldePrevCCM, soldePrevBoursorama,
                reelles, previsionnelles);
    }

    List<List<Object>> readAllRows(String monthKey) throws IOException {
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.getSpreadsheetId(), monthKey + "!A1:D300")
                .execute();
        List<List<Object>> values = response.getValues();
        if (values != null && values.size() == 300) {
            log.warn("readAllRows: onglet '{}' retourne exactement 300 lignes — données potentiellement tronquées", monthKey);
        }
        return values != null ? values : new ArrayList<>();
    }

    List<Object> readRow(String monthKey, int ligne) throws IOException {
        String range = monthKey + "!A" + ligne + ":D" + ligne;
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.getSpreadsheetId(), range)
                .execute();
        List<List<Object>> rows = response.getValues();
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("Ligne introuvable : " + ligne);
        return rows.get(0);
    }

    Integer getSheetId(String monthKey) throws IOException {
        Spreadsheet spreadsheet = sheets.spreadsheets()
                .get(properties.getSpreadsheetId())
                .execute();
        return spreadsheet.getSheets().stream()
                .filter(s -> monthKey.equals(s.getProperties().getTitle()))
                .map(s -> s.getProperties().getSheetId())
                .findFirst()
                .orElse(null);
    }

    int resolveSheetId(String monthKey) throws IOException {
        Integer id = getSheetId(monthKey);
        if (id == null) throw new IllegalStateException("Onglet introuvable : " + monthKey);
        return id;
    }

    String findPreviousMonth(String monthKey) throws IOException {
        List<String> keys = getMonthKeys();
        int targetSortKey = monthSortKey(monthKey);
        String prev = null;
        int prevSortKey = -1;
        for (String key : keys) {
            int sk = monthSortKey(key);
            if (sk < targetSortKey && sk > prevSortKey) {
                prev = key;
                prevSortKey = sk;
            }
        }
        return prev;
    }

    // -------------------------------------------------------------------------
    // Helpers purs — structure
    // -------------------------------------------------------------------------

    int findReelRow(List<List<Object>> values) {
        return findRowByLabel(values, appProperties.getLabelsZones().getReel());
    }

    int findMouvAVenirRow(List<List<Object>> values) {
        return findRowByLabel(values, appProperties.getLabelsZones().getMouvementAVenir());
    }

    int findRecurrentsRow(List<List<Object>> values) {
        return findRowByLabel(values, appProperties.getLabelsZones().getMouvementsRecurrents());
    }

    int findPrevisionnelRow(List<List<Object>> values) {
        return findRowByLabel(values, appProperties.getLabelsZones().getPrevisionnelFinDeMois());
    }

    int findRowByLabel(List<List<Object>> values, String label) {
        String normalized = normalize(label);
        for (int i = 0; i < values.size(); i++) {
            String b = getCellValue(values, i, 1);
            if (b != null && normalize(b).equals(normalized)) return i;
        }
        return -1;
    }

    int findLastFilledReelRow(List<List<Object>> values, int reelIdx) {
        int end = reelIdx >= 0 ? reelIdx : values.size();
        int lastFilled = REEL_OPS_FIRST_ROW - 2;
        for (int i = REEL_OPS_FIRST_ROW - 1; i < end; i++) {
            String b = getCellValue(values, i, 1);
            String c = getCellValue(values, i, 2);
            String d = getCellValue(values, i, 3);
            if ((b != null && !b.isBlank()) || (c != null && !c.isBlank()) || (d != null && !d.isBlank())) {
                lastFilled = i;
            }
        }
        return lastFilled + 2;
    }

    int findSortedInsertRowForDate(List<List<Object>> values, int reelIdx, String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return -1;
        int newDay;
        try {
            newDay = Integer.parseInt(dateStr.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
        int end = reelIdx >= 0 ? reelIdx : values.size();
        for (int i = REEL_OPS_FIRST_ROW - 1; i < end; i++) {
            String colA = getCellValue(values, i, 0);
            if (colA != null && !colA.isBlank()) {
                try {
                    int existingDay = Integer.parseInt(colA.trim());
                    if (existingDay > newDay) return i + 1;
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    int findNextFreeAVenirRow(List<List<Object>> values, int mouvIdx, int recIdx) {
        int start = mouvIdx + 2;
        int end   = recIdx >= 0 ? recIdx : values.size();
        int lastFilled = mouvIdx + 1;
        for (int i = start; i < end; i++) {
            String b = getCellValue(values, i, 1);
            String c = getCellValue(values, i, 2);
            String d = getCellValue(values, i, 3);
            if ((b != null && !b.isBlank()) || (c != null && !c.isBlank()) || (d != null && !d.isBlank())) {
                lastFilled = i;
            }
        }
        return lastFilled + 2;
    }

    int findNextFreePrevRow(List<List<Object>> values, int prevIdx) {
        if (prevIdx < 0) return -1;
        int start = prevIdx + 2;
        int lastFilled = prevIdx;
        for (int i = start; i < values.size(); i++) {
            String b = getCellValue(values, i, 1);
            String c = getCellValue(values, i, 2);
            String d = getCellValue(values, i, 3);
            if ((b != null && !b.isBlank()) || (c != null && !c.isBlank()) || (d != null && !d.isBlank())) {
                lastFilled = i;
            }
        }
        return lastFilled + 2;
    }

    // -------------------------------------------------------------------------
    // Helpers purs — parsing
    // -------------------------------------------------------------------------

    MonthData.Operation parseOperation(List<List<Object>> values, int i, boolean withDate, String type) {
        String libelle = getCellValue(values, i, 1);
        if (libelle == null || libelle.isBlank()) return null;
        String norm = normalize(libelle);
        if (norm.contains("prevision fin mois"))  return null;
        if (norm.equals("mouvement a venir"))      return null;
        if (norm.equals("mouvements recurrents"))  return null;
        if (norm.equals("reel"))                   return null;
        String montantC1 = getCellValue(values, i, 2);
        String montantC2 = getCellValue(values, i, 3);
        if (montantC1 != null && !montantC1.isBlank()) {
            String date = withDate ? getCellValue(values, i, 0) : null;
            return new MonthData.Operation(i + 1, date, libelle, appProperties.getComptes().getCompte1(), parseDouble(montantC1), type);
        } else if (montantC2 != null && !montantC2.isBlank()) {
            String date = withDate ? getCellValue(values, i, 0) : null;
            return new MonthData.Operation(i + 1, date, libelle, appProperties.getComptes().getCompte2(), parseDouble(montantC2), type);
        }
        return null;
    }

    String normalize(String s) {
        return s.toLowerCase()
                .replace("é", "e").replace("è", "e").replace("ê", "e")
                .replace("à", "a").replace("â", "a")
                .replace("î", "i").replace("ô", "o").replace("û", "u")
                .trim();
    }

    Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    String getCellValue(List<List<Object>> values, int rowIndex, int colIndex) {
        if (rowIndex >= values.size()) return null;
        List<Object> row = values.get(rowIndex);
        if (row == null || colIndex >= row.size()) return null;
        Object val = row.get(colIndex);
        return val != null ? val.toString() : null;
    }

    int monthSortKey(String monthKey) {
        for (Map.Entry<String, Integer> entry : MONTH_ORDER.entrySet()) {
            if (monthKey.startsWith(entry.getKey())) {
                try {
                    int year = Integer.parseInt(monthKey.substring(entry.getKey().length()));
                    return year * 100 + entry.getValue();
                } catch (NumberFormatException e) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return Integer.MAX_VALUE;
    }
}
