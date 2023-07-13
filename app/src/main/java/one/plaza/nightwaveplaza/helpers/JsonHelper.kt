package one.plaza.nightwaveplaza.helpers

import org.json.JSONException
import org.json.JSONObject


object JsonHelper {
    fun windowName(name: String?): String {
        val jo = JSONObject()
        try {
            jo.put("window", name)
        } catch (e: JSONException) {
            e.printStackTrace()
            return "{}"
        }
        return jo.toString()
    }
}
