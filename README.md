# JettraStoreDriverJava

Official Java Driver for **JettraStoreEngine** with native support for **Java 25 Records**, Typed Repositories, Fluent Queries, and all 9 Multi-Model Database Engines.

## Installation (Maven)

```xml
<dependency>
    <groupId>com.jettra</groupId>
    <artifactId>JettraStoreDriverJava</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Quickstart with Java 25 Records

```java
import com.jettra.driver.java.JettraClient;
import com.jettra.driver.java.JettraRepository;
import java.util.Optional;

// 1. Define an immutable Java 25 Record
public record EmployeeRecord(String id, String fullName, String department, double salary, boolean active) {}

public class App {
    public static void main(String[] args) throws Exception {
        // 2. Connect and authenticate
        JettraClient client = new JettraClient("localhost", 8086);
        client.connect();
        client.login("admin", "admin");

        // 3. Approach A: Typed Record Repository Pattern
        JettraRepository<EmployeeRecord> repo = client.recordRepository(EmployeeRecord.class, "employees");
        
        EmployeeRecord emp = new EmployeeRecord("EMP-001", "Carlos Mendez", "Engineering", 95000.0, true);
        repo.save("EMP-001", emp);

        Optional<EmployeeRecord> retrieved = repo.findById("EMP-001");
        retrieved.ifPresent(r -> System.out.println("Loaded record: " + r.fullName() + " -> $" + r.salary()));

        // 4. Approach B: Fluent Records Query API
        client.records().collection("employees").insert("EMP-002", 
            "{\"_recordClass\":\"EmployeeRecord\",\"components\":{\"id\":\"EMP-002\",\"fullName\":\"Ana Gomez\",\"salary\":88000}}");

        String rawJson = client.records().collection("employees").get("EMP-002");
        System.out.println("Raw stored record: " + rawJson);

        // 5. Direct Helper Methods
        client.saveRecord("employees", "EMP-003", new EmployeeRecord("EMP-003", "David Kim", "DevOps", 91000.0, true));
        Optional<EmployeeRecord> david = client.getRecord("employees", "EMP-003", EmployeeRecord.class);

        // 6. Deletion
        repo.delete("EMP-001");
        client.deleteRecord("employees", "EMP-002");
        
        client.close();
    }
}
```

## Multi-Model Engines Overview

| Engine | Fluent Method | Example |
| :--- | :--- | :--- |
| **`RECORDS`** | `client.records()` / `client.recordRepository(...)` | `repo.save("id", new MyRecord(...))` |
| **`DOCUMENT`** | `client.document()` | `client.document().collection("c").insert("id", json)` |
| **`VECTOR`** | `client.vector()` | `client.vector().collection("c").insert("id", json)` |
| **`GRAPH`** | `client.graph()` | `client.graph().collection("c").insert("id", json)` |
| **`TIMESERIES`**| `client.timeseries()` | `client.timeseries().collection("c").insert("ts", json)` |
| **`COLUMN`** | `client.column()` | `client.column().collection("c").insert("id", json)` |
| **`KEYVALUE`** | `client.keyvalue()` | `client.keyvalue().collection("c").insert("k", val)` |
| **`GEOSPATIAL`**| `client.geospatial()` | `client.geospatial().collection("c").insert("id", json)` |
| **`OBJECT`** | `client.object()` | `client.object().collection("c").insert("id", json)` |
