GUIDE TECHNIQUE : UPLOAD DE DOCUMENTS ET SECURITE JWT

    PRESENTATION DU FLUX DE SECURITE Pour que l'utilisateur puisse uploader un document sans envoyer son ID manuellement, nous utilisons le jeton JWT. Le processus est le suivant :

    Le client envoie le jeton dans le header Authorization.

    Le JwtFilter intercepte la requete et utilise JwtService pour valider le jeton.

    L'identite de l'utilisateur est extraite et placee dans le contexte de securite de Spring (Principal).

    Le DocumentController recupere l'ID utilisateur directement via l'annotation @AuthenticationPrincipal.

    CORRECTIONS APPORTEES AU PROJET

2.1. Gestion de la Casse (Case Sensitivity) Le serveur est sensible a la casse pour les parametres multipart.

    Erreur : Postman envoyait "piecetype" alors que le code attendait "pieceType".

    Correction : Les cles dans Postman et dans le code sont desormais synchronisees sur "pieceType".

2.2. Resolution du NullPointerException L'objet "principal" etait null dans le controleur, provoquant une erreur 500.

    Cause : Le filtre de securite ne remplissait pas le contexte de Spring WebFlux.

    Solution : Creation de la classe JwtFilter et mise a jour de SecurityConfig pour activer l'authentification automatique a chaque requete.

2.3. Restauration du AuthService La refactorisation du JwtService avait supprime la methode generateToken().

    Solution : Re-implementation de generateToken() pour permettre la creation de jetons lors de la connexion (Login).

    UTILISATION AVEC POSTMAN

Etape 1 : Authentification

    Methode : POST

    URL : http://localhost:8080/api/auth/login

    Action : Recuperer la valeur du "token" dans la reponse JSON.

Etape 2 : Upload du document

    Methode : POST

    URL : http://localhost:8080/api/documents/upload

    Header : Ajouter "Authorization" avec la valeur "Bearer VOTRE_TOKEN".

    Body (selectionner form-data) :

        Cle 1 : "file" (changer le type en "File" dans Postman et choisir un fichier).

        Cle 2 : "pieceType" (type "Text", valeur : PASSPORT ou ID_CARD).

    STRUCTURE DU CODE ET NORMES

    Architecture : Le projet suit le modele Controller -> Service -> Repository.

    Reactivite : Utilisation des types Mono et Flux (Project Reactor) pour assurer des performances optimales en environnement WebFlux.

    Reponses API : Les succes retournent desormais un code HTTP 201 (Created) avec un message de confirmation JSON.

    DEPENDANCES REQUISES Pour le bon fonctionnement des annotations et du JWT, les dependances suivantes doivent etre presentes dans le pom.xml :

    spring-boot-starter-security

    spring-boot-starter-webflux

    jjwt-api / jjwt-impl / jjwt-jackson

    jakarta.annotation-api
