package one.plaza.nightwaveplaza.Api2.Models;

import com.google.gson.annotations.SerializedName;

public class Song {
    public String id;
    public String artist;
    public String title;
    public String album;
    public int position;
    public int length;
    @SerializedName("artwork_src") public String artworkSrc;
    public int reactions;
}
