package one.plaza.nightwaveplaza.Api2.Models;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import one.plaza.nightwaveplaza.Entities.Artwork;
import one.plaza.nightwaveplaza.Utils.Storage;
import one.plaza.nightwaveplaza.Utils.Utils;

public class Status {
    public Song song;
    public int listeners;

    public static Song getSong(Context ctx) {
        String json = Storage.get("status_song", "", ctx);
        return json.isEmpty() ? new Song() : (new Gson()).fromJson(json, Song.class);
    }

    public static void setSong(Song song, Context ctx) {
        String json = (new Gson()).toJson(song);
        Storage.set("status_song", json, ctx);
    }

    public static int getListeners(Context ctx) {
        return Storage.get("status_listeners", 0, ctx);
    }

    public static void setListeners(int listeners, Context ctx) {
        Storage.set("status_listeners", listeners, ctx);
    }

    public static boolean isEqual(Status status, Context ctx) {
        Song song = getSong(ctx);
        return status.song.id.equals(song.id);
    }

    public static void stop(Context ctx) {
        setIsPlaying(false, ctx);
        setRecievedAt(0, ctx);
        setSleepTime(0, ctx);
    }

    public static void setIsPlaying(boolean isPlaying, Context ctx) {
        Storage.set("status_playing", isPlaying, ctx);
    }

    public static boolean getIsPlaying(Context ctx) {
        return Storage.get("status_playing", false, ctx);
    }

    public static void setSleepTime(long time, Context ctx) {
        Storage.set("status_sleeptime", time, ctx);
    }

    public static long getSleepTime(Context ctx) {
        return Storage.get("status_sleeptime", (long) 0, ctx);
    }

    public static void setRecievedAt(long time, Context ctx) {
        Storage.set("status_receivedat", time, ctx);
    }

    public static long getRecievedAt(Context ctx) {
        return Storage.get("status_receivedat", (long) 0, ctx);
    }

    public static boolean getIsSleepTimerPass(Context ctx) {
        long sleepTime = getSleepTime(ctx);
        return sleepTime > 0 && (System.currentTimeMillis() - sleepTime > 0);
    }

    public static int getCurrentPosition(Context ctx) {
        Song song = getSong(ctx);
        int sinceUpdate = (int) ((System.currentTimeMillis() - getRecievedAt(ctx)) / 1000);
        int length = song.length;
        return sinceUpdate > length ? length : sinceUpdate + song.position;
    }

    public static boolean getIsOutdated(Context ctx) {
        Song song = getSong(ctx);
        return getRecievedAt(ctx) == 0 || (getCurrentPosition(ctx) + 7 > song.length);
    }

    public static MediaMetadataCompat getAsMetadata(Context ctx, Bitmap bitmap) {
        Song song = getSong(ctx);
        return new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                .build();
    }

    public static MediaDescriptionCompat getAsMediaDescriptor(Context ctx, Bitmap bitmap) {
        Song song = getSong(ctx);
        Bundle extras = new Bundle();

        extras.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist);
        extras.putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title);
        extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album);
        extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.artist);
        extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L);
        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);

        return new MediaDescriptionCompat.Builder()
//                .setTitle("1111111")
//                .setSubtitle("2222222")
//                .setDescription("ADSS")
                .setExtras(extras).build();
    }

    public static String getAsJson(Context ctx) {
        Song song = getSong(ctx);

        JSONObject json = new JSONObject();
        try {
            json.put("isPlaying", getIsPlaying(ctx));
            json.put("id", song.id);
            json.put("artist", song.artist);
            json.put("title", song.title);
            json.put("length", song.length);
            json.put("artwork_src", Artwork.getArtworkPath(ctx));
            json.put("sleepTime", getSleepTime(ctx));
            json.put("position", getCurrentPosition(ctx));
            json.put("likes", song.reactions);
            json.put("listeners", getListeners(ctx));
            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }
}
