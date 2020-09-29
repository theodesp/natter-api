package controller;

import com.lambdaworks.crypto.SCrypt;
import com.lambdaworks.crypto.SCryptUtil;
import org.dalesbred.Database;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.json.JSONObject;
import spark.Filter;
import spark.Request;
import spark.Response;
import static spark.Spark.*;

import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class UserController {
    private static final String USERNAME_PATTERN =
            "[a-zA-Z][a-zA-Z0-9]{1,29}";
    private static int PASSWORD_MIN_LENGTH = 8;

    private final Database database;

    public UserController(Database database) {
        this.database = database;
    }

    public JSONObject registerUser(Request req, Response res) throws Exception {
        var json = new JSONObject(req.body());
        var username = json.getString("username");
        var password = json.getString("password");

        if (!username.matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException("invalid username");
        }
        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("password too short");
        }

        String hash = SCryptUtil.scrypt(password, 32768, 8, 1);
        database.updateUnique(
                "INSERT INTO users(user_id, pw_hash)" +
                        " VALUES(?, ?)", username, hash);

        res.status(HttpStatus.CREATED_201);
        res.header("Location", "/users/" + username);
        return new JSONObject().put("username", username);
    }

    public void authenticate(Request req, Response res) {
        // Check Header
        var authHeader = req.headers(HttpHeader.AUTHORIZATION.asString());
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return;
        }

        // Extract credentials
        var offset = "Basic ".length();
        var credentials = new String(Base64.getDecoder().decode(
                authHeader.substring(offset)), StandardCharsets.UTF_8);

        var components = credentials.split(":", 2);

        // Validate credentials
        if (components.length != 2) {
            throw new IllegalArgumentException("invalid auth header");
        }

        var username = components[0];
        var password = components[1];

        if (!username.matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException("invalid username");
        }

        // Fetch stored password
        var hash = database.findOptional(String.class,
                "SELECT pw_hash FROM users WHERE user_id = ?", username);
        // Compare Passwords
        if (hash.isPresent() &&
                SCryptUtil.check(password, hash.get())) {
            req.attribute("subject", username);
        }
    }

    public void requireAuthentication(Request request, Response response) {
        if (request.attribute("subject") == null) {
            response.header("WWW-Authenticate", "Bearer");
            halt(401);
        }
    }

    public Filter requirePermission(String method, String permission) {
        return (req, res) -> {
            if (!method.equalsIgnoreCase(req.requestMethod())) {
                return;
            }

            requireAuthentication(req, res);

            var spaceId = Long.parseLong(req.params(":spaceId"));
            var username = (String) req.attribute("subject");

            var perms = database.findOptional(String.class,
                    "SELECT perms FROM permissions " +
                            "WHERE space_id = ? AND user_id = ?",
                    spaceId, username).orElse("");

            if (!perms.contains(permission)) {
                halt(HttpStatus.FORBIDDEN_403);
            }
        };
    }
}
