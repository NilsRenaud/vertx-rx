package examples;

import io.reactivex.*;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.plugins.RxJavaPlugins;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.docgen.Source;
import io.vertx.reactivex.MaybeHelper;
import io.vertx.reactivex.WriteStreamSubscriber;
import io.vertx.reactivex.core.ObservableHelper;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.WorkerExecutor;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.dns.DnsClient;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import io.vertx.reactivex.core.file.AsyncFile;
import io.vertx.reactivex.core.file.FileSystem;
import io.vertx.reactivex.core.http.*;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Source
public class RxifiedExamples {

  public void toFlowable(Vertx vertx) {
    FileSystem fs = vertx.fileSystem();
    fs.open("/data.txt", new OpenOptions()).onComplete( result -> {
      AsyncFile file = result.result();
      Flowable<Buffer> observable = file.toFlowable();
      observable.forEach(data -> System.out.println("Read data: " + data.toString("UTF-8")));
    });
  }

  private static void checkAuth(Handler<AsyncResult<Void>> handler) {
    throw new UnsupportedOperationException();
  }

  public void delayFlowable(HttpServer server) {
    server.requestHandler(request -> {
      if (request.method() == HttpMethod.POST) {

        // Stop receiving buffers
        request.pause();

        checkAuth(res -> {

          // Now we can receive buffers again
          request.resume();

          if (res.succeeded()) {
            Flowable<Buffer> flowable = request.toFlowable();
            flowable.subscribe(buff -> {
              // Get buffers
            });
          }
        });
      }
    });
  }

  public void single(Vertx vertx) {

    // Obtain a single that performs the actual listen on subscribe
    Single<HttpServer> single = vertx
      .createHttpServer()
      .rxListen(1234, "localhost");

    // Subscribe to bind the server
    single.
        subscribe(
            server -> {
              // Server is listening
            },
            failure -> {
              // Server could not start
            }
        );
  }

  public void maybe(Vertx vertx, int dnsPort, String dnsHost, String ipAddress) {

    DnsClient client = vertx.createDnsClient(dnsPort, dnsHost);

    // Obtain a maybe that performs the actual reverse lookup on subscribe
    Maybe<String> maybe = client.rxReverseLookup(ipAddress);

    // Subscribe to perform the lookup
    maybe.
      subscribe(
        name -> {
          // Lookup produced a result
        },
        failure -> {
          // Lookup failed
        },
        () -> {
          // Lookup produced no result
        }
      );
  }

  public void completable(HttpServer server) {

    // Obtain a completable that performs the actual close on subscribe
    Completable single = server.rxClose();

    // Subscribe to bind the server
    single.
      subscribe(
        () -> {
          // Server is closed
        },
        failure -> {
          // Server closed but encoutered issue
        }
      );
  }

  public void executeBlockingAdapter(io.vertx.core.Vertx vertx) {
    Maybe<String> maybe = MaybeHelper.toMaybe(handler -> {
      vertx.executeBlocking(() -> invokeBlocking()).onComplete(handler);
    });
  }

  private String invokeBlocking() {
    return null;
  }

  public void scheduler(Vertx vertx) {
    Scheduler scheduler = RxHelper.scheduler(vertx);
    Observable<Long> timer = Observable.interval(100, 100, TimeUnit.MILLISECONDS, scheduler);
  }

  public void scheduler(WorkerExecutor workerExecutor) {
    Scheduler scheduler = RxHelper.blockingScheduler(workerExecutor);
    Observable<Long> timer = Observable.interval(100, 100, TimeUnit.MILLISECONDS, scheduler);
  }

  public void schedulerHook(Vertx vertx) {
    RxJavaPlugins.setComputationSchedulerHandler(s -> RxHelper.scheduler(vertx));
    RxJavaPlugins.setIoSchedulerHandler(s -> RxHelper.blockingScheduler(vertx));
    RxJavaPlugins.setNewThreadSchedulerHandler(s -> RxHelper.scheduler(vertx));
  }

  private class MyPojo {
  }

  public void unmarshaller(FileSystem fileSystem) {
    fileSystem.open("/data.txt", new OpenOptions()).onComplete(result -> {
      AsyncFile file = result.result();
      Observable<Buffer> observable = file.toObservable();
      observable.compose(ObservableHelper.unmarshaller((MyPojo.class))).subscribe(
        mypojo -> {
          // Process the object
        }
      );
    });
  }

  public void deployVerticle(Vertx vertx, Verticle verticle) {
    Single<String> deployment = RxHelper.deployVerticle(vertx, verticle);

    deployment.subscribe(id -> {
      // Deployed
    }, err -> {
      // Could not deploy
    });
  }

  public void embedded() {
    Vertx vertx = io.vertx.reactivex.core.Vertx.vertx();
  }

  public void verticle() {
    class MyVerticle extends io.vertx.reactivex.core.AbstractVerticle {
      public void start() {
        // Use Rxified Vertx here
      }
    }
  }

  public void rxStart() {
    class MyVerticle extends io.vertx.reactivex.core.AbstractVerticle {
      public Completable rxStart() {
        return vertx.createHttpServer()
          .requestHandler(req -> req.response().end("Hello World"))
          .rxListen()
          .toCompletable();
      }
    }
  }

  public void eventBusMessages(Vertx vertx) {
    EventBus eb = vertx.eventBus();
    MessageConsumer<String> consumer = eb.<String>consumer("the-address");
    Observable<Message<String>> observable = consumer.toObservable();
    Disposable sub = observable.subscribe(msg -> {
      // Got message
    });

    // Unregisters the stream after 10 seconds
    vertx.setTimer(10000, id -> {
      sub.dispose();
    });
  }

  public void eventBusBodies(Vertx vertx) {
    EventBus eb = vertx.eventBus();
    MessageConsumer<String> consumer = eb.<String>consumer("the-address");
    Observable<String> observable = consumer.bodyStream().toObservable();
  }

  public void eventBusMapReduce(Vertx vertx) {
    Observable<Double> observable = vertx.eventBus().
        <Double>consumer("heat-sensor").
        bodyStream().
        toObservable();

    observable.
        buffer(1, TimeUnit.SECONDS).
        map(samples -> samples.
            stream().
            collect(Collectors.averagingDouble(d -> d))).
        subscribe(heat -> {
          vertx.eventBus().send("news-feed", "Current heat is " + heat);
        });
  }

  public void websocketServerBuffer(Flowable<ServerWebSocket> socketObservable) {
    socketObservable.subscribe(
        socket -> {
          Observable<Buffer> dataObs = socket.toObservable();
          dataObs.subscribe(buffer -> {
            System.out.println("Got message " + buffer.toString("UTF-8"));
          });
        }
    );
  }

  public void websocketClient(Vertx vertx) {
    WebSocketClient client = vertx.createWebSocketClient(new WebSocketClientOptions());
    client.rxConnect(8080, "localhost", "/the_uri").subscribe(
        ws -> {
          // Use the websocket
        },
        error -> {
          // Could not connect
        }
    );
  }

  public void websocketClientBuffer(Flowable<WebSocket> socketObservable) {
    socketObservable.subscribe(
        socket -> {
          Flowable<Buffer> dataObs = socket.toFlowable();
          dataObs.subscribe(buffer -> {
            System.out.println("Got message " + buffer.toString("UTF-8"));
          });
        }
    );
  }

  public void httpClientRequest(Vertx vertx) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    Single<HttpClientResponse> request = client
      .rxRequest( HttpMethod.GET, 8080, "localhost", "/the_uri")
      .flatMap(HttpClientRequest::rxSend);
    request.subscribe(
      response -> {
        // Process the response
      },
      error -> {
        // Could not connect
      }
    );
  }

  public void httpClientResponse(HttpClient client) {
    Single<HttpClientResponse> request = client
      .rxRequest(HttpMethod.GET, 8080, "localhost", "/the_uri")
      .flatMap(HttpClientRequest::rxSend);
    request.subscribe(
      response -> {
        Observable<Buffer> observable = response.toObservable();
        observable.forEach(
          buffer -> {
            // Process buffer
          }
        );
      }
    );
  }

  public void httpClientResponseFlatMap(HttpClient client) {
    Single<HttpClientResponse> request = client
      .rxRequest(HttpMethod.GET, 8080, "localhost", "/the_uri")
      .flatMap(HttpClientRequest::rxSend);
    request.
      flatMapObservable(HttpClientResponse::toObservable).
      forEach(
        buffer -> {
          // Process buffer
        }
      );
  }

  public void httpServerRequestObservable(HttpServerRequest request) {
    Observable<Buffer> observable = request.toObservable();
  }

  public void httpServerRequestObservableUnmarshall(HttpServerRequest request) {
    Observable<MyPojo> observable = request.
      toObservable().
      compose(io.vertx.reactivex.core.ObservableHelper.unmarshaller(MyPojo.class));
  }

  public void writeStreamSubscriberAdapter(Flowable<io.vertx.core.buffer.Buffer> flowable, io.vertx.core.http.HttpServerResponse response) {
    response.setChunked(true);
    WriteStreamSubscriber<io.vertx.core.buffer.Buffer> subscriber = io.vertx.reactivex.RxHelper.toSubscriber(response);
    flowable.subscribe(subscriber);
  }

  public void rxWriteStreamSubscriberAdapter(Flowable<Buffer> flowable, HttpServerResponse response) {
    response.setChunked(true);
    flowable.subscribe(response.toSubscriber());
  }

  public void writeStreamSubscriberAdapterCallbacks(Flowable<Buffer> flowable, HttpServerResponse response) {
    response.setChunked(true);

    WriteStreamSubscriber<Buffer> subscriber = response.toSubscriber();

    subscriber.onError(throwable -> {
      if (!response.headWritten() && response.closed()) {
        response.setStatusCode(500).end("oops");
      } else {
        // log error
      }
    });

    subscriber.onWriteStreamError(throwable -> {
      // log error
    });

    subscriber.onWriteStreamEnd(() -> {
      // log end of transaction to audit system...
    });

    flowable.subscribe(subscriber);
  }
}
