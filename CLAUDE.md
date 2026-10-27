# GestionComptes — Back-end (Spring Boot)

## Stack
- Java 21, Spring Boot 3.2.5
- Spring Security (JWT stateless, BCrypt)
- Google Sheets API v4 (Service Account OAuth2)
- JJWT 0.12.5

## Package racine
`com.famille.gestioncompte`

## Structure des packages

```
config/
  GoogleSheetsConfig.java       Service Account OAuth2
  GoogleSheetsProperties.java   spreadsheet-id, credentials-path
  AppProperties.java            app.username/password, comptes (compte1/compte2),
                                libelle-budget-courses, labels-zones (configurables)
  CorsProperties.java           @ConfigurationProperties(prefix="cors"), origines CORS
  SecurityConfig.java           JWT stateless, CORS, BCrypt

controller/
  AuthController.java           POST /api/auth/login
  MonthController.java          CRUD mois + opérations
  DiagnosticController.java     GET /api/status
  GlobalExceptionHandler.java

dto/
  MonthData.java                réponse GET mois (record + Operation inner record)
  OperationRequest.java         body POST opération
  OperationResponse.java        {created, message}
  UpdateOperationRequest.java   libelle, date, montant, compte
  PointerRequest.java           date, libelle, montant, deduireDesCourses + montantAsDouble()
  LoginRequest.java / LoginResponse.java

exception/
  MonthNotFoundException.java   mappée en 404 par GlobalExceptionHandler

security/
  JwtService.java               génération/validation token
  JwtAuthenticationFilter.java  OncePerRequestFilter
  UserDetailsServiceImpl.java   utilisateur unique depuis application.properties

service/
  GoogleSheetsService.java      orchestrateur métier pur, injecte SheetReader + SheetWriter
  SheetReader.java              toutes les lectures API Google Sheets + helpers purs
  SheetWriter.java              toutes les écritures API Google Sheets
  StatusService.java            injecte SheetReader, checkConnectivity()

util/
  DateUtil.java
```

## Endpoints

| Méthode | URL | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Non | Retourne JWT |
| GET | `/api/status` | Non | Statut + anomalies Google Sheets |
| GET | `/api/months` | Oui | Liste des onglets (hors TEMPLATE) triés |
| GET | `/api/months/{key}` | Oui | Données du mois |
| POST | `/api/months/{key}/operations` | Oui | Ajoute une opération (crée le mois si besoin) |
| PATCH | `/api/months/{key}/operations/{ligne}` | Oui | Modifie une opération existante |
| DELETE | `/api/months/{key}/operations/{ligne}` | Oui | Supprime une opération |
| POST | `/api/months/{key}/pointer/{ligne}` | Oui | Pointe une opération prévisionnelle |
| POST | `/api/months/{key}/depointer-reel/{ligne}` | Oui | Dépointé : remet en zone "à venir" + supprime du réel |

Endpoints publics : `/api/auth/login`, `/api/status`

## Structure du Google Sheet

Chaque mois = un onglet `{Mois}{Année}` sans accent : `Octobre2026`, `Fevrier2027`

Mois sans accent : `Janvier, Fevrier, Mars, Avril, Mai, Juin, Juillet, Aout, Septembre, Octobre, Novembre, Decembre`

Colonnes : A=date (numéro jour), B=libellé, C=compte1 (défaut CCM), D=compte2 (défaut Boursorama)

```
Ligne 2   : C2=solde initial CCM, D2=solde initial Boursorama (FIXE)
Lignes 4→ : ZONE REELLE
[label]   : "Réel"           → formule SUM soldes réels C et D
[vide]
[label]   : "Mouvement à venir" + ligne vide préservée
              ops ponctuelles (type="a_venir")
[label]   : "Mouvements récurrents"
              EDF, salaire, abonnements… (type="recurrent")
[label]   : "Prévisionnel fin de mois" → formule soldes prévisionnels
[label]   : "Prévision Fin Mois"
```

Labels détectés dynamiquement : scan col B, normalisation minuscules + suppression accents + trim.

## Règles de gestion

### Ajout opération

**dejaDebite=true** → zone réelle
1. `findLastFilledReelRow` → dernière ligne remplie zone réelle
2. `insertRowsBefore` avec `inheritFromBefore=true` si besoin
3. `updateReelFormulas` : regex `:C\d+` → `:C{nouvelleFin}` sur la ligne "Réel"
4. Écrire l'opération avec sa date

**dejaDebite=false** → zone "Mouvement à venir"
1. `findNextFreeAVenirRow`
2. Écrire sans date

### Pointage

Body `PointerRequest` (tous optionnels) : `date` (jour 1-31), `libelle` (modifiable), `montant` (override), `deduireDesCourses` (boolean).

- zone `a_venir` (ligne < recIdx) → copie en zone réelle + `deleteRow` (ligne supprimée)
- zone `recurrent` (ligne > recIdx) → copie en zone réelle + `clearAmountCell` (ligne conservée)

**Décalage critique** : après `insertRowsBefore`, corriger via `computeActualLigne(ligne, insertRow, rowsToInsert)`.

**Budget courses** : si `deduireDesCourses=true`, le montant est soustrait de la ligne identifiée par `app.libelle-budget-courses` dans les récurrents.

### Dépointage

`POST /depointer-reel/{ligne}` : lit la ligne réelle, la réinsère en zone "à venir" (via `addOperation` avec `dejaDebite=false`), puis supprime la ligne réelle originale.

### Création nouveau mois

Déclenchée automatiquement au 1er POST /operations sur un mois inexistant.

1. Trouver le mois précédent (tri par `année*100+mois`)
2. Dupliquer l'onglet du mois précédent (fallback : TEMPLATE)
3. `C2:D2` ← valeurs de la ligne "Prévisionnel fin de mois" du mois précédent
4. Effacer zone réelle : `A4:D{ligneReel-1}`
5. Effacer zone "Mouvement à venir" (labels + ligne vide préservés)
6. Conserver "Mouvements récurrents" avec leurs montants

## Configuration (`application.yml`)

Uniquement des variables d'environnement (`${VAR:default}`). Les valeurs dev locales sont dans `application-local.yml` (dans `.gitignore`).

Variables d'environnement systemd à définir :
- `SPREADSHEET_ID`, `CREDENTIALS_PATH`
- `JWT_SECRET` (32 chars min), `JWT_EXPIRATION` (ms, défaut 86400000), `APP_USERNAME`, `APP_PASSWORD` (BCrypt)
- `CORS_ALLOWED_ORIGINS`, `SERVER_PORT`
- `APP_COMPTE1` (défaut `CCM`), `APP_COMPTE2` (défaut `Boursorama`)
- `APP_LIBELLE_COURSES` (défaut `Course`)
- Labels zones (tous optionnels) : `APP_LABEL_REEL`, `APP_LABEL_MOUVEMENT_A_VENIR`, `APP_LABEL_MOUVEMENTS_RECURRENTS`, `APP_LABEL_PREVISIONNEL`

## Déploiement

Raspberry Pi, JAR lancé via service systemd.

> **Skill a utiliser** : pour tout ce qui concerne le déploiement sur le Pi (transfert du JAR, commandes systemd, config serveur, chemins), utiliser la skill `info-pi-apps2` qui décrit la configuration exacte du Raspberry Pi cible.
