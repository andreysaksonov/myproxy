package me.saksonov.myproxy;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import me.saksonov.myproxy.support.ForwardedHeader;

import java.util.Iterator;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public class MyProxy extends AbstractVerticle {

    @Override
    public void start(Future<Void> future) {
        Config config = new Config();

        HttpClient httpClient = vertx.createHttpClient();
        HttpServer httpServer = vertx.createHttpServer();

        Iterator<String> hosts = Iterables.cycle(config.getHttpClientHosts()).iterator();

        httpServer.requestHandler(serverRequest -> {
            HttpMethod method = serverRequest.method();
            String host = hosts.next();
            String uri = serverRequest.uri();

            HttpClientRequest clientRequest = httpClient.request(method, host, uri, clientResponse -> {
                HttpServerResponse serverResponse = serverRequest.response();
                serverResponse.setChunked(true);
                serverResponse.setStatusCode(clientResponse.statusCode());
                serverResponse.headers().setAll(clientResponse.headers());
                serverResponse.headers().set(Headers.HOST, serverRequest.getHeader(Headers.HOST));

                clientResponse.handler(serverResponse::write);
                clientResponse.endHandler($ -> serverResponse.end());
            });
            clientRequest.setChunked(true);
            clientRequest.headers().setAll(serverRequest.headers());
            clientRequest.headers().set(Headers.HOST, host);
            // RFC 7239 "Forwarded HTTP Extension"
            clientRequest.headers().set(Headers.FORWARDED, ForwardedHeader.format(serverRequest.localAddress().host(),
                    serverRequest.remoteAddress().host(),
                    serverRequest.getHeader(Headers.HOST),
                    HTTP_PROTO));

            serverRequest.handler(clientRequest::write);
            serverRequest.endHandler($ -> clientRequest.end());
        }).listen(config.getHttpServerPort(), result -> {
            if (result.succeeded()) {
                future.complete();
            } else {
                future.fail(result.cause());
            }
        });
    }

    private interface Headers {
        String HOST = "Host";
        String FORWARDED = "Forwarded";
    }

    private class Config {
        private final Integer httpServerPort;
        private final List<String> httpClientHosts;

        public Config() {
            JsonObject config = config();

            httpServerPort = config.getInteger("http.server.port", DEFAULT_HTTP_SERVER_PORT);
            checkArgument(httpServerPort != null, "http.server.port must be not empty");

            httpClientHosts = Splitter.on(',')
                    .omitEmptyStrings()
                    .trimResults()
                    .splitToList(config.getString("http.client.hosts", DEFAULT_HTTP_CLIENT_HOSTS));
            checkArgument(!httpClientHosts.isEmpty(), "http.client.hosts must be not empty");
        }

        public Integer getHttpServerPort() {
            return httpServerPort;
        }

        public List<String> getHttpClientHosts() {
            return httpClientHosts;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("httpServerPort", httpServerPort)
                    .add("httpClientHosts", httpClientHosts)
                    .toString();
        }

        private static final int DEFAULT_HTTP_SERVER_PORT = 8081;
        private static final String DEFAULT_HTTP_CLIENT_HOSTS = "www.yandex.ru, www.google.com";
    }

    private static final String HTTP_PROTO = "http";
}
