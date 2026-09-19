FROM node:20-alpine AS frontend-build
RUN apk add --no-cache git
WORKDIR /frontend
RUN git clone --depth 1 https://github.com/silver-bullet007/codesentry-frontend.git .
RUN npm install
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B
COPY src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]