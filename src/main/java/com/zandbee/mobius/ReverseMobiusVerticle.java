package com.zandbee.mobius;

import com.google.common.net.InetAddresses;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reverse Mobius - Simple HTTP Reverse Proxy.
 *
 * @author Andrey Saksonov
 */
public class ReverseMobiusVerticle extends Verticle {

    private static final String REVERSE_MOBIUS_IP = "REVERSE_MOBIUS_IP";
    private static final String REVERSE_MOBIUS_PORT = "REVERSE_MOBIUS_PORT";
    private static final String HOST = "host";

    private JsonObject config;
    private Logger logger;
    private Map<String, String> env;

    private final List<String> hosts = new ArrayList<>();

    public void start() {
        config = container.config();
        logger = container.logger();
        env = container.env();

        Integer port = 8080;
        try {
            String p = env.get(REVERSE_MOBIUS_PORT);
            if (p != null) {
                port = Integer.parseInt(p);
                if (port < 1 || port > 65535) {
                    error("$REVERSE_MOBIUS_PORT -> Bad Port");
                }
            }
        } catch (NumberFormatException e) {
            error("$REVERSE_MOBIUS_PORT -> Bad Port");
        }

        String inetAddress = env.getOrDefault(REVERSE_MOBIUS_IP, "127.0.0.1");
        if (!InetAddresses.isInetAddress(inetAddress)) {
            error("$REVERSE_MOBIUS_IP -> Bad IP");
        }

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

        if (hosts.size() > 0) {
            final HttpServer httpsServer = vertx.createHttpServer().requestHandler(routeMatcher);
            httpsServer.listen(port, inetAddress);
            logger.info("Started Reverse Mobius!");
        } else {
            error("No hosts configured! Configure backend hosts via json file passed to -conf parameter!");
        }
    }

    private void error(String message) {
        logger.error(message);
        System.exit(1);
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
            // logger.debug("Host: " + host);
            if (++index == hosts.size()) {
                index = 0;
            }
            return host;
        }
    }
}
