# Stockroom - Java Inventory Management System

An interactive inventory management mini project implementing the operations
from the Group C problem statement:

- List all products
- Display individual product information
- Purchase / restock
- Shipping / sale
- Balance stock
- Loss and profit calculation
- Purchase and transaction report by date range

## Main implementation

The inventory domain is implemented in Java 17 using object-oriented classes:

- `Product` stores product identity, category, stock, cost price, and selling price.
- `Transaction` records purchases and shipments.
- `Inventory` owns the product catalogue and transaction collection and performs
  stock changes, balance calculations, profit calculations, and reporting.
- `InventoryServer` provides the Java HTTP API and serves the frontend.

## Run locally

Requirement: Java 17 or newer.

```powershell
cd D:\c++\inventory-management-java
javac -d target\classes src\main\java\com\inventory\InventoryServer.java
java -cp target\classes com.inventory.InventoryServer
```

Open `http://localhost:3000`.

## API

- `GET /api/inventory` - dashboard totals
- `GET /api/inventory?view=products` - product catalogue
- `GET /api/inventory?view=report&from=2026-01-01&to=2026-12-31` - report
- `POST /api/inventory` - purchase or shipping action
- `GET /api/health` - deployment health check

POST fields:

```text
action=purchase|shipping
productId=P1001
quantity=5
customer=Supplier or customer name
```

## Render deployment

The included `render.yaml` compiles directly with `javac` and starts the Java
HTTP server:

```text
Build: mkdir -p target/classes && javac -d target/classes src/main/java/com/inventory/InventoryServer.java
Start: java -cp target/classes com.inventory.InventoryServer
Health: /api/health
```

No environment variables are required other than Render's `PORT`, which is
read automatically by the server. The direct `javac` build also works when
Render creates the service as a Java or Node web service, as long as a JDK is
available.
