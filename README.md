# GestionCompte API

API REST Spring Boot pour piloter un budget familial stocké dans Google Sheets.

## Prérequis

- Java 21
- Un Google Sheet structuré (voir ci-dessous)
- Un compte de service Google avec accès au sheet (Service Account OAuth2)

---

## Configuration

### Variables d'environnement (production / systemd)

| Variable | Description |
|---|---|
| `SPREADSHEET_ID` | ID du Google Sheet (dans l'URL) |
| `CREDENTIALS_PATH` | Chemin vers le fichier JSON du compte de service |
| `JWT_SECRET` | Clé secrète JWT (32 caractères minimum) |
| `APP_USERNAME` | Identifiant de connexion |
| `APP_PASSWORD` | Mot de passe hashé BCrypt |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées (ex : `http://localhost:4200`) |
| `SERVER_PORT` | Port HTTP (défaut : `8080`) |

### Noms des comptes et labels du sheet (optionnel)

Ces valeurs ont des défauts qui correspondent à une configuration standard.
À surcharger si vos intitulés de colonnes ou de zones diffèrent.

| Variable | Défaut |
|---|---|
| `APP_COMPTE1` | `CCM` |
| `APP_COMPTE2` | `Boursorama` |
| `APP_LABEL_REEL` | `Réel` |
| `APP_LABEL_MOUVEMENT_A_VENIR` | `Mouvement à venir` |
| `APP_LABEL_MOUVEMENTS_RECURRENTS` | `Mouvements récurrents` |
| `APP_LABEL_PREVISIONNEL` | `Prévisionnel fin de mois` |

### Développement local

Créer `src/main/resources/application-local.yml` (ignoré par git) :

```yaml
google:
  sheets:
    spreadsheet-id: "1ABC..."
    credentials-path: "/home/user/.config/gestion-compte/credentials.json"

jwt:
  secret: "une-cle-secrete-de-32-caracteres-min"

app:
  username: admin
  password: "$2a$10$..."  # BCrypt

cors:
  allowed-origins: "http://localhost:4200"

# Optionnel — si vos labels diffèrent des défauts
app:
  comptes:
    compte1: "LCL"
    compte2: "Revolut"
  labels-zones:
    reel: "Réel"
    mouvement-a-venir: "À venir"
    mouvements-recurrents: "Récurrents"
    previsionnel-fin-de-mois: "Prévision fin mois"
```

Lancer avec le profil local :
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## Structure du Google Sheet

Chaque mois correspond à un onglet nommé `{Mois}{Année}` sans accent ni espace,
par exemple `Octobre2025` ou `Fevrier2026`.

Un onglet `TEMPLATE` sert de modèle pour la création automatique d'un nouveau mois.

### Colonnes

| A | B | C | D |
|---|---|---|---|
| Jour | Libellé | Compte 1 | Compte 2 |

### Structure d'un onglet

```
     A          B                            C          D
─────────────────────────────────────────────────────────────
 1 │           │                            │           │
 2 │           │                            │  1 450,00 │  2 300,00   ← soldes initiaux (fixes)
 3 │           │                            │           │
 4 │  3        │ Courses supermarché        │    -85,50 │             ← zone réelle (opérations débitées)
 5 │  5        │ Loyer                      │  -750,00  │
 6 │  8        │ Remboursement mutuelle     │            │    -42,00
 7 │  12       │ Restaurant                 │    -38,00 │
   │           │                            │           │
 … │  …        │ Réel                       │   534,50  │  2 258,00   ← label "Réel" + formule soldes réels
   │           │                            │           │
   │           │ Mouvement à venir          │           │             ← label zone prévisionnelle ponctuelle
   │           │                            │           │             ← ligne vide (préservée)
   │           │ Virement épargne           │  -200,00  │
   │           │ Abonnement streaming       │    -15,99 │
   │           │                            │           │
   │           │ Mouvements récurrents      │           │             ← label zone récurrente
   │           │ Salaire                    │            │  2 800,00
   │           │ Assurance habitation       │    -28,00 │
   │           │ Forfait téléphone          │    -19,99 │
   │           │                            │           │
   │           │ Prévisionnel fin de mois   │   270,52  │  5 058,00   ← label + formule soldes prévisionnels
```

### Règles importantes

- La ligne 2 (`C2`/`D2`) contient les soldes initiaux du mois — **ne pas y mettre de formule**.
  Elle est renseignée automatiquement lors de la création d'un nouveau mois
  (reprise des soldes prévisionnels du mois précédent).
- Les lignes de label (`Réel`, `Mouvement à venir`, etc.) sont détectées dynamiquement —
  l'orthographe doit correspondre aux variables `APP_LABEL_*`.
- La ligne vide après `Mouvement à venir` est préservée lors de la réinitialisation mensuelle.
- Les opérations récurrentes sont conservées d'un mois sur l'autre ; seul leur montant
  est effacé lors du pointage.

---

## Endpoints principaux

| Méthode | URL | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Non | Retourne un token JWT |
| GET | `/api/status` | Non | Statut de l'API et connectivité Google Sheets |
| GET | `/api/months` | Oui | Liste des mois disponibles |
| GET | `/api/months/{key}` | Oui | Données du mois (soldes + opérations) |
| POST | `/api/months/{key}/operations` | Oui | Ajoute une opération |
| POST | `/api/months/{key}/pointer/{ligne}` | Oui | Pointe une opération prévisionnelle |
