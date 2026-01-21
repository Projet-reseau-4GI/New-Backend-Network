# Multi-stage build pour optimiser la taille
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copier pom.xml et télécharger les dépendances (cache layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copier le code source
COPY src ./src

# Build (skip tests pour plus rapide)
RUN mvn clean package -DskipTests

# Stage final - image runtime légère
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copier le JAR depuis le stage build
COPY --from=build /app/target/*.jar app.jar

# Variables d'environnement par défaut
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SERVER_PORT=8080

# Variables d'environnement pour le mail
ENV MAIL_HOST=smtp.gmail.com
ENV MAIL_PORT=587
ENV MAIL_USERNAME="tadidajalil01@gmail.com"
ENV MAIL_PASSWORD="turp sgnc bghu sztr"

# Exposer le port de l'application
EXPOSE 8080

# Note: Les ports SMTP (587, 465, 25) sont des ports SORTANTS
# Votre application se connecte à des serveurs SMTP externes
# Pas besoin de les exposer dans Docker
#
# Si vous avez besoin d'autoriser les connexions sortantes SMTP:
# - Port 587 (TLS/STARTTLS) - recommandé
# - Port 465 (SSL)
# - Port 25 (non sécurisé, souvent bloqué)

# Healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Lancer l'application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]