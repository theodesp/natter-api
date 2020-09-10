import org.dalesbred.Database;
import org.h2.jdbcx.JdbcConnectionPool;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        // Create a Connection pool
        var datasource = JdbcConnectionPool.create(
                "jdbc:h2:mem:natter", "natter", "password");
        // Use Dalesbred wrapper
        var database = Database.forDataSource(datasource);
        createTables(database);
    }

    private static void createTables(Database database)
            throws Exception {
        var path = Paths.get(
                Main.class.getResource("/schema.sql").toURI());
        database.update(Files.readString(path));
    }
}