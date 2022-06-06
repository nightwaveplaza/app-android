package one.plaza.nightwaveplaza;

public class Events {
    public static class onReaction { }
    public static class onArtwork { }
    public static class onPlayback { }

    public static class onToast {
        public final String message;
        public onToast(String message) {
            this.message = message;
        }
    }
}
