RAPPORT TECHNIQUE : MODULE D'AUTHENTIFICATION ET DE GESTION DOCUMENTAIRE
1. Presentation du Projet

Ce module backend assure la gestion securisee des utilisateurs et le stockage de leurs documents d'identite. Il repose sur une architecture reactive (Spring WebFlux) permettant de gerer les flux de donnees de maniere non-bloquante entre l'utilisateur, le serveur de fichiers et la base de donnees.
2. Environnement Technologique et Infrastructure Docker

L'application s'appuie sur des services conteneurises. Il est imperatif de lancer les conteneurs suivants manuellement avant le demarrage du serveur Spring :

    Base de donnees PostgreSQL

        Nom du conteneur : postgre-reseau

        Commande : docker start postgre-reseau

        Port d'ecoute : 5433

    Stockage d'objets MinIO

        Nom du conteneur : minio-reseau

        Commande : docker start minio-reseau

        Port d'ecoute : 9000

3. Configuration et Securite (application.properties)

Les identifiants de connexion aux services Docker sont les suivants :

Connexion PostgreSQL (R2DBC) :

    URL : r2dbc:postgresql://localhost:5433/stock_doc

    Utilisateur : amina

    Mot de passe : 1234

Connexion MinIO (S3 API) :

    URL : http://localhost:9000

    Access-Key : minioadmin

    Secret-Key : minioadmin123

    Bucket cible : stockdoc

4. Analyse de l'Architecture des Packages

    package Projects.Network.config : Contient les classes de configuration technique (MinioConfig, SecurityConfig). Definit les Beans pour la connexion aux services externes et les regles de securite.

    package Projects.Network.controller : Heberge les points d'entree de l'API. Traite les requetes HTTP (Multipart pour l'upload) et renvoie les reponses.

    package Projects.Network.service : Logique metier complexe. Coordonne l'envoi de fichiers vers MinIO et l'enregistrement des metadonnees dans PostgreSQL.

    package Projects.Network.repository : Interfaces de communication reactive avec PostgreSQL via Spring Data R2DBC.

    package Projects.Network.model : Definitions des entites persistantes (User, DocumentEntity) mappees sur les tables de la base de donnees.

    package Projects.Network.security : Contient le filtre JWT et le service de gestion des tokens pour l'authentification.

    package Projects.Network.dto : Objets de transfert de donnees pour structurer les echanges sans exposer les entites de la base.

5. Guide de Test avec Postman
Etape 1 : Authentification

    Methode : POST

    URL : http://localhost:8080/api/auth/login

    Body (JSON) : {"username": "votre_nom", "password": "votre_password"}

    Action : Copier le "token" recu dans la reponse.

Etape 2 : Upload de Document

    Methode : POST

    URL : http://localhost:8080/api/documents/upload

    Headers : Ajouter Authorization avec la valeur Bearer <votre_token>

    Body (form-data) :

        Cle file (type File) : Choisir votre fichier.

        Cle pieceType (type Text) : Indiquer PASSPORT ou ID_CARD.

6. Dependances Majeures (Extraits du pom.xml)

    Spring Boot Starter Security : Gestion de la securite.

    Spring Boot Data R2DBC : Acces base de donnees reactif.

    MinIO SDK (8.5.7) : Client pour le stockage d'objets.

    io.jsonwebtoken (0.11.5) : Librairie pour les tokens JWT.
