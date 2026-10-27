# TODO — gestion-compte-api

Issu de la revue architecturale du 2026-10-26.

---

## MOYENNE PRIORITE

- [x] **Tests unitaires — fonctions prioritaires**
  - `normalize`, `monthSortKey`, `findSortedInsertRowForDate`, `parseOperation`, `parseDouble`, `findLastFilledReelRow`, `findRowByLabel`
  - Déplacés dans `SheetReaderTest` suite au refactoring

---

## LONG TERME

- [x] **Découper `GoogleSheetsService`**
  - `SheetReader` — lecture, navigation et helpers purs
  - `SheetWriter` — toutes les opérations d'écriture API
  - `GoogleSheetsService` — orchestrateur métier (délègue à reader/writer)

- [ ] **Profil Spring `test` avec mock Google Sheets**
  - Permettre des tests d'intégration sans dépendance réseau
