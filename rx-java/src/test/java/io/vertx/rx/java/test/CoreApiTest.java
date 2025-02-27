package io.vertx.rx.java.test;

import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.lang.rx.test.SimplePojo;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Context;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.core.file.AsyncFile;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.WebSocketClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.core.http.HttpServerRequest;
import io.vertx.rxjava.core.http.ServerWebSocket;
import io.vertx.rxjava.core.http.WebSocket;
import io.vertx.rxjava.core.net.NetServer;
import io.vertx.rxjava.core.net.NetSocket;
import io.vertx.rxjava.core.parsetools.RecordParser;
import io.vertx.rxjava.core.streams.ReadStream;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;
import rx.Observable;
import rx.Observer;
import rx.Single;
import rx.Subscriber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CoreApiTest extends VertxTestBase {

  private Vertx vertx;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    vertx = new Vertx(super.vertx);
  }

  @Test
  public void testConsumeBodyStream() {
    EventBus eb = vertx.eventBus();
    MessageConsumer<String> consumer = eb.<String>consumer("the-address");
    Observable<String> obs = consumer.bodyStream().toObservable();
    List<String> items = new ArrayList<>();
    obs.subscribe(new Subscriber<String>() {
      @Override
      public void onNext(String s) {
        items.add(s);
        if (items.size() == 3) {
          unsubscribe();
          assertEquals(Arrays.asList("msg1", "msg2", "msg3"), items);
          assertFalse(consumer.isRegistered());
          testComplete();
        }
      }

      @Override
      public void onError(Throwable throwable) {
        fail(throwable.getMessage());
      }

      @Override
      public void onCompleted() {
        fail();
      }
    });
    eb.send("the-address", "msg1");
    eb.send("the-address", "msg2");
    eb.send("the-address", "msg3");
    await();
  }

  @Test
  public void testRegisterAgain() {
    EventBus eb = vertx.eventBus();
    MessageConsumer<String> consumer = eb.<String>consumer("the-address");
    Observable<String> obs = consumer.bodyStream().toObservable();
    obs.subscribe(new Subscriber<String>() {
      @Override
      public void onNext(String s) {
        assertEquals("msg1", s);
        unsubscribe();
        assertFalse(consumer.isRegistered());
        obs.subscribe(new Subscriber<String>() {
          @Override
          public void onNext(String s) {
            assertEquals("msg2", s);
            unsubscribe();
            assertFalse(consumer.isRegistered());
            testComplete();
          }

          @Override
          public void onError(Throwable throwable) {
            fail("Was not esxpecting error " + throwable.getMessage());
          }

          @Override
          public void onCompleted() {
            fail();
          }
        });
        eb.send("the-address", "msg2");
      }

      @Override
      public void onError(Throwable throwable) {
        fail("Was not esxpecting error " + throwable.getMessage());
      }

      @Override
      public void onCompleted() {
        fail();
      }
    });
    eb.send("the-address", "msg1");
    await();
  }

  @Test
  public void testObservableUnsubscribeDuringObservation() {
    EventBus eb = vertx.eventBus();
    MessageConsumer<String> consumer = eb.<String>consumer("the-address");
    Observable<String> obs = consumer.bodyStream().toObservable();
    Observable<String> a = obs.take(4);
    List<String> obtained = new ArrayList<>();
    a.subscribe(new Subscriber<String>() {
      @Override
      public void onCompleted() {
        assertEquals(Arrays.asList("msg0", "msg1", "msg2", "msg3"), obtained);
        testComplete();
      }

      @Override
      public void onError(Throwable e) {
        fail(e.getMessage());
      }

      @Override
      public void onNext(String str) {
        obtained.add(str);
      }
    });
    for (int i = 0; i < 7; i++) {
      eb.send("the-address", "msg" + i);
    }
    await();
  }

  @Test
  public void testUnregisterConsumer() {
    EventBus eb = vertx.eventBus();
    MessageConsumer<String> consumer = eb.<String>consumer("the-address");
    Observable<String> obs = consumer.bodyStream().toObservable();
    obs.subscribe(new Subscriber<String>() {
      @Override
      public void onCompleted() {
        testComplete();
      }
      @Override
      public void onError(Throwable e) {
        fail(e.getMessage());
      }
      @Override
      public void onNext(String str) {
        fail();
      }
    });
    consumer.unregister();
    await();
  }

  @Test
  public void testConcatReplies() {
    EventBus eb = vertx.eventBus();
    eb.<String>consumer("the-address", msg -> {
      msg.reply(msg.body());
    });
    Single<Message<String>> obs1 = eb.rxRequest("the-address", "msg1");
    Single<Message<String>> obs2 = eb.rxRequest("the-address", "msg2");
    eb.request("the-address", "done").onComplete(reply -> {
      Observable<Message<String>> all = Single.concat(obs1, obs2);
      LinkedList<String> values = new LinkedList<String>();
      all.subscribe(next -> {
        values.add(next.body());
      }, err -> {
        fail();
      }, () -> {
        assertEquals(Arrays.asList("msg1", "msg2"), values);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testObservableNetSocket() {
    ObservableFuture<NetServer> onListen = RxHelper.observableFuture();
    onListen.subscribe(
        server -> vertx.createNetClient(new NetClientOptions()).connect(1234, "localhost").onComplete(ar -> {
          assertTrue(ar.succeeded());
          NetSocket so = ar.result();
          so.write("foo");
          so.close();
        }),
        error -> fail(error.getMessage())
    );
    NetServer server = vertx.createNetServer(new NetServerOptions().setPort(1234).setHost("localhost"));
    server.connectHandler(o -> {
      Observable<Buffer> dataObs = o.toObservable();
      dataObs.subscribe(new Observer<Buffer>() {

        LinkedList<Buffer> buffers = new LinkedList<>();

        @Override
        public void onNext(Buffer buffer) {
          buffers.add(buffer);
        }

        @Override
        public void onError(Throwable e) {
          fail(e.getMessage());
        }

        @Override
        public void onCompleted() {
          assertEquals(1, buffers.size());
          assertEquals("foo", buffers.get(0).toString("UTF-8"));
          server.close();
          testComplete();
        }
      });
    });
    server.listen().onComplete(onListen.toHandler());
    await();
  }

  @Test
  public void testObservableWebSocket() {
    ObservableFuture<HttpServer> onListen = RxHelper.observableFuture();
    onListen.subscribe(
        server -> vertx.createWebSocketClient(new WebSocketClientOptions()).connect(8080, "localhost", "/some/path").onComplete(ar -> {
          if (ar.succeeded()) {
            WebSocket ws = ar.result();
            ws.write(Buffer.buffer("foo"));
            ws.close();
          } else {
            fail(ar.cause().getMessage());
          }
        })
    );
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(8080).setHost("localhost"));
    server.webSocketHandler(o -> {
      Observable<Buffer> dataObs = o.toObservable();
      dataObs.subscribe(new Observer<Buffer>() {

        LinkedList<Buffer> buffers = new LinkedList<>();

        @Override
        public void onNext(Buffer buffer) {
          buffers.add(buffer);
        }

        @Override
        public void onError(Throwable e) {
          fail(e.getMessage());
        }

        @Override
        public void onCompleted() {
          assertEquals(1, buffers.size());
          assertEquals("foo", buffers.get(0).toString("UTF-8"));
          server.close();
          testComplete();
        }
      });
    });
    server.listen().onComplete(onListen.toHandler());
    await();
  }

  @Test
  public void testObservableHttpRequest() {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(8080).setHost("localhost"));
    server.requestHandler(o -> {
      Observable<Buffer> dataObs = o.toObservable();
      dataObs.subscribe(new Observer<Buffer>() {

        LinkedList<Buffer> buffers = new LinkedList<>();

        @Override
        public void onNext(Buffer buffer) {
          buffers.add(buffer);
        }

        @Override
        public void onError(Throwable e) {
          fail(e.getMessage());
        }

        @Override
        public void onCompleted() {
          assertEquals(1, buffers.size());
          assertEquals("foo", buffers.get(0).toString("UTF-8"));
          server.close();
          testComplete();
        }
      });
    });
    Single<HttpServer> onListen = server.rxListen();
    onListen.subscribe(
        s -> {
          vertx.createHttpClient(new HttpClientOptions())
            .rxRequest(HttpMethod.PUT, 8080, "localhost", "/some/path")
            .subscribe(req -> {
              req.putHeader("Content-Length", "3");
              req.write("foo");
            });
        },
        error -> fail(error.getMessage())
    );
    await();
  }

  @Test
  public void testScheduledTimer() {
    vertx.runOnContext(v -> {
      long startTime = System.currentTimeMillis();
      Context initCtx = Vertx.currentContext();
      Observable.timer(100, 100, TimeUnit.MILLISECONDS, io.vertx.rxjava.core.RxHelper.scheduler(vertx)).take(10).subscribe(new Observer<Long>() {
        public void onNext(Long value) {
          assertEquals(initCtx.getDelegate(), Vertx.currentContext().getDelegate());
        }

        public void onError(Throwable e) {
          fail("unexpected failure");
        }

        public void onCompleted() {
          long timeTaken = System.currentTimeMillis() - startTime;
          assertTrue("Was expecting to have time taken | " + timeTaken + " -  1000 | < 200", Math.abs(timeTaken - 1000) < 1000);
          testComplete();
        }
      });
    });
    await();
  }

  @Test
  public void testScheduledBuffer() {
    vertx.runOnContext(v -> {
      long startTime = System.currentTimeMillis();
      Context initCtx = Vertx.currentContext();
      Observable
          .timer(10, 10, TimeUnit.MILLISECONDS, io.vertx.rxjava.core.RxHelper.scheduler(vertx))
          .buffer(100, TimeUnit.MILLISECONDS, io.vertx.rxjava.core.RxHelper.scheduler(vertx))
          .take(10)
          .subscribe(new Observer<List<Long>>() {
            private int eventCount = 0;

            public void onNext(List<Long> value) {
              eventCount++;
              assertEquals(initCtx.getDelegate(), Vertx.currentContext().getDelegate());
            }

            public void onError(Throwable e) {
              fail("unexpected failure");
            }

            public void onCompleted() {
              long timeTaken = System.currentTimeMillis() - startTime;
              assertEquals(10, eventCount);
              assertTrue("Was expecting to have time taken | " + timeTaken + " -  1000 | < 200", Math.abs(timeTaken - 1000) < 1000);
              testComplete();
            }
          });
    });
    await();
  }

  @Test
  public void testTimeMap() {
    vertx.runOnContext(v -> {
      Context initCtx = Vertx.currentContext();
      EventBus eb = vertx.eventBus();
      ReadStream<String> consumer = eb.<String>localConsumer("the-address").bodyStream();
      Observer<String> observer = new Subscriber<String>() {
        boolean first = true;
        @Override
        public void onNext(String s) {
          if (first) {
            first = false;
            assertEquals(initCtx.getDelegate(), Vertx.currentContext().getDelegate());
            assertEquals("msg1msg2msg3", s);
            testComplete();
          }
        }
        @Override
        public void onError(Throwable e) {
          fail(e.getMessage());
        }
        @Override
        public void onCompleted() {
        }
      };
      Observable<String> observable = consumer.toObservable();
      observable.
          buffer(500, TimeUnit.MILLISECONDS, io.vertx.rxjava.core.RxHelper.scheduler(vertx)).
          map(samples -> samples.stream().reduce("", (a, b) -> a + b)).
          subscribe(observer);
      eb.send("the-address", "msg1");
      eb.send("the-address", "msg2");
      eb.send("the-address", "msg3");
    });
    await();
  }

  @Test
  public void testObserverToFuture() {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(8080)).requestHandler(req -> {});
    AtomicInteger count = new AtomicInteger();
    Observer<HttpServer> observer = new Observer<HttpServer>() {
      @Override
      public void onCompleted() {
        server.close();
        assertEquals(1, count.get());
        testComplete();
      }

      @Override
      public void onError(Throwable e) {
        fail(e.getMessage());
      }

      @Override
      public void onNext(HttpServer httpServer) {
        count.incrementAndGet();
      }
    };
    Single<HttpServer> onListen = server.rxListen();
    onListen.subscribe(observer);
    await();
  }

  @Test
  public void testObserverToHandler() throws Exception {
    AtomicInteger count = new AtomicInteger();
    Observer<Long> observer = new Observer<Long>() {
      @Override
      public void onCompleted() {
        assertEquals(1, count.get());
        testComplete();
      }

      @Override
      public void onError(Throwable e) {
        fail(e.getMessage());
      }

      @Override
      public void onNext(Long l) {
        count.incrementAndGet();
      }
    };
    vertx.setTimer(1, RxHelper.toHandler(observer));
    await();
  }

  @Test
  public void testHttpClient() {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(8080));
    server.requestHandler(req -> {
      req.response().setChunked(true).end("some_content");
    });
    try {
      server.listen().onComplete(ar -> {
        HttpClient client = vertx.createHttpClient(new HttpClientOptions());
        client
          .rxRequest(HttpMethod.GET, 8080, "localhost", "/the_uri")
          .flatMap(HttpClientRequest::rxSend)
          .subscribe(resp -> {
          Buffer content = Buffer.buffer();
          Observable<Buffer> observable = resp.toObservable();
          observable.forEach(content::appendBuffer, err -> fail(), () -> {
            assertEquals("some_content", content.toString("UTF-8"));
            testComplete();
          });
        });
      });
      await();
    } finally {
      server.close();
    }
  }

  @Test
  public void testHttpClientFlatMap() {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(8080));
    server.requestHandler(req -> {
      req.response().setChunked(true).end("some_content");
    });
    server.listen().onComplete(ar -> {
      HttpClient client = vertx.createHttpClient(new HttpClientOptions());
      Single<HttpClientResponse> req = client
        .rxRequest(HttpMethod.GET, 8080, "localhost", "/the_uri")
        .flatMap(HttpClientRequest::rxSend);
      Buffer content = Buffer.buffer();
      req.flatMapObservable(HttpClientResponse::toObservable).forEach(
          content::appendBuffer,
          err -> fail(), () -> {
        server.close();
        assertEquals("some_content", content.toString("UTF-8"));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testHttpClientFlatMapUnmarshallPojo() {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(8080));
    server.requestHandler(req -> {
      req.response().setChunked(true).end("{\"foo\":\"bar\"}");
    });
    server.listen().onComplete(ar -> {
      HttpClient client = vertx.createHttpClient(new HttpClientOptions());
      Single<HttpClientResponse> req = client
        .rxRequest(HttpMethod.GET, 8080, "localhost", "/the_uri")
        .flatMap(HttpClientRequest::rxSend);
      ArrayList<SimplePojo> objects = new ArrayList<>();
      req.flatMapObservable(HttpClientResponse::toObservable).
          lift(io.vertx.rxjava.core.RxHelper.unmarshaller(SimplePojo.class)).
          forEach(
              objects::add,
              err -> fail(), () -> {
                server.close();
                assertEquals(Arrays.asList(new SimplePojo("bar")), objects);
                testComplete();
              });
    });
    await();
  }

  @Test
  public void testHttpClientConnectionFailure() {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    Single<HttpClientResponse> req = client
      .rxRequest(HttpMethod.GET, 9998, "255.255.255.255", "/the_uri")
      .flatMap(HttpClientRequest::rxSend);
    req.subscribe(
        resp -> fail(),
        err -> testComplete());
    await();
  }

  @Test
  public void testHttpClientConnectionFailureFlatMap() {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    Single<HttpClientResponse> req = client
      .rxRequest(HttpMethod.GET, 9998, "255.255.255.255", "/the_uri")
      .flatMap(HttpClientRequest::rxSend);
    req.flatMapObservable(HttpClientResponse::toObservable).forEach(
        buffer -> fail(),
        err -> testComplete(),
        this::fail);
    await();
  }

  @Test
  public void testWebsocketClient() throws Exception {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(8080));
    WebSocketClient client = vertx.createWebSocketClient(new WebSocketClientOptions());
    server.webSocketHandler(ws -> {
      ws.write(Buffer.buffer("some_content"));
      ws.close();
    });
    awaitFuture(server.listen());
    vertx.getDelegate().runOnContext(v -> {
      client.connect(8080, "localhost", "/the_uri").onComplete(onSuccess(ws -> {
        Buffer content = Buffer.buffer();
        Observable<Buffer> observable = ws.toObservable();
        observable.forEach(content::appendBuffer, err -> fail(), () -> {
          server.close();
          assertEquals("some_content", content.toString("UTF-8"));
          testComplete();
        });
      }));
    });
    await();
  }

  @Test
  public void testWebsocketClientFlatMap() {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(8080));
    server.webSocketHandler(ws -> {
      ws.write(Buffer.buffer("some_content"));
      ws.close();
    });
    server.listen().onComplete(ar -> {
      WebSocketClient client = vertx.createWebSocketClient(new WebSocketClientOptions());
      Buffer content = Buffer.buffer();
      client.
          rxConnect(8080, "localhost", "/the_uri").
          flatMapObservable(WebSocket::toObservable).
          forEach(content::appendBuffer, err -> fail(), () -> {
            server.close();
            assertEquals("some_content", content.toString("UTF-8"));
            testComplete();
          });
    });
    await();
  }

  @Test
  public void testDeployVerticle() throws Exception {
    CountDownLatch deployLatch = new CountDownLatch(2);
    io.vertx.rxjava.core.RxHelper.deployVerticle(vertx, new AbstractVerticle() {
      @Override
      public void start() {
        deployLatch.countDown();
      }
    }).subscribe(resp -> {
      deployLatch.countDown();
    });
    awaitLatch(deployLatch);
  }

  @Test
  public void testRecordParser() {
    Single<AsyncFile> source = vertx.fileSystem().rxOpen("src/test/resources/test.txt", new OpenOptions());

    waitFor(5);

    source.map(file -> RecordParser.newDelimited("\n", file.toObservable()))
      .flatMapObservable(RecordParser::toObservable)
      .doOnNext(v -> complete())
      .doOnCompleted(() -> complete())
      .ignoreElements()
      .toCompletable()
      .subscribe(() -> complete(), this::fail);

    await();
  }
}
