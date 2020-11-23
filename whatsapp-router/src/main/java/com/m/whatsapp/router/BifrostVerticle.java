package com.m.whatsapp.router;

import com.m.whatsapp.router.circuitbreaker.CircuitBreakerProvider;
import com.m.whatsapp.router.reverseproxy.ReverseProxyHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * A high performance non-blocking reverse proxy verticle.
 *
 * Goals:
 * - Dynamic Routing based on Request Input (a target header)
 * - Circuit Breaking per route (Fail-Fast)
 * - Non-blocking http client external calls
 * - High number of routes with low thread count
 *
 */
public class BifrostVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(BifrostVerticle.class);
    private HttpServer server;

    @Override
    public void start(Promise<Void> startPromise) {


        final CircuitBreakerProvider circuitBreakerProvider =
                new CircuitBreakerProvider(vertx);

        vertx.exceptionHandler(t -> LOG.error("Vetx",t));
        ver.
        HttpClient client = vertx.createHttpClient(new HttpClientOptions()
        .setKeepAlive(false)
        .setMaxPoolSize(9000)
        .setTcpKeepAlive(false));

        final ReverseProxyHandler reverseProxyHandler = new ReverseProxyHandler(circuitBreakerProvider,
                client,
                vertx);

        LOG.info("-- Eagerly Starting Bifrost Verticle --");
        LOG.info("-- Creating Vertx Http Server & Registering hanlder.\n -- This may take a while");
        server = vertx.createHttpServer(new HttpServerOptions())
                .requestHandler(reverseProxyHandler)
                .exceptionHandler(t -> LOG.error("Couldn't handle request: " + t.getMessage(),t));

        // Now bind the server:
        LOG.info("-- Binding port: 8080 --");
        server.listen(8080, res -> {
            if (res.succeeded()) {
                startPromise.complete();
            } else {
                startPromise.fail(res.cause());
            }
        });
    }

    @Override
    public void stop() throws Exception {
        LOG.info("-- Stopping Bifrost Verticle & Closing Vertx Server --");
        server.close();
    }

}