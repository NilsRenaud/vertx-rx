package io.vertx.rxjava3.test;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.codegen.rxjava3.MethodWithCompletable;
import io.vertx.rxjava3.codegen.rxjava3.MethodWithMaybeString;
import io.vertx.rxjava3.codegen.rxjava3.MethodWithSingleString;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

public class AsyncResultAdapterTest extends VertxTestBase {

  @Test
  public void testSingleReportingSubscribeUncheckedException() {
    RuntimeException cause = new RuntimeException();
    MethodWithSingleString meth = new MethodWithSingleString(() -> {
      throw cause;
    });
    Single<String> single = meth.doSomethingWithResult();
    single.subscribe(result -> fail(), err -> testComplete());
    await();
  }

  @Test
  public void testMaybeReportingSubscribeUncheckedException() {
    RuntimeException cause = new RuntimeException();
    MethodWithMaybeString meth = new MethodWithMaybeString(() -> {
      throw cause;
    });
    Maybe<String> single = meth.doSomethingWithMaybeResult();
    single.subscribe(result -> fail(), err -> testComplete(), this::fail);
    await();
  }

  @Test
  public void testCompletableReportingSubscribeUncheckedException() {
    RuntimeException cause = new RuntimeException();
    MethodWithCompletable meth = new MethodWithCompletable(() -> {
      throw cause;
    });
    Completable single = meth.doSomethingWithResult();
    single.subscribe(this::fail, err -> testComplete());
    await();
  }
}
