package com.zandbee.mobius;

import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

import java.util.ArrayList;
import java.util.List;

/**
 * Reverse Mobius - Simple HTTP Reverse Proxy.
 *
 * @author Andrey Saksonov
 */
public class ReverseProxyVerticle extends Verticle {

    private static final String HOST = "host";
    private static final String PORT = "port";

    private JsonObject config;
    private Logger logger;

    private final List<String> hosts = new ArrayList<>();

    public void start() {
        config = container.config();
        logger = container.logger();
        RouteMatcher routeMatcher = new RouteMatcher();
        routeMatcher.all("/*", new ReverseProxyHandler());

        String host = config.getString(HOST);
        if (host != null) {
            logger.info("Proxy: " + host);
            hosts.add(host);
        } else {
            int i = 1;
            do {
                host = config.getString(HOST + i);
                if (host != null) {
                    logger.info("Proxy " + i + ": " + host);
                    hosts.add(host);
                }
                i++;
            } while (host != null);
        }
        Integer port = config.getInteger(PORT);

        if (hosts.size() > 0 && port != null) {
            final HttpServer httpsServer = vertx.createHttpServer().requestHandler(routeMatcher);
            httpsServer.listen(port);
            logger.info("Started Reverse Mobius!");
        } else {
            logger.error("Reverse Mobius Error!");
            System.exit(1);
        }
    }

    private final class ReverseProxyHandler implements Handler<HttpServerRequest> {

        private int index = 0;

        @Override
        public void handle(final HttpServerRequest serverRequest) {
            HttpClient httpClient = vertx.createHttpClient();
            httpClient.setHost(getHost());

            final HttpClientRequest clientRequest = httpClient.request(serverRequest.method(), serverRequest.uri(), new Handler<HttpClientResponse>() {
                public void handle(HttpClientResponse clientResponse) {

                    serverRequest.response().setStatusCode(clientResponse.statusCode());
                    serverRequest.response().headers().add(clientResponse.headers());
                    serverRequest.response().setChunked(true);
                    clientResponse.dataHandler(new Handler<Buffer>() {
                        public void handle(Buffer data) {
                            serverRequest.response().write(data);
                        }
                    });
                    clientResponse.endHandler(new VoidHandler() {
                        public void handle() {
                            serverRequest.response().end();
                        }
                    });
                }
            });
            clientRequest.headers().set(serverRequest.headers());
            clientRequest.setChunked(true);
            clientRequest.end();
        }

        private String getHost() {
            String host = hosts.get(index);
            logger.debug("Host: " + host);
            if (++index == hosts.size()) {
                index = 0;
            }
            return host;
        }
    }
}
