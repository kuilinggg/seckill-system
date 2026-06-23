FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml .
COPY user-service/pom.xml user-service/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml

RUN mvn -B -ntp dependency:go-offline

COPY . .

ARG MODULE
RUN mvn -B -ntp -pl ${MODULE} -am package -DskipTests \
    && cp ${MODULE}/target/*.jar /workspace/app.jar

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

ENV SERVER_PORT=8080

COPY --from=builder /workspace/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --server.port=${SERVER_PORT}"]
