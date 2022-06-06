package one.plaza.nightwaveplaza.Api2;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;
import one.plaza.nightwaveplaza.Utils.Utils;

public class ApiCallback implements Callback {
    public void onSuccess(String response) {}
    public void onFailure(String message) {}
    public void onEnd() {}

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        Utils.debugLog(e.getMessage());
        onFailure("Network exception.");
        onEnd();
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        if (response.isSuccessful()) {
            ResponseBody body = response.body();
            if (body != null) {
                onSuccess(body.string());
            } else {
                onFailure("Response parse error.");
            }
        } else {
            String error = ApiError.parseError(response.body(), "error");
            onFailure(error);
        }
        onEnd();
    }
}