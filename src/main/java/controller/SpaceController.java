package controller;

import org.dalesbred.Database;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.json.*;
import spark.*;

public class SpaceController {
    private final Database database;

    public SpaceController(Database database) {
        this.database = database;
    }

    public JSONObject createSpace(Request req, Response res) {
        var json = new JSONObject(req.body());
        var spaceName = json.getString("name");
        var owner = json.getString("owner");

        return database.withTransaction(tx -> {
            // Get next id
            var spaceId = database.findUniqueLong(
                    "SELECT NEXT VALUE FOR space_id_seq;"
            );
            // Create new space
            database.updateUnique(
                    "INSERT INTO spaces(space_id, name, owner) " +
                            "VALUES(" + spaceId + ", '" + spaceName +
                            "', '" + owner + "');");
            res.status(HttpStatus.CREATED_201);
            res.header(HttpHeader.LOCATION.asString(), "/spaces/" + spaceId);
            return new JSONObject()
                    .put("name", spaceName)
                    .put("uri", "/spaces/" + spaceId);
        });
    }
}
