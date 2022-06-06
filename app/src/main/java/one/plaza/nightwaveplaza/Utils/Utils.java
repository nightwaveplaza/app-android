package one.plaza.nightwaveplaza.Utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Random;

import one.plaza.nightwaveplaza.BuildConfig;

public class Utils {
    private static final String ENC_SEC = "pkJSxL39RhhJAmPY";

    /**
     * Output debug log with Plaza prefix
     *
     * @param message //
     */
    public static void debugLog(String message) {
        if (BuildConfig.DEBUG) {
            Log.d("PPlaza", message);
        }
    }

    public static void debugLog(String message, Object obj) {
        if (BuildConfig.DEBUG) {
            String name = obj.getClass().getName().replace("one.plaza.nightwaveplaza.", "");
            Log.d("AAA Plaza: " + name, message);
        }
    }

    public static String getUserAgent() {
        return String.format(Locale.US,
                "%s/%s (Android: %s; %s %s; %s)",
                "NightwavePlaza",
                BuildConfig.VERSION_NAME,
                Build.MODEL,
                Build.BRAND,
                Build.DEVICE,
                Locale.getDefault().getLanguage());
    }

    public static String randomString(int len) {
        final String DATA = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random RANDOM = new Random();

        StringBuilder sb = new StringBuilder(len);

        for (int i = 0; i < len; i++) {
            sb.append(DATA.charAt(RANDOM.nextInt(DATA.length())));
        }

        return sb.toString();
    }

    public static String xorEncrypt(String input) {
        byte[] inputBytes = input.getBytes();
        int inputSize = inputBytes.length;

        byte[] keyBytes = ENC_SEC.getBytes();
        int keySize = keyBytes.length - 1;

        byte[] outBytes = new byte[inputSize];
        for (int i = 0; i < inputSize; i++) {
            outBytes[i] = (byte) (inputBytes[i] ^ keyBytes[i % keySize]);
        }

        return new String(Base64.encode(outBytes, Base64.DEFAULT));
    }

    public static String xorDecrypt(String input) {
        byte[] inputBytes = Base64.decode(input, Base64.DEFAULT);
        int inputSize = inputBytes.length;

        byte[] keyBytes = ENC_SEC.getBytes();
        int keySize = keyBytes.length - 1;

        byte[] outBytes = new byte[inputSize];
        for (int i = 0; i < inputSize; i++) {
            outBytes[i] = (byte) (inputBytes[i] ^ keyBytes[i % keySize]);
        }

        return new String(outBytes);
    }


    public static String makeDisplayTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return "";
        }
        final char lastChar = title.charAt(title.length() - 1);
        if (lastChar != ')') {
            return title;
        }
        final int i = indexOfAppName(title);
        if (i > 0) {
            return title.substring(0, i).trim();
        }
        return title;
    }

    private static int indexOfAppName(String title) {
        int depth = 0;
        for (int i = title.length() - 1; i >= 0; i--) {
            final char c = title.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                depth--;
            }
            if (depth == 0) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isHuawei() {
        return (
                android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1 ||
                        android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP
        ) && Build.MANUFACTURER.toLowerCase(Locale.getDefault()).contains("huawei");
    }

    public static String md5(String s) {
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();

            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) hexString.append(Integer.toHexString(0xFF & b));
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String md52(String input) throws NoSuchAlgorithmException {
        String result = input;
        if(input != null) {
            MessageDigest md = MessageDigest.getInstance("MD5"); //or "SHA-1"
            md.update(input.getBytes());
            BigInteger hash = new BigInteger(1, md.digest());
            result = hash.toString(16);
            while(result.length() < 32) { //40 for SHA-1
                result = "0" + result;
            }
        }
        return result;
    }

    public static Bitmap getSmallerBitmap(Bitmap bm) {
        if (bm == null) {
            return null;
        }

        int width = bm.getWidth();
        int height = bm.getHeight();

        float newWidth = 150;
        float newHeight = (newWidth * height / width);

        Matrix matrix = new Matrix();
        matrix.postScale(newWidth / width, newHeight / height);

        return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
    }
}
