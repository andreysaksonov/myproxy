package me.saksonov.myproxy;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.core.http.HttpServerResponse;
import me.saksonov.myproxy.support.ForwardedHeader;

import java.util.Iterator;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public class MyProxy extends AbstractVerticle {

    private static Logger logger = LoggerFactory.getLogger(MyProxy.class);

    @Override
    public void start(Future<Void> startFuture) {
        logger.info("MyProxy 0.2");

        Config config = new Config();

        HttpServer httpServer = vertx.createHttpServer();
        HttpClient httpClient = vertx.createHttpClient();

        Iterator<String> hosts = Iterables.cycle(config.getHttpClientHosts()).iterator();

        httpServer.requestStream().toObservable().subscribe(serverRequest -> {
            String host = hosts.next();

            HttpMethod method = serverRequest.method();
            String uri = serverRequest.uri();

            HttpClientRequest clientRequest = httpClient.request(method, host, uri).setChunked(true);

            String forwardedBy = serverRequest.localAddress().host();
            String forwardedFor = serverRequest.remoteAddress().host();
            String forwardedHost = serverRequest.getHeader(Headers.HOST);

            clientRequest.headers()
                    .setAll(serverRequest.headers())
                    .set(Headers.HOST, host)
                    // RFC 7239 "Forwarded HTTP Extension"
                    .set(Headers.FORWARDED, ForwardedHeader.format(forwardedBy, forwardedFor, forwardedHost, HTTP_PROTO));

            clientRequest.toObservable().subscribe(
                    clientResponse -> {
                        HttpServerResponse serverResponse = serverRequest.response();

                        serverResponse.setChunked(true);
                        serverResponse.setStatusCode(clientResponse.statusCode());
                        serverResponse.headers().setAll(clientResponse.headers());
                        serverResponse.headers().set(Headers.HOST, serverRequest.getHeader(Headers.HOST));

                        clientResponse.toObservable().subscribe(serverResponse::write);
                        serverResponse.end();
                    }
            );

            serverRequest.toObservable().subscribe(clientRequest::write);
            clientRequest.end();

            logger.trace(String.format("%s %s -> %s %s%s", method, uri, method, host, uri));
        });

        httpServer.listenObservable(config.getHttpServerPort())
                .subscribe($ -> startFuture.complete(), error -> startFuture.fail(error.getCause()));
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
