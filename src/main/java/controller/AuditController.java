package controller;

import org.dalesbred.*;
import org.json.*;
import spark.*;

import java.sql.*;
import java.time.*;
import java.time.temporal.*;

public class AuditController {

    private final Database database;

    public AuditController(Database database) {
        this.database = database;
    }

    public void auditRequestStart(Request req, Response res) {
        database.withVoidTransaction(tx -> {
            var auditId = database.findUniqueLong(
                    "SELECT NEXT VALUE FOR audit_id_seq");
            req.attribute("audit_id", auditId);
            database.updateUnique(
                    "INSERT INTO audit_log(audit_id, method, path, " +
                            "user_id, audit_time) " +
                            "VALUES(?, ?, ?, ?, current_timestamp)",
                    auditId,
                    req.requestMethod(),
                    req.pathInfo(),
                    req.attribute("subject"));
        });
    }

    public void auditRequestEnd(Request req, Response res) {
        database.updateUnique(
                "INSERT INTO audit_log(audit_id, method, path, status, " +
                        "user_id, audit_time) " +
                        "VALUES(?, ?, ?, ?, ?, current_timestamp)",
                req.attribute("audit_id"),
                req.requestMethod(),
                req.pathInfo(),
                res.status(),
                req.attribute("subject"));
    }

    public JSONArray readAuditLog(Request req, Response res) {
        var since = Instant.now().minus(1, ChronoUnit.HOURS);
        var logs = database.findAll(AuditController::recordToJson,
                "SELECT * FROM audit_log " +
                        "WHERE audit_time >= ? LIMIT 20", since);
        return new JSONArray(logs);
    }

    private static JSONObject recordToJson(ResultSet row)
            throws SQLException {
        return new JSONObject()
                .put("id", row.getLong("audit_id"))
                .put("method", row.getString("method"))
                .put("path", row.getString("path"))
                .put("status", row.getInt("status"))
                .put("user", row.getString("user_id"))
                .put("time", row.getTimestamp("audit_time").toInstant());
    }
}