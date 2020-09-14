package controller;

import org.dalesbred.Database;
import org.json.JSONObject;

import spark.*;

public class ModeratorController {

    private final Database database;

    public ModeratorController(Database database) {
        this.database = database;
    }

    public JSONObject deletePost(Request req, Response res) {
        var spaceId = Long.parseLong(req.params(":spaceId"));
        var msgId = Long.parseLong(req.params(":msgId"));

        database.updateUnique("DELETE FROM messages " +
                "WHERE space_id = ? AND msg_id = ?", spaceId, msgId);
        res.status(200);
        return new JSONObject();
    }
}