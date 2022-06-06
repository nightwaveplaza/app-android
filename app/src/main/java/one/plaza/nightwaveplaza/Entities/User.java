package one.plaza.nightwaveplaza.Entities;

import android.content.Context;

import one.plaza.nightwaveplaza.Utils.PrefKeys;
import one.plaza.nightwaveplaza.Utils.Storage;

public class User {
    public static void setToken(String token, Context context) {
        Storage.setCrypto(PrefKeys.USER_TOKEN, token, context);
    }

    public static String getToken(Context context) {
        return Storage.getCrypto(PrefKeys.USER_TOKEN, "", context);
    }

    public static boolean isLogged(Context ctx) {
        String token = getToken(ctx);
        return !token.isEmpty();
    }
}
