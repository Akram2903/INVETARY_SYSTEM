FROM eclipse-temurin:17-jdk AS build

WORKDIR /app
COPY src ./src

RUN mkdir -p target/classes && \
    javac -d target/classes src/main/java/com/inventory/InventoryServer.java

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /app/target/classes ./target/classes
COPY public ./public

CMD ["java", "-cp", "target/classes", "com.inventory.InventoryServer"]
