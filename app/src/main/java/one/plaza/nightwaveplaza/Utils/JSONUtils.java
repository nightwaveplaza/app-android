package one.plaza.nightwaveplaza.Utils;

import org.json.JSONException;
import org.json.JSONObject;

public class JSONUtils
{
    public static String windowName(String name) {
        JSONObject jo = new JSONObject();
        try {
            jo.put("window", name);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
        return jo.toString();
    }
}
