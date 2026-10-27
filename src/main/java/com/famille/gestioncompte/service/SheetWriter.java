package com.famille.gestioncompte.service;

import com.famille.gestioncompte.config.AppProperties;
import com.famille.gestioncompte.config.GoogleSheetsProperties;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SheetWriter {

    private final Sheets sheets;
    private final GoogleSheetsProperties properties;
    private final AppProperties appProperties;

    public SheetWriter(Sheets sheets, GoogleSheetsProperties properties, AppProperties appProperties) {
        this.sheets = sheets;
        this.properties = properties;
        this.appProperties = appProperties;
    }

    public void writeOperation(String monthKey, int ligne, String date,
                               String libelle, String compte, Double montant) throws IOException {
        List<Object> row = new ArrayList<>();
        row.add(date != null ? date : "");
        row.add(libelle);
        row.add(appProperties.getComptes().getCompte1().equals(compte) ? montant : "");
        row.add(appProperties.getComptes().getCompte2().equals(compte) ? montant : "");
        String range = monthKey + "!A" + ligne + ":D" + ligne;
        ValueRange body = new ValueRange().setRange(range).setValues(List.of(row));
        sheets.spreadsheets().values()
                .update(properties.getSpreadsheetId(), range, body)
                .setValueInputOption("RAW")
                .execute();
    }

    public void writeSingleValue(String monthKey, String cellRef, Object value) throws IOException {
        String range = monthKey + "!" + cellRef;
        ValueRange body = new ValueRange()
                .setRange(range)
                .setValues(List.of(List.of(value)));
        sheets.spreadsheets().values()
                .update(properties.getSpreadsheetId(), range, body)
                .setValueInputOption("RAW")
                .execute();
    }

    public void insertRowsBefore(int sheetId, int ligneAvant1Based, int count) throws IOException {
        int rowIndex = ligneAvant1Based - 1;
        InsertDimensionRequest insert = new InsertDimensionRequest()
                .setRange(new DimensionRange()
                        .setSheetId(sheetId)
                        .setDimension("ROWS")
                        .setStartIndex(rowIndex)
                        .setEndIndex(rowIndex + count))
                .setInheritFromBefore(true);
        sheets.spreadsheets().batchUpdate(properties.getSpreadsheetId(),
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(List.of(new Request().setInsertDimension(insert))))
                .execute();
    }

    public void deleteRow(int sheetId, int ligne) throws IOException {
        DeleteDimensionRequest delete = new DeleteDimensionRequest()
                .setRange(new DimensionRange()
                        .setSheetId(sheetId)
                        .setDimension("ROWS")
                        .setStartIndex(ligne - 1)
                        .setEndIndex(ligne));
        sheets.spreadsheets().batchUpdate(properties.getSpreadsheetId(),
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(List.of(new Request().setDeleteDimension(delete))))
                .execute();
    }

    public void clearAmountCell(String monthKey, int ligne, String compte) throws IOException {
        String col = appProperties.getComptes().getCompte1().equals(compte) ? "C" : "D";
        sheets.spreadsheets().values()
                .clear(properties.getSpreadsheetId(), monthKey + "!" + col + ligne,
                        new ClearValuesRequest())
                .execute();
    }

    public void clearRange(String monthKey, String range) throws IOException {
        sheets.spreadsheets().values()
                .clear(properties.getSpreadsheetId(), monthKey + "!" + range,
                        new ClearValuesRequest())
                .execute();
    }

    public void updateReelFormulas(String monthKey, int newReelRow, int newRangeEnd) throws IOException {
        String range = monthKey + "!C" + newReelRow + ":D" + newReelRow;
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.getSpreadsheetId(), range)
                .setValueRenderOption("FORMULA")
                .execute();
        List<List<Object>> rows = response.getValues();
        if (rows == null || rows.isEmpty()) return;
        List<Object> updatedRow = new ArrayList<>();
        for (Object cell : rows.get(0)) {
            String updated = cell.toString()
                    .replaceAll("([CD])(\\d+):", "$1" + SheetReader.REEL_OPS_FIRST_ROW + ":")
                    .replaceAll(":([CD])(\\d+)", ":$1" + newRangeEnd);
            updatedRow.add(updated);
        }
        sheets.spreadsheets().values()
                .update(properties.getSpreadsheetId(), range,
                        new ValueRange().setRange(range).setValues(List.of(updatedRow)))
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    public void duplicateSheet(int sourceSheetId, String newSheetName) throws IOException {
        DuplicateSheetRequest duplicate = new DuplicateSheetRequest()
                .setSourceSheetId(sourceSheetId)
                .setNewSheetName(newSheetName);
        sheets.spreadsheets().batchUpdate(properties.getSpreadsheetId(),
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(List.of(new Request().setDuplicateSheet(duplicate))))
                .execute();
    }
}
