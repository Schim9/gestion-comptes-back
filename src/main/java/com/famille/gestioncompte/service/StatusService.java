package com.famille.gestioncompte.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class StatusService {

    private final SheetReader sheetReader;

    public StatusService(SheetReader sheetReader) {
        this.sheetReader = sheetReader;
    }

    public List<String> getAnomalies() {
        List<String> anomalies = new ArrayList<>();
        try {
            sheetReader.checkConnectivity();
        } catch (Exception e) {
            log.warn("Google Sheets inaccessible : {}", e.getMessage());
            anomalies.add("Google Sheets inaccessible : " + e.getMessage());
        }
        return anomalies;
    }
}
