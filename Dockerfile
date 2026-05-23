# ==========================================
# ETAPA 1: Construcción (Build)
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copiamos archivos de Maven y descargamos dependencias para acelerar futuras construcciones
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline

# Copiamos el código fuente y compilamos el archivo .jar ignorando los tests
COPY src src
RUN ./mvnw clean package -DskipTests

# ==========================================
# ETAPA 2: Ejecución (Producción)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiamos solo el archivo ejecutable de la etapa anterior
COPY --from=build /app/target/*.jar app.jar

# Exponemos el puerto que usa tu aplicación
EXPOSE 8081

# Comando para iniciar Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]