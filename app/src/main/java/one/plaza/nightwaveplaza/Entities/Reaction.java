package one.plaza.nightwaveplaza.Entities;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import one.plaza.nightwaveplaza.Api2.Models.Song;
import one.plaza.nightwaveplaza.Api2.Models.Status;
import one.plaza.nightwaveplaza.Utils.Storage;

public class Reaction {
    private static final String PREFIX = "like_";

    public static String getSongId(Context ctx) {
        return Storage.get(PREFIX + "songId", "", ctx);
    }

    public static void setSongId(String songId, Context ctx) {
        Storage.set(PREFIX + "songId", songId, ctx);
    }

    public static int getScore(Context ctx) {
        Song song = Status.getSong(ctx);
        if (song != null && !(getSongId(ctx).equals(song.id))) {
            return 0;
        }

        return Storage.get(PREFIX + "score", 0, ctx);
    }

    public static void setScore(int score, Context ctx) {
        Storage.set(PREFIX + "score", score, ctx);
    }

    public static String getAsJson(Context ctx) {
        JSONObject json = new JSONObject();
        try {
            json.put("songId", getSongId(ctx));
            json.put("score", getScore(ctx));
            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{'songId': '', 'score': 0}";
        }
    }

    public static void set(int score, Context ctx) {
        if (score != 0) {
            Song song = Status.getSong(ctx);
            setSongId(song.id, ctx);
        } else {
            setSongId("", ctx);
        }
        setScore(score, ctx);
    }

    public static void clear(Context ctx) {
        setSongId("", ctx);
        setScore(0, ctx);
    }

    public static int matchNewScore(int score, Context ctx) {
        return score == getScore(ctx) ? 0 : score;
    }
}
