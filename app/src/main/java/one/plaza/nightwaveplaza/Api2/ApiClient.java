package one.plaza.nightwaveplaza.Api2;

import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import one.plaza.nightwaveplaza.Utils.Utils;

public class ApiClient {
    private static final String BASE_URL = "https://api.plaza.one/";

    private final OkHttpClient client;

    public ApiClient() {
        this.client = createClient();
    }

    private OkHttpClient createClient() {
        OkHttpClient.Builder client;
        client = new OkHttpClient.Builder();

        Interceptor headerAuthorizationInterceptor = chain -> {
            okhttp3.Request request = chain.request();
            Headers headers = request.headers().newBuilder().add(
                    "User-Agent", Utils.getUserAgent()).build();
            request = request.newBuilder().headers(headers).build();
            return chain.proceed(request);
        };

        client.addInterceptor(headerAuthorizationInterceptor);
        return client.build();
    }

    public void getStatus(ApiCallback callback) {
        Request request = new Request.Builder().url(BASE_URL + "/status").build();
        this.client.newCall(request).enqueue(callback);
    }

    public void sendReaction(int reaction, String token, ApiCallback callback) {
        FormBody.Builder body = new FormBody.Builder();
        body.add("reaction", String.valueOf(reaction));
        Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + token)
                .url(BASE_URL + "/reactions")
                .post(body.build())
                .build();
        this.client.newCall(request).enqueue(callback);
    }
}
