package com.famille.gestioncompte.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String username;
    private String password;
    private String libelleBudgetCourses = "Course";
    private Comptes comptes = new Comptes();
    private LabelsZones labelsZones = new LabelsZones();

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getLibelleBudgetCourses() { return libelleBudgetCourses; }
    public void setLibelleBudgetCourses(String v) { this.libelleBudgetCourses = v; }
    public Comptes getComptes() { return comptes; }
    public void setComptes(Comptes comptes) { this.comptes = comptes; }
    public LabelsZones getLabelsZones() { return labelsZones; }
    public void setLabelsZones(LabelsZones labelsZones) { this.labelsZones = labelsZones; }

    public static class Comptes {
        private String compte1 = "CCM";
        private String compte2 = "Boursorama";

        public String getCompte1() { return compte1; }
        public void setCompte1(String compte1) { this.compte1 = compte1; }
        public String getCompte2() { return compte2; }
        public void setCompte2(String compte2) { this.compte2 = compte2; }
    }

    public static class LabelsZones {
        private String reel = "Réel";
        private String mouvementAVenir = "Mouvement à venir";
        private String mouvementsRecurrents = "Mouvements récurrents";
        private String previsionnelFinDeMois = "Prévisionnel fin de mois";

        public String getReel() { return reel; }
        public void setReel(String reel) { this.reel = reel; }
        public String getMouvementAVenir() { return mouvementAVenir; }
        public void setMouvementAVenir(String v) { this.mouvementAVenir = v; }
        public String getMouvementsRecurrents() { return mouvementsRecurrents; }
        public void setMouvementsRecurrents(String v) { this.mouvementsRecurrents = v; }
        public String getPrevisionnelFinDeMois() { return previsionnelFinDeMois; }
        public void setPrevisionnelFinDeMois(String v) { this.previsionnelFinDeMois = v; }
    }
}
