package com.famille.gestioncompte.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google.sheets")
public class GoogleSheetsProperties {
    private String spreadsheetId;
    private String credentialsPath;

    public String getSpreadsheetId() { return spreadsheetId; }
    public void setSpreadsheetId(String spreadsheetId) { this.spreadsheetId = spreadsheetId; }
    public String getCredentialsPath() { return credentialsPath; }
    public void setCredentialsPath(String credentialsPath) { this.credentialsPath = credentialsPath; }
}
