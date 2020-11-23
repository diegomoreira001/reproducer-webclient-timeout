package com.m.whatsapp.router;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class VerticleDeployer extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(VerticleDeployer.class);

    @Override
    public void start(Promise<Void> startPromise) {

        //Horizontal Scaling across cores
        vertx.deployVerticle(BifrostVerticle.class.getName(), new DeploymentOptions().setInstances(3));
        vertx.deployVerticle(MockBackendServerVerticle.class.getName());
        //vertx.deployVerticle(MockBackendServerVerticle2.class.getName());
    }

    @Override
    public void stop() throws Exception {
    }

}