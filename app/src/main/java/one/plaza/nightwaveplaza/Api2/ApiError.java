package one.plaza.nightwaveplaza.Api2;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.ResponseBody;

public class ApiError {
    public static String parseError(ResponseBody errorBody, String field) {
        String err = "Unknown error.";

        if (errorBody == null) {
            return err;
        }

        try {
            JSONObject jObject = new JSONObject(errorBody.string());
            err = jObject.getString(field);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }

        return err;
    }
}
