/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.test;

import static org.apache.accumulo.fate.util.UtilWaitThread.sleepUninterruptibly;
import static org.junit.Assert.assertEquals;

import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.BatchWriterConfig;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.conf.ClientProperty;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.metadata.MetadataTable;
import org.apache.accumulo.core.metadata.RootTable;
import org.apache.accumulo.core.metadata.schema.MetadataSchema;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.harness.AccumuloClusterHarness;
import org.apache.accumulo.minicluster.ServerType;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloConfigImpl;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterators;

public class CleanWalIT extends AccumuloClusterHarness {
  private static final Logger log = LoggerFactory.getLogger(CleanWalIT.class);

  @Override
  public int defaultTimeoutSeconds() {
    return 4 * 60;
  }

  @Override
  public void configureMiniCluster(MiniAccumuloConfigImpl cfg, Configuration hadoopCoreSite) {
    cfg.setProperty(Property.INSTANCE_ZK_TIMEOUT, "15s");
    cfg.setClientProperty(ClientProperty.INSTANCE_ZOOKEEPERS_TIMEOUT, "15s");
    cfg.setNumTservers(1);
    // use raw local file system so walogs sync and flush will work
    hadoopCoreSite.set("fs.file.impl", RawLocalFileSystem.class.getName());
  }

  @Before
  public void offlineTraceTable() throws Exception {
    try (AccumuloClient client = createAccumuloClient()) {
      String traceTable = client.instanceOperations().getSystemConfiguration()
          .get(Property.TRACE_TABLE.getKey());
      if (client.tableOperations().exists(traceTable)) {
        client.tableOperations().offline(traceTable, true);
      }
    }
  }

  @After
  public void onlineTraceTable() throws Exception {
    if (cluster != null) {
      try (AccumuloClient client = createAccumuloClient()) {
        String traceTable = client.instanceOperations().getSystemConfiguration()
            .get(Property.TRACE_TABLE.getKey());
        if (client.tableOperations().exists(traceTable)) {
          client.tableOperations().online(traceTable, true);
        }
      }
    }
  }

  // test for ACCUMULO-1830
  @Test
  public void test() throws Exception {
    try (AccumuloClient client = createAccumuloClient()) {
      String tableName = getUniqueNames(1)[0];
      client.tableOperations().create(tableName);
      BatchWriter bw = client.createBatchWriter(tableName, new BatchWriterConfig());
      Mutation m = new Mutation("row");
      m.put("cf", "cq", "value");
      bw.addMutation(m);
      bw.close();
      getCluster().getClusterControl().stopAllServers(ServerType.TABLET_SERVER);
      // all 3 tables should do recovery, but the bug doesn't really remove the log file references

      getCluster().getClusterControl().startAllServers(ServerType.TABLET_SERVER);

      for (String table : new String[] {MetadataTable.NAME, RootTable.NAME})
        client.tableOperations().flush(table, null, null, true);
      log.debug("Checking entries for {}", tableName);
      assertEquals(1, count(tableName, client));
      for (String table : new String[] {MetadataTable.NAME, RootTable.NAME}) {
        log.debug("Checking logs for {}", table);
        assertEquals("Found logs for " + table, 0, countLogs(table, client));
      }

      bw = client.createBatchWriter(tableName, new BatchWriterConfig());
      m = new Mutation("row");
      m.putDelete("cf", "cq");
      bw.addMutation(m);
      bw.close();
      assertEquals(0, count(tableName, client));
      client.tableOperations().flush(tableName, null, null, true);
      client.tableOperations().flush(MetadataTable.NAME, null, null, true);
      client.tableOperations().flush(RootTable.NAME, null, null, true);
      try {
        getCluster().getClusterControl().stopAllServers(ServerType.TABLET_SERVER);
        sleepUninterruptibly(3, TimeUnit.SECONDS);
      } finally {
        getCluster().getClusterControl().startAllServers(ServerType.TABLET_SERVER);
      }
      assertEquals(0, count(tableName, client));
    }
  }

  private int countLogs(String tableName, AccumuloClient client) throws TableNotFoundException {
    int count = 0;
    try (Scanner scanner = client.createScanner(MetadataTable.NAME, Authorizations.EMPTY)) {
      scanner.fetchColumnFamily(MetadataSchema.TabletsSection.LogColumnFamily.NAME);
      scanner.setRange(MetadataSchema.TabletsSection.getRange());
      for (Entry<Key,Value> entry : scanner) {
        log.debug("Saw {}={}", entry.getKey(), entry.getValue());
        count++;
      }
    }
    return count;
  }

  int count(String tableName, AccumuloClient client) throws Exception {
    try (Scanner s = client.createScanner(tableName, Authorizations.EMPTY)) {
      return Iterators.size(s.iterator());
    }
  }
}
