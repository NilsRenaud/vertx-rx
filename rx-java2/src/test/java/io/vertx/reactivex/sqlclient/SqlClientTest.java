/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.reactivex.sqlclient;

import io.reactivex.Flowable;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.test.core.VertxTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Thomas Segismont
 */
public class SqlClientTest extends VertxTestBase {

  protected static final List<String> NAMES = Arrays.asList("John", "Paul", "Peter", "Andrew", "Peter", "Steven");

  private static PostgreSQLContainer container;

  protected PgPool pool;

  @BeforeClass
  public static void startPg() {
    container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:10.10"))
      .withDatabaseName("postgres")
      .withUsername("postgres")
      .withPassword("postgres");
    // new EnvironmentAndSystemPropertyClientProviderStrategy();
    container.start();
  }

  @AfterClass
  public static void stopPg() {
    container.stop();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    PgConnectOptions connectOptions = new PgConnectOptions();
    connectOptions.setHost(container.getHost());
    connectOptions.setPort(container.getMappedPort(5432));
    connectOptions.setDatabase(container.getDatabaseName());
    connectOptions.setUser(container.getUsername());
    connectOptions.setPassword(container.getPassword());
    pool = PgPool.newInstance(io.vertx.pgclient.PgPool.pool(connectOptions, new PoolOptions()));
    pool
      .query("drop table if exists folks")
      .rxExecute()
      .flatMap(res -> pool.query("create table folks (firstname varchar(255) not null)").rxExecute())
      .flatMap(res -> pool.preparedQuery("insert into folks (firstname) values ($1)").rxExecuteBatch(NAMES.stream().map(Tuple::of).collect(Collectors.toList())))
      .blockingGet();
  }

  @Override
  public void tearDown() throws Exception {
    pool.close();
  }

  @Test
  public void testStream() {
    Flowable<Row> flowable = pool.rxBegin()
      .flatMapPublisher(tx -> tx
        .rxPrepare("SELECT * FROM folks")
        .flatMapPublisher(pq -> pq.createStream(2).toFlowable())
        .doAfterTerminate(tx::commit));
    List<String> list = Collections.synchronizedList(new ArrayList<>());
    flowable.subscribe(row -> {
      list.add(row.getString(0));
    }, this::fail, () -> {
      assertEquals(list, NAMES);
      testComplete();
    });
    await();
  }
}
