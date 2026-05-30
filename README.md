# VerifID Backend (New-Backend-Network)

## Système de vérification de documents et gestion multi-tenante (B2B)

Ce projet est un backend réactif et performant développé avec **Spring WebFlux**. Il permet aux plateformes (tenants) de vérifier des documents d'identité de manière sécurisée grâce à une extraction par OCR et une analyse sémantique propulsée par l'IA.

Le projet suit rigoureusement une **Architecture Hexagonale (Ports and Adapters)** inspirée de `RT-Comops-file-core`, garantissant un découplage total entre le cœur métier et les dépendances techniques (frameworks, bases de données, API externes).

---

## 🚀 Fonctionnalités Principales

- **Architecture Réactive** : Basée sur Project Reactor (WebFlux) pour traiter un grand volume de requêtes de manière non bloquante.
- **Architecture Hexagonale Stricte** : Logique métier pure (Domain/Application) isolée de l'infrastructure (Adapters).
- **Multi-Tenancy (B2B)** : Gestion sécurisée des plateformes via authentification JWT et clés d'API rotatives.
- **Analyse Documentaire Avancée** :
  - **OCR** : Extraction brute du texte depuis des images ou des PDF.
  - **IA (Google Gemini)** : Analyse intelligente pour structurer les données extraites (Nom, Prénom, Dates d'expiration) et détecter les fraudes potentielles.
- **Tableau de bord & Exports** : Génération de statistiques complètes et exports en PDF/CSV.
- **Sécurité et Emails** : Authentification complète (OTP, Mot de passe oublié) via l'API Brevo.

---

## 📂 Structure du Projet (Hexagonal Architecture)

Le code source sous `src/main/java/com/projects/` est organisé comme suit :

```text
com.projects
├── domain
│   └── model/                  # Entités métier pures (POJOs sans annotations Spring/R2DBC)
├── application
│   ├── port/
│   │   ├── in/                 # Interfaces entrantes (Use Cases)
│   │   └── out/                # Interfaces sortantes (Repositories, Services externes)
│   └── service/                # Implémentations des Use Cases (logique d'orchestration)
├── adapter
│   ├── in/
│   │   └── web/                # Contrôleurs REST (Spring WebFlux)
│   │       └── dto/            # Objets de transfert de données pour l'API
│   └── out/
│       ├── persistence/        # Accès R2DBC (Entités R2DBC, Repositories, Mappers, Adapters)
│       ├── security/           # Adaptateurs de tokens (JJWT, Hachage)
│       ├── email/              # Adaptateur Brevo pour l'envoi d'emails (OTP)
│       ├── ai/                 # Adaptateur Google Gemini
│       └── ocr/                # Adaptateur pour l'extraction de texte (OCR)
└── config/                     # Configuration globale (Beans Spring, Filtres WebFlux, Swagger)
```

---

## 🛠 Stack Technique

- **Core** : Java 17+, Spring Boot 3, Spring WebFlux.
- **Persistance** : PostgreSQL avec Spring Data R2DBC (Réactif).
- **Génération de PDF/CSV** : iTextPDF, OpenCSV.
- **Sécurité** : JJWT (JSON Web Token), BCrypt, `X-API-KEY`.
- **Intégrations Externes** : Google Gemini API, Brevo API (Emailing).

---

## 📖 Principaux Points d'Entrée de l'API

L'application expose plusieurs groupes d'API (visibles sur Swagger via `/swagger-ui.html`) :

### 1. Authentification des Plateformes (`/api/auth`)
- `POST /register` : Inscription d'une plateforme.
- `POST /verify-email` : Vérification du compte via code OTP envoyé par email.
- `POST /login` : Connexion et récupération du JWT.

### 2. Vérification Documentaire (`/api/documents`)
- `POST /upload-analyze` : Endpoint principal pour soumettre des documents (Recto/Verso). Le processus enchaîne OCR -> IA -> Enregistrement des logs. *(Nécessite le Header `X-API-KEY`)*.

### 3. Tableau de Bord et Métriques (`/api/dashboard`)
- `GET /stats` : Statistiques clés (taux de succès, vérifications récentes).
- `GET /export/pdf` & `GET /export/csv` : Exportation des rapports de vérification.

### 4. Administration Globale (SuperAdmin) (`/api/admin`)
- Gestion globale des plateformes, statistiques transversales, désactivation de comptes.

---

## ⚙️ Démarrage Rapide

### Prérequis
- Java 17+
- PostgreSQL
- Maven

### Variables d'Environnement
Assurez-vous de définir les variables suivantes dans `application.properties` ou dans votre environnement de déploiement :
- `DB_PASSWORD` : Mot de passe de la base de données.
- `BREVO_API_KEY` : Clé API pour l'envoi des emails.
- `GEMINI_API_KEY` : Clé API pour l'analyse IA.
- `JWT_SECRET` : Clé secrète pour la signature des tokens de session.
- `ENCRYPTION_SECRET_KEY` : Clé secrète AES-256.

### Lancer l'application
```bash
./mvnw clean spring-boot:run
```

L'application démarrera par défaut sur le port 8080.
Vous pouvez consulter la documentation Swagger à l'adresse : `http://localhost:8080/swagger-ui.html`.
