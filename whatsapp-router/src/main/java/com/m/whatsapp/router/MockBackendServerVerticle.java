package com.m.whatsapp.router;

import com.m.whatsapp.router.reverseproxy.ReverseProxyHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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
public class MockBackendServerVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MockBackendServerVerticle.class);

    private HttpServer server;

    @Override
    public void start(Promise<Void> startPromise) {

        LOG.info("-- MOCK BACKEND SERVER --");
        server = vertx.createHttpServer(new HttpServerOptions()).requestHandler(
                req -> {
                    req.connection().exceptionHandler(t -> LOG.info("Exception del connection: " + t.getMessage()));
                    req.exceptionHandler(t -> LOG.info("Exception despues del reqHandler: " + t.getMessage()));
                    req.response().end("response!!");

                }).exceptionHandler(t -> LOG.info("Exception antes del reqHandler: " + t.getMessage()));

        // Now bind the server:
        LOG.info("-- Binding port: 9443 --");

        server.listen(9443, res -> {
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