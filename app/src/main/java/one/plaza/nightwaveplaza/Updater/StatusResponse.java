package one.plaza.nightwaveplaza.Updater;

public interface StatusResponse {
    void onUpdated();
    void onChanged();
    void onFailed();
    void onArtwork();
//    void onArtworkFail();
}
