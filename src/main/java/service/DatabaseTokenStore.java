package service;

import org.dalesbred.Database;
import org.json.JSONObject;
import spark.Request;

import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DatabaseTokenStore implements TokenStore {
    private final Database database;
    private final SecureRandom secureRandom;

    public DatabaseTokenStore(Database database) {
        this.database = database;
        this.secureRandom = new SecureRandom();

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::deleteExpiredTokens,
                        10, 10, TimeUnit.MINUTES);
    }

    private String randomId() {
        var bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return Base64url.encode(bytes);
    }

    @Override
    public String create(Request request, Token token) {
        var tokenId = randomId();
        var attrs = new JSONObject(token.attributes).toString();

        database.updateUnique("INSERT INTO " +
                        "tokens(token_id, user_id, expiry, attributes) " +
                        "VALUES(?, ?, ?, ?)", hash(tokenId), token.username,
                token.expiry, attrs);

        return tokenId;
    }

    @Override
    public Optional<Token> read(Request request, String tokenId) {
        return database.findOptional(this::readToken,
                "SELECT user_id, expiry, attributes " +
                        "FROM tokens WHERE token_id = ?", hash(tokenId));
    }

    @Override
    public void revoke(Request request, String tokenId) {
        database.update("DELETE FROM tokens WHERE token_id = ?",
                hash(tokenId));
    }

    private String hash(String tokenId) {
        var hash = CookieTokenStore.sha256(tokenId);
        return Base64url.encode(hash);
    }

    public void deleteExpiredTokens() {
        database.update(
                "DELETE FROM tokens WHERE expiry < current_timestamp");
    }
}