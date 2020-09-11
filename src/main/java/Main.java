import controller.SpaceController;
import org.dalesbred.Database;
import org.dalesbred.result.EmptyResultException;
import org.eclipse.jetty.http.HttpStatus;
import org.h2.jdbcx.JdbcConnectionPool;
import org.json.JSONException;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import com.google.common.util.concurrent.*;

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

        var rateLimiter = RateLimiter.create(2.0d);
        createTables(database);

        // Init Controllers
        var spaceController = new SpaceController(database);
        post("/spaces", spaceController::createSpace);

        before((req, res) -> {
            if (!rateLimiter.tryAcquire()) {
                res.header("Retry-After", "2");
                halt(HttpStatus.TOO_MANY_REQUESTS_429);
            }
        });

        before(((req, res) -> {
            if (req.requestMethod().equals("POST") &&
                    !"application/json".equals(req.contentType())) {
                halt(HttpStatus.UNSUPPORTED_MEDIA_TYPE_415, new JSONObject().put(
                        "error", "Only application/json supported"
                ).toString());
            }
        }));
        after((req, res) -> {
            res.type("application/json");
        });
        afterAfter((request, response) -> {
            response.type("application/json;charset=utf-8");
            response.header("X-Content-Type-Options", "nosniff");
            response.header("X-Frame-Options", "DENY");
            response.header("X-XSS-Protection", "0");
            response.header("Cache-Control", "no-store");
            response.header("Content-Security-Policy",
                    "default-src 'none'; frame-ancestors 'none'; sandbox");
            response.header("Server", "");
                });

        internalServerError(new JSONObject().put("error", "internal server error").toString());
        notFound(new  JSONObject().put("error", "not found").toString());
        exception(IllegalArgumentException.class,
                Main::badRequest);
        exception(JSONException.class,
                Main::badRequest);
        exception(EmptyResultException.class,
                (e, request, response) -> response.status(HttpStatus.NOT_FOUND_404));
    }

    private static void createTables(Database database)
            throws Exception {
        var path = Paths.get(
                Main.class.getResource("/schema.sql").toURI());
        database.update(Files.readString(path));
    }

    private static void badRequest(Exception ex,
                                   Request req, Response res) {
        res.status(400);
        res.body("{\"error\": \"" + ex.getMessage() + "\"}");
    }
}

