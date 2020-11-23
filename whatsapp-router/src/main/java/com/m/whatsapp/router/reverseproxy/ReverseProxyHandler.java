package com.m.whatsapp.router.reverseproxy;

import com.m.whatsapp.router.circuitbreaker.CircuitBreakerProvider;
import io.netty.util.internal.StringUtil;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.OpenCircuitException;
import io.vertx.circuitbreaker.TimeoutException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.Random;

/**
 * Created by dmoreira <diegomoreira00@gmail.com> on 13/11/2020.
 */
public class ReverseProxyHandler implements Handler<HttpServerRequest> {


    private static final Logger LOG = LoggerFactory.getLogger(ReverseProxyHandler.class);


    //@ConfigProperty(name = "reverse-proxy.routing.target-host-header-name", defaultValue = "X-Target-Host")
    public static final  String TARGET_HOST_HEADER_NAME = "X-Target-Host";
    //@ConfigProperty(name = "reverse-proxy.routing.target-port-header-name", defaultValue = "X-Target-Port")
    public static final String TARGET_PORt_HEADER_NAME = "X-Target-Port";
    //@ConfigProperty(name = "reverse-proxy.forward-request.timeout", defaultValue = "40000")
    public static final Integer FORWARD_REQUEST_TIMEOUT = 12000;

    private final CircuitBreakerProvider circuitBreakerProvider;
    private final HttpClient httpClient;
    private final Vertx vertx;

    public ReverseProxyHandler(final CircuitBreakerProvider circuitBreakerProvider,
                               final HttpClient httpClient,
                               final Vertx vertx) {
        this.circuitBreakerProvider = circuitBreakerProvider;
        this.vertx = vertx;
        this.httpClient = httpClient;
    }

    /**
     * A small method for handling inbound requests.
     * <p>
     * Receives an incoming request and forwards it to the target URL.
     *
     * @param proxyRequest
     */

    @Override
    public void handle(final HttpServerRequest proxyRequest) {

        final String targetHost = retrieveHeader(proxyRequest, TARGET_HOST_HEADER_NAME);
        int targetPort = Integer.parseInt(retrieveHeader(proxyRequest, TARGET_PORt_HEADER_NAME));

        Integer traceId = new Random().nextInt();
        proxyRequest.pause();
        //Get a circuit for the specific route (host + port).
        HttpServerResponse proxyResponse = proxyRequest.response();
        final CircuitBreaker cb = circuitBreakerProvider.getOrCreate(targetHost + targetPort);


        cb.executeWithFallback( promise -> {

            proxyRequest.exceptionHandler(t -> {
                LOG.info(String.format("[%d] [%s][Proxy Request] Exception!!! ", System.currentTimeMillis(),
                        traceId));
                promise.fail(t);
            });
            proxyResponse.exceptionHandler(t -> {
                LOG.info(String.format("[%d] [%s][Proxy Response] Exception!!! ", System.currentTimeMillis(),
                        traceId));
                promise.fail(t);
            });
            //Prepares a Forward Request
            vertx.executeBlocking( blockingPromise -> {
                        createForwardRequest(proxyRequest, targetHost, targetPort, traceId)
                                .onSuccess(forwardReq -> {
                                    //Avoid propagating the helper headers
                                    proxyRequest.headers().forEach(proxyHeader -> {
                                        if (!"Host".equals(proxyHeader.getKey()) &&
                                                !TARGET_HOST_HEADER_NAME.equals(proxyHeader.getKey()) &&
                                                !TARGET_PORt_HEADER_NAME.equals(proxyHeader.getKey())) {
                                            forwardReq.putHeader(proxyHeader.getKey(), proxyHeader.getValue());
                                        }
                                    });
                                    forwardReq.exceptionHandler(t -> {
                                        LOG.info(String.format("[%d] [%s][Forward Request] Exception!!! ", System.currentTimeMillis(), traceId));
                                        blockingPromise.fail(t);
                                    });
                                    forwardReq.send(proxyRequest)
                                            .onSuccess(forwardResponse -> {
                                                LOG.info(String.format("[%d] [%s][Forward Response] Handling Response ", System.currentTimeMillis(),
                                                        traceId));
                                                proxyResponse.setStatusCode(forwardResponse.statusCode())
                                                        .setStatusMessage(forwardResponse.statusMessage());
                                                proxyResponse.headers().addAll(forwardResponse.headers());

                                                forwardResponse.exceptionHandler(t -> {
                                                    LOG.info(String.format("[%d] [%s][Forward Response] " +
                                                            "Exception!!! ", System.currentTimeMillis(), traceId));
                                                    blockingPromise.fail(t);
                                                });
                                                //Send response back to client
                                                proxyResponse.send(forwardResponse).onSuccess(v -> blockingPromise.complete()).onFailure(blockingPromise::fail);
                                            }).onFailure(blockingPromise::fail);
                                });
                    }).onSuccess(o -> promise.complete()).onFailure(promise::fail);

            //Triggered when the proxyRequest
            /*
            proxyRequest.endHandler(v -> {
                LOG.info(String.format("[%d] [%s][Proxy Request] Ending ", System.currentTimeMillis(), traceId));
                forwardReq.end();
            });

            //Execute a Forward request; When there are no more chucks, it triggers the endHandler
            proxyRequest.handler(t -> {
                LOG.info(String.format("[%d] [%s][Proxy Request] Handling Read ", System.currentTimeMillis(), traceId));
                forwardReq.write(t);
            });

            LOG.info(String.format("[%d] [%s][Circuit Breaker] Start ", System.currentTimeMillis(), traceId));
            proxyRequest.resume();*/

        }, throwable -> {

            if (throwable instanceof OpenCircuitException) {
                LOG.info(String.format("[%d] [%s][Circuit Breaker] OpenCircuit!!! ", System.currentTimeMillis(), traceId));
                proxyResponse.setStatusCode(502).setStatusMessage("Bad Gateway - Circuit is open").end();
                LOG.error("[Circuit Breaker Fallback] Key: "+ cb.name() + " State: " + cb.state(), throwable);
            } else if (throwable instanceof TimeoutException) {
                LOG.info(String.format("[%d] [%s][Circuit Breaker] CB Timeout!!! ", System.currentTimeMillis(), traceId));
                LOG.error("[Circuit Breaker Fallback] Key: "+ cb.name() + " State: " + cb.state(), throwable);
                proxyResponse.setStatusCode(504).setStatusMessage("Bad Gateway - Command took too long").end();
            } else {
                LOG.info(String.format("[%d] [%s][Circuit Breaker] WhoKnows!!! ", System.currentTimeMillis(), traceId));
                LOG.error("[Circuit Breaker Fallback] Key: "+ cb.name() + " State: " + cb.state() + " - Message: "
                        + throwable.getMessage(), throwable);
                proxyResponse.setStatusCode(502).setStatusMessage("Bad Gateway - Timeout").end(throwable.getLocalizedMessage());
            }
            return "fallback";
        });

    }



    /**
     * Convenience method for retrieving a header from a request.
     * We fail with a Bad Request if the requested header is null or empty is not present
     *
     * @param proxyRequest
     * @return the target uri
     */
    private String retrieveHeader(final HttpServerRequest proxyRequest, String headerName) {

        final String header = proxyRequest.getHeader(headerName);
        if (StringUtil.isNullOrEmpty(header)) {
            proxyRequest.response()
                    .setStatusCode(400)
                    .setStatusMessage("Bad Request - " + headerName + " " + "is null")
                    .end();
            throw new IllegalArgumentException(headerName + " is null. Cannot resolve target backend.");
        }
        return header;
    }



    /**
     * Creates a forwardRequest from a proxyRequest.
     * Resolves the target HOST:PORT using the "target headers" from the request.
     * It also copies all the headers but filters Host and the Custom Proxy Headers.
     * Attaches a response handler to the request.
     *
     * IMPORTANT: Is up to the consumer of this method to fill the body afterwards!!
     *
     * @param proxyRequest inbound server request
     * @param targetHost backend's destination host
     * @param targetPort backend's destination port
     * @return HttpClientRequest a fully configured request
     */
    private Future<HttpClientRequest> createForwardRequest(final HttpServerRequest proxyRequest,
                                                   String targetHost,
                                                   int targetPort,
                                                   Integer traceId) {

        final Future<HttpClientRequest> forwardReq = httpClient.request(proxyRequest.method(),
                targetPort,
                targetHost,
                proxyRequest.uri());

        LOG.info(String.format("[%d] [%s][Forward Request] Creating ", System.currentTimeMillis(), traceId));

        return forwardReq;
    }

}
