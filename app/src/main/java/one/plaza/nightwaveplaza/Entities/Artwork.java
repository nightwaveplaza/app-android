package one.plaza.nightwaveplaza.Entities;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import one.plaza.nightwaveplaza.Updater.ArtworkResponse;
import one.plaza.nightwaveplaza.Updater.ArtworkResponseBitmap;
import one.plaza.nightwaveplaza.Utils.Storage;
import one.plaza.nightwaveplaza.Utils.Utils;

public class Artwork {
    private static final String deadArtworkFile = "/assets/app/img/dead.jpg";

    private static void setArtworkPath(String path, Context ctx) {
        Storage.set("artworkPath", path, ctx);
    }

    public static String getArtworkPath(Context ctx) {
        return Storage.get("artworkPath", "", ctx);
    }

    public static String extractArtworkName(String src) {
        return src.substring(src.lastIndexOf("artwork_"));
    }

    public static boolean isArtworkDead(Context ctx) {
        String path = getArtworkPath(ctx);
        return path.isEmpty() || path.contains("dead.jpg");
    }

    public static void fetch(String src, Context ctx, ArtworkResponse callback) {
        Glide.with(ctx).asBitmap().diskCacheStrategy(DiskCacheStrategy.NONE).load(src).into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                try {
                    setArtworkPath(cacheArtwork(resource, src, ctx), ctx);
                    callback.onLoad();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                super.onLoadFailed(errorDrawable);
                setArtworkPath(deadArtworkFile, ctx);
                callback.onLoad();
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {}
        });
    }

    private static String cacheArtwork(Bitmap bmp, String src, Context ctx) throws IOException {
        File cacheDir = new File(ctx.getCacheDir() + "/artworks");
        if (!cacheDir.isDirectory()) {
            if (!cacheDir.mkdirs()) {
                return deadArtworkFile;
            }
        }

        String artworkName = extractArtworkName(src);
        File artworkFile = new File(cacheDir, artworkName);
        FileOutputStream out = new FileOutputStream(artworkFile);
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out);
        out.flush();
        out.close();

        cleanupArtworks(ctx);
        return "/artworks/" + artworkName;
    }

    public static void getBitmap(Context ctx, ArtworkResponseBitmap response) {
        String artworkPath = ctx.getCacheDir() + getArtworkPath(ctx);
        String uri = "";
        Utils.debugLog(artworkPath);

        boolean isCached = (new File(artworkPath)).exists();
        if (isCached && !isArtworkDead(ctx)) {
            uri = artworkPath;
        }

        Glide.with(ctx).asBitmap().diskCacheStrategy(DiskCacheStrategy.NONE).load(uri).into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                response.onLoad(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                super.onLoadFailed(errorDrawable);
                response.onLoad(null);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {}
        });
    }

    private static void cleanupArtworks(Context ctx) {
        String cacheDir = ctx.getCacheDir() + "/artworks";
        File[] files = (new File(cacheDir)).listFiles();

        if (files != null) {
            for (File file : files) {
                String filename = file.getName();
                Date fileDate = new Date(file.lastModified());
                boolean outdated = System.currentTimeMillis() - fileDate.getTime() > 48 * 60 * 60 * 1000;

                if (filename.contains("artwork_") && outdated) {
                    if (file.delete()) {
                        Utils.debugLog("DELETED: " + filename);
                    }
                }
            }
        }
    }
}
