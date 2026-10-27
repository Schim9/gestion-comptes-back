package com.famille.gestioncompte.controller;

import com.famille.gestioncompte.service.StatusService;
import com.famille.gestioncompte.util.DateUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DiagnosticController {

    private final StatusService statusService;
    private final DateUtil dateUtil;

    public DiagnosticController(StatusService statusService, DateUtil dateUtil) {
        this.statusService = statusService;
        this.dateUtil = dateUtil;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appCode", "GESTION_COMPTE");
        body.put("status", "up");
        body.put("uptime", dateUtil.formatUptime());
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("anomalies", statusService.getAnomalies());
        return ResponseEntity.ok(body);
    }
}
