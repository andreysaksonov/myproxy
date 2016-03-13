package me.saksonov.myproxy;

import com.google.common.base.Strings;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import me.saksonov.myproxy.support.ServerSocketUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MyProxyTest {

    private Vertx vertx;
    private int httpServerPort;

    @Before
    public void initialize(TestContext context) {
        vertx = Vertx.vertx();
        httpServerPort = ServerSocketUtils.getLocalPort();

        JsonObject config = new JsonObject()
                .put("http.server.port", httpServerPort)
                .put("http.client.hosts", "www.yandex.ru, www.google.com");

        DeploymentOptions deploymentOptions = new DeploymentOptions()
                .setConfig(config);

        vertx.deployVerticle(MyProxy.class.getName(),
                deploymentOptions,
                context.asyncAssertSuccess());
    }

    @Test
    public void testMyProxy(TestContext context) {
        Async async = context.async();

        vertx.createHttpClient().getNow(httpServerPort, "localhost", "/",
                response -> {
                    response.handler(buffer -> context.assertFalse(Strings.isNullOrEmpty(buffer.toString())));
                    response.endHandler($ -> async.complete());
                });
    }

    @After
    public void close(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }
}
