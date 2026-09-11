package com.fnphoto.tv.api;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class ApiInterceptorTest {
    private static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType IMAGE = MediaType.parse("image/jpeg");
    private static final String FILTERED_SEARCH_BODY =
            "{\"keyword\":\"\",\"filters\":[{\"filterName\":\"file_type\",\"filterValue\":\"video\"}],\"antiFilters\":[]}";

    @Test
    public void unrecoverable401_redirectsToLoginAndReturnsOpenResponse() throws IOException {
        RecordingSessionRefresher refresher = new RecordingSessionRefresher(false, "");
        RecordingRedirector redirector = new RecordingRedirector();
        ApiInterceptor interceptor = new ApiInterceptor(refresher, redirector);
        MockChain chain = new MockChain(buildRequest(), 401);

        Response response = interceptor.intercept(chain);

        assertEquals(401, response.code());
        assertEquals("body-401", response.body().string());
        assertEquals(1, refresher.calls);
        assertEquals(1, redirector.calls);
        assertEquals(1, chain.proceededRequests.size());
    }

    @Test
    public void recoverable401_retriesWithFreshTokenAndSkipsRedirect() throws IOException {
        RecordingSessionRefresher refresher = new RecordingSessionRefresher(true, "fresh-token");
        RecordingRedirector redirector = new RecordingRedirector();
        ApiInterceptor interceptor = new ApiInterceptor(refresher, redirector);
        MockChain chain = new MockChain(buildRequest(), 401, 200);

        Response response = interceptor.intercept(chain);

        assertEquals(200, response.code());
        assertEquals("body-200", response.body().string());
        assertEquals(1, refresher.calls);
        assertEquals(0, redirector.calls);
        assertEquals(2, chain.proceededRequests.size());
        assertEquals("fresh-token", chain.proceededRequests.get(1).header("accesstoken"));
    }

    @Test
    public void retryStill401_redirectsToLogin() throws IOException {
        RecordingSessionRefresher refresher = new RecordingSessionRefresher(true, "fresh-token");
        RecordingRedirector redirector = new RecordingRedirector();
        ApiInterceptor interceptor = new ApiInterceptor(refresher, redirector);
        MockChain chain = new MockChain(buildRequest(), 401, 401);

        Response response = interceptor.intercept(chain);

        assertEquals(401, response.code());
        assertEquals("body-401", response.body().string());
        assertEquals(1, refresher.calls);
        assertEquals(1, redirector.calls);
        assertEquals(2, chain.proceededRequests.size());
    }

    @Test
    public void bodyCode401_redirectsToLoginAndReturnsOpenResponse() throws IOException {
        RecordingSessionRefresher refresher = new RecordingSessionRefresher(false, "");
        RecordingRedirector redirector = new RecordingRedirector();
        ApiInterceptor interceptor = new ApiInterceptor(refresher, redirector);
        String authFailureBody = "{\"code\":401,\"msg\":\"Authentication failed\"}";
        MockChain chain = new MockChain(buildRequest(), response(200, authFailureBody, JSON));

        Response response = interceptor.intercept(chain);

        assertEquals(200, response.code());
        assertEquals(authFailureBody, response.body().string());
        assertEquals(1, refresher.calls);
        assertEquals(1, redirector.calls);
        assertEquals(1, chain.proceededRequests.size());
    }

    @Test
    public void bodyCode401_retriesWithFreshTokenAndSkipsRedirect() throws IOException {
        RecordingSessionRefresher refresher = new RecordingSessionRefresher(true, "fresh-token");
        RecordingRedirector redirector = new RecordingRedirector();
        ApiInterceptor interceptor = new ApiInterceptor(refresher, redirector);
        MockChain chain = new MockChain(
                buildRequest(),
                response(200, "{\"code\":401,\"msg\":\"Authentication failed\"}", JSON),
                response(200, "{\"code\":0,\"data\":{}}", JSON)
        );

        Response response = interceptor.intercept(chain);

        assertEquals(200, response.code());
        assertEquals("{\"code\":0,\"data\":{}}", response.body().string());
        assertEquals(1, refresher.calls);
        assertEquals(0, redirector.calls);
        assertEquals(2, chain.proceededRequests.size());
        assertEquals("fresh-token", chain.proceededRequests.get(1).header("accesstoken"));
    }

    @Test
    public void retryBodyStillCode401_redirectsToLogin() throws IOException {
        RecordingSessionRefresher refresher = new RecordingSessionRefresher(true, "fresh-token");
        RecordingRedirector redirector = new RecordingRedirector();
        ApiInterceptor interceptor = new ApiInterceptor(refresher, redirector);
        MockChain chain = new MockChain(
                buildRequest(),
                response(200, "{\"code\":401,\"msg\":\"Authentication failed\"}", JSON),
                response(200, "{\"code\":401,\"msg\":\"Authentication failed\"}", JSON)
        );

        Response response = interceptor.intercept(chain);

        assertEquals(200, response.code());
        assertEquals("{\"code\":401,\"msg\":\"Authentication failed\"}", response.body().string());
        assertEquals(1, refresher.calls);
        assertEquals(1, redirector.calls);
        assertEquals(2, chain.proceededRequests.size());
    }

    @Test
    public void nonJsonBodyCode401_doesNotRefreshOrRedirect() throws IOException {
        RecordingSessionRefresher refresher = new RecordingSessionRefresher(true, "fresh-token");
        RecordingRedirector redirector = new RecordingRedirector();
        ApiInterceptor interceptor = new ApiInterceptor(refresher, redirector);
        String imageLikeBody = "{\"code\":401,\"msg\":\"image bytes are not api json\"}";
        MockChain chain = new MockChain(buildRequest(), response(200, imageLikeBody, IMAGE));

        Response response = interceptor.intercept(chain);

        assertEquals(200, response.code());
        assertEquals(imageLikeBody, response.body().string());
        assertEquals(0, refresher.calls);
        assertEquals(0, redirector.calls);
        assertEquals(1, chain.proceededRequests.size());
    }

    @Test
    public void filteredSearch401_signsAndResendsTheExactRepeatableBodyBytes() throws IOException {
        RecordingSessionRefresher refresher = new RecordingSessionRefresher(true, "fresh-token");
        RecordingRedirector redirector = new RecordingRedirector();
        RecordingRequestSigner signer = new RecordingRequestSigner();
        ApiInterceptor interceptor = new ApiInterceptor(refresher, redirector, signer);
        Request request = new Request.Builder()
                .url("https://nas.example.test/p/api/v2/search/results")
                .header("accesstoken", "expired-token")
                .header("authx", "old-authx")
                .post(RequestBody.create(JSON, FILTERED_SEARCH_BODY))
                .build();
        MockChain chain = new MockChain(request, 401, 200);

        Response response = interceptor.intercept(chain);

        assertEquals(200, response.code());
        assertEquals("/p/api/v2/search/results", signer.path);
        assertEquals("POST", signer.method);
        assertEquals(FILTERED_SEARCH_BODY, signer.data);
        Request retry = chain.proceededRequests.get(1);
        assertEquals("signed-retry", retry.header("authx"));
        okio.Buffer retryBody = new okio.Buffer();
        retry.body().writeTo(retryBody);
        assertEquals(FILTERED_SEARCH_BODY, retryBody.readUtf8());
    }

    @Test
    public void get401_keepsEncodedQuerySigningBehavior() throws IOException {
        RecordingSessionRefresher refresher = new RecordingSessionRefresher(true, "fresh-token");
        RecordingRedirector redirector = new RecordingRedirector();
        RecordingRequestSigner signer = new RecordingRequestSigner();
        ApiInterceptor interceptor = new ApiInterceptor(refresher, redirector, signer);
        Request request = new Request.Builder()
                .url("https://nas.example.test/p/api/v1/gallery/recent?offset=0&limit=72")
                .header("accesstoken", "expired-token")
                .header("authx", "old-authx")
                .build();

        interceptor.intercept(new MockChain(request, 401, 200));

        assertEquals("/p/api/v1/gallery/recent", signer.path);
        assertEquals("GET", signer.method);
        assertEquals("offset=0&limit=72", signer.data);
    }

    @Test
    public void oneShotBody401_isNotConsumedOrRetried() throws IOException {
        RecordingSessionRefresher refresher = new RecordingSessionRefresher(true, "fresh-token");
        RecordingRedirector redirector = new RecordingRedirector();
        RecordingRequestSigner signer = new RecordingRequestSigner();
        ApiInterceptor interceptor = new ApiInterceptor(refresher, redirector, signer);
        RecordingOneShotBody body = new RecordingOneShotBody();
        Request request = new Request.Builder()
                .url("https://nas.example.test/p/api/v2/search/results")
                .header("accesstoken", "expired-token")
                .header("authx", "old-authx")
                .post(body)
                .build();
        MockChain chain = new MockChain(request, 401);

        Response response = interceptor.intercept(chain);

        assertEquals(401, response.code());
        assertEquals(1, chain.proceededRequests.size());
        assertEquals(0, refresher.calls);
        assertEquals(1, redirector.calls);
        assertFalse(body.written);
        assertNull(signer.data);
    }

    @Test
    public void authenticationFailureLogEndpoint_excludesHostAndQuery() {
        HttpUrl url = HttpUrl.get(
                "https://private-nas.example.test/p/api/v1/gallery/recent?private=value"
        );

        String endpoint = ApiInterceptor.sanitizedEndpointForLog(url);

        assertEquals("/p/api/v1/gallery/recent", endpoint);
        assertFalse(endpoint.contains("private-nas"));
        assertFalse(endpoint.contains("private=value"));
    }

    private static Request buildRequest() {
        return new Request.Builder()
                .url("https://nas.example.test/p/api/v1/gallery/timeline")
                .header("accesstoken", "expired-token")
                .header("authx", "old-authx")
                .build();
    }

    private static ResponseSpec response(int code, String body) {
        return response(code, body, TEXT);
    }

    private static ResponseSpec response(int code, String body, MediaType mediaType) {
        return new ResponseSpec(code, body, mediaType);
    }

    private static final class RecordingSessionRefresher implements ApiInterceptor.SessionRefresher {
        private final boolean success;
        private final String token;
        private int calls;

        RecordingSessionRefresher(boolean success, String token) {
            this.success = success;
            this.token = token;
        }

        @Override
        public boolean reLoginSync() {
            calls++;
            return success;
        }

        @Override
        public String currentToken() {
            return token;
        }
    }

    private static final class RecordingRedirector implements ApiInterceptor.AuthFailureRedirector {
        private int calls;

        @Override
        public void redirectToLogin() {
            calls++;
        }
    }

    private static final class RecordingRequestSigner implements ApiInterceptor.RequestSigner {
        private String path;
        private String method;
        private String data;

        @Override
        public String sign(String path, String method, String data) {
            this.path = path;
            this.method = method;
            this.data = data;
            return "signed-retry";
        }
    }

    private static final class RecordingOneShotBody extends RequestBody {
        private boolean written;

        @Override
        public MediaType contentType() {
            return JSON;
        }

        @Override
        public boolean isOneShot() {
            return true;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            written = true;
            sink.writeUtf8(FILTERED_SEARCH_BODY);
        }
    }

    private static final class MockChain implements Interceptor.Chain {
        private final Request originalRequest;
        private final ResponseSpec[] responses;
        private final List<Request> proceededRequests = new ArrayList<>();

        MockChain(Request originalRequest, int... responseCodes) {
            this.originalRequest = originalRequest;
            this.responses = new ResponseSpec[responseCodes.length];
            for (int i = 0; i < responseCodes.length; i++) {
                this.responses[i] = response(responseCodes[i], "body-" + responseCodes[i]);
            }
        }

        MockChain(Request originalRequest, ResponseSpec... responses) {
            this.originalRequest = originalRequest;
            this.responses = responses;
        }

        @Override
        public Request request() {
            return originalRequest;
        }

        @Override
        public Response proceed(Request request) {
            proceededRequests.add(request);
            int index = Math.min(proceededRequests.size() - 1, responses.length - 1);
            ResponseSpec response = responses[index];
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(response.code)
                    .message(response.code == 200 ? "OK" : "Unauthorized")
                    .body(ResponseBody.create(response.mediaType, response.body))
                    .build();
        }

        @Override
        public Connection connection() {
            return null;
        }

        @Override
        public Call call() {
            return null;
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            return this;
        }
    }

    private static final class ResponseSpec {
        private final int code;
        private final String body;
        private final MediaType mediaType;

        private ResponseSpec(int code, String body, MediaType mediaType) {
            this.code = code;
            this.body = body;
            this.mediaType = mediaType;
        }
    }
}
