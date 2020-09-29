import controller.*;
import filter.CorsFilter;
import org.dalesbred.Database;
import org.dalesbred.result.EmptyResultException;
import org.eclipse.jetty.http.HttpStatus;
import org.h2.jdbcx.JdbcConnectionPool;
import org.json.JSONException;
import org.json.JSONObject;
import service.CookieTokenStore;
import service.DatabaseTokenStore;
import service.TokenStore;
import spark.Request;
import spark.Response;
import com.google.common.util.concurrent.*;
import spark.Spark;

import static spark.Spark.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        port(args.length > 0 ? Integer.parseInt(args[0])
                : spark.Service.SPARK_DEFAULT_PORT);
//        Spark.staticFiles.location("/public");
        // SSL
        secure("deploy/keystore.jks", "password", null, null);
        // Create a Connection pool
        var datasource = JdbcConnectionPool.create(
                "jdbc:h2:test", "natter_api_user", "password");
        // Use Dalesbred wrapper
        var database = Database.forDataSource(datasource);

        var rateLimiter = RateLimiter.create(2.0d);
        before(new CorsFilter(Set.of("https://localhost:9999")));
        createTables(database);

        var databaseTokenStore = new DatabaseTokenStore(database);

        // Init Controllers
        var spaceController = new SpaceController(database);
        var userController = new UserController(database);
        var auditController = new AuditController(database);
        var moderatorController = new ModeratorController(database);
        var tokenController = new TokenController(databaseTokenStore);

        before(userController::authenticate);
        before(tokenController::validateToken);

        before("/sessions", userController::requireAuthentication);
        post("/sessions", tokenController::login);
        delete("/sessions", tokenController::logout);

        before("/expired_tokens", userController::requireAuthentication);
        delete("/expired_tokens", (request, response) -> {
            databaseTokenStore.deleteExpiredTokens();
            return new JSONObject();
        });


        before(userController::authenticate);

        before(auditController::auditRequestStart);
        afterAfter(auditController::auditRequestEnd);

        before("/spaces", userController::requireAuthentication);
        post("/spaces", spaceController::createSpace);

        before("/spaces/:spaceId/messages",
                userController.requirePermission("POST", "w"));
        post("/spaces/:spaceId/messages", spaceController::postMessage);

        before("/spaces/:spaceId/messages/*",
                userController.requirePermission("GET", "r"));
        get("/spaces/:spaceId/messages/:msgId",
                spaceController::readMessage);

        before("/spaces/:spaceId/messages",
                userController.requirePermission("GET", "r"));
        get("/spaces/:spaceId/messages", spaceController::findMessages);

        before("/spaces/:spaceId/members",
                userController.requirePermission("POST", "rwd"));
        post("/spaces/:spaceId/members", spaceController::addMember);

        before("/spaces/:spaceId/messages/*",
                userController.requirePermission("DELETE", "d"));
        delete("/spaces/:spaceId/messages/:msgId",
                moderatorController::deletePost);

        post("/users", userController::registerUser);
        get("/logs", auditController::readAuditLog);

        // Hooks
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
        afterAfter((req, res) -> {
            res.type("application/json;charset=utf-8");
            res.header("X-Content-Type-Options", "nosniff");
            res.header("X-Frame-Options", "DENY");
            res.header("X-XSS-Protection", "0");
            res.header("Cache-Control", "no-store");
            res.header("Content-Security-Policy",
                    "default-src 'none'; frame-ancestors 'none'; sandbox");
            res.header("Server", "");
//            res.header("Strict-Transport-Security", "max-age=31536000");
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

