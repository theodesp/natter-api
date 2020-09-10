import controller.SpaceController;
import org.dalesbred.Database;
import org.h2.jdbcx.JdbcConnectionPool;
import org.json.JSONObject;

import static spark.Spark.*;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        // Create a Connection pool
        var datasource = JdbcConnectionPool.create(
                "jdbc:h2:test", "natter_api_user", "password");
        // Use Dalesbred wrapper
        var database = Database.forDataSource(datasource);
        createTables(database);

        // Init Controllers
        var spaceController = new SpaceController(database);
        post("/spaces", spaceController::createSpace);

        after((req, res) -> {
            res.type("application/json");
        });

        internalServerError(new JSONObject().put("error", "internal server error").toString());
        notFound(new  JSONObject().put("error", "not found").toString());
    }

    private static void createTables(Database database)
            throws Exception {
        var path = Paths.get(
                Main.class.getResource("/schema.sql").toURI());
        database.update(Files.readString(path));
    }
}