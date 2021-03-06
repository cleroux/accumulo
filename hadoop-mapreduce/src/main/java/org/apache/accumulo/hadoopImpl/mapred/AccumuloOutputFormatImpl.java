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
package org.apache.accumulo.hadoopImpl.mapred;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.MultiTableBatchWriter;
import org.apache.accumulo.core.client.MutationsRejectedException;
import org.apache.accumulo.core.client.TableExistsException;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.client.security.SecurityErrorCode;
import org.apache.accumulo.core.clientImpl.ClientInfo;
import org.apache.accumulo.core.data.ColumnUpdate;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.TabletId;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.accumulo.hadoop.mapred.AccumuloOutputFormat;
import org.apache.accumulo.hadoopImpl.mapreduce.lib.OutputConfigurator;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.mapred.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class allows MapReduce jobs to use Accumulo as the sink for data. This {@link OutputFormat}
 * accepts keys and values of type {@link Text} (for a table name) and {@link Mutation} from the Map
 * and Reduce functions.
 *
 * The user must specify the following via static configurator methods:
 *
 * <ul>
 * <li>{@link AccumuloOutputFormatImpl#setClientInfo(JobConf, ClientInfo)}
 * </ul>
 *
 * Other static methods are optional.
 */
public class AccumuloOutputFormatImpl {

  private static final Class<?> CLASS = AccumuloOutputFormat.class;
  private static final Logger log = LoggerFactory.getLogger(CLASS);

  /**
   * Set the connection information needed to communicate with Accumulo in this job.
   *
   * @param job
   *          Hadoop job to be configured
   * @param info
   *          Accumulo connection information
   * @since 2.0.0
   */
  public static void setClientInfo(JobConf job, ClientInfo info) {
    ClientInfo outInfo = OutputConfigurator.updateToken(job.getCredentials(), info);
    OutputConfigurator.setClientInfo(CLASS, job, outInfo);
  }

  /**
   * Get the connection information needed to communication with Accumulo
   *
   * @param job
   *          Hadoop job to be configured
   * @since 2.0.0
   */
  public static ClientInfo getClientInfo(JobConf job) {
    return OutputConfigurator.getClientInfo(CLASS, job);
  }

  /**
   * Set Accumulo client properties file used to connect to Accumulo
   *
   * @param job
   *          Hadoop job to be configured
   * @param clientPropsFile
   *          URL (hdfs:// or http://) to Accumulo client properties file
   * @since 2.0.0
   */
  protected static void setClientPropertiesFile(JobConf job, String clientPropsFile) {
    OutputConfigurator.setClientPropertiesFile(CLASS, job, clientPropsFile);
  }

  /**
   * Sets the default table name to use if one emits a null in place of a table name for a given
   * mutation. Table names can only be alpha-numeric and underscores.
   *
   * @param job
   *          the Hadoop job instance to be configured
   * @param tableName
   *          the table to use when the tablename is null in the write call
   * @since 1.5.0
   */
  public static void setDefaultTableName(JobConf job, String tableName) {
    OutputConfigurator.setDefaultTableName(CLASS, job, tableName);
  }

  /**
   * Gets the default table name from the configuration.
   *
   * @param job
   *          the Hadoop context for the configured job
   * @return the default table name
   * @since 1.5.0
   * @see #setDefaultTableName(JobConf, String)
   */
  protected static String getDefaultTableName(JobConf job) {
    return OutputConfigurator.getDefaultTableName(CLASS, job);
  }

  /**
   * Sets the directive to create new tables, as necessary. Table names can only be alpha-numeric
   * and underscores.
   *
   * <p>
   * By default, this feature is <b>disabled</b>.
   *
   * @param job
   *          the Hadoop job instance to be configured
   * @param enableFeature
   *          the feature is enabled if true, disabled otherwise
   * @since 1.5.0
   */
  public static void setCreateTables(JobConf job, boolean enableFeature) {
    OutputConfigurator.setCreateTables(CLASS, job, enableFeature);
  }

  /**
   * Determines whether tables are permitted to be created as needed.
   *
   * @param job
   *          the Hadoop context for the configured job
   * @return true if the feature is disabled, false otherwise
   * @since 1.5.0
   * @see #setCreateTables(JobConf, boolean)
   */
  protected static Boolean canCreateTables(JobConf job) {
    return OutputConfigurator.canCreateTables(CLASS, job);
  }

  /**
   * Sets the directive to use simulation mode for this job. In simulation mode, no output is
   * produced. This is useful for testing.
   *
   * <p>
   * By default, this feature is <b>disabled</b>.
   *
   * @param job
   *          the Hadoop job instance to be configured
   * @param enableFeature
   *          the feature is enabled if true, disabled otherwise
   * @since 1.5.0
   */
  public static void setSimulationMode(JobConf job, boolean enableFeature) {
    OutputConfigurator.setSimulationMode(CLASS, job, enableFeature);
  }

  /**
   * Determines whether this feature is enabled.
   *
   * @param job
   *          the Hadoop context for the configured job
   * @return true if the feature is enabled, false otherwise
   * @since 1.5.0
   * @see #setSimulationMode(JobConf, boolean)
   */
  protected static Boolean getSimulationMode(JobConf job) {
    return OutputConfigurator.getSimulationMode(CLASS, job);
  }

  /**
   * A base class to be used to create {@link RecordWriter} instances that write to Accumulo.
   */
  public static class AccumuloRecordWriter implements RecordWriter<Text,Mutation> {
    private MultiTableBatchWriter mtbw = null;
    private HashMap<Text,BatchWriter> bws = null;
    private Text defaultTableName = null;

    private boolean simulate = false;
    private boolean createTables = false;

    private long mutCount = 0;
    private long valCount = 0;

    private AccumuloClient client;

    public AccumuloRecordWriter(JobConf job) {
      this.simulate = getSimulationMode(job);
      this.createTables = canCreateTables(job);

      if (simulate)
        log.info("Simulating output only. No writes to tables will occur");

      this.bws = new HashMap<>();

      String tname = getDefaultTableName(job);
      this.defaultTableName = (tname == null) ? null : new Text(tname);

      if (!simulate) {
        this.client = Accumulo.newClient().from(getClientInfo(job).getProperties()).build();
        mtbw = client.createMultiTableBatchWriter();
      }
    }

    /**
     * Push a mutation into a table. If table is null, the defaultTable will be used. If
     * {@link AccumuloOutputFormatImpl#canCreateTables(JobConf)} is set, the table will be created
     * if it does not exist. The table name must only contain alphanumerics and underscore.
     */
    @Override
    public void write(Text table, Mutation mutation) throws IOException {
      if (table == null || table.toString().isEmpty())
        table = this.defaultTableName;

      if (!simulate && table == null)
        throw new IOException("No table or default table specified. Try simulation mode next time");

      ++mutCount;
      valCount += mutation.size();
      printMutation(table, mutation);

      if (simulate)
        return;

      if (!bws.containsKey(table))
        try {
          addTable(table);
        } catch (final Exception e) {
          log.error("Could not add table '" + table + "'", e);
          throw new IOException(e);
        }

      try {
        bws.get(table).addMutation(mutation);
      } catch (MutationsRejectedException e) {
        throw new IOException(e);
      }
    }

    protected void addTable(Text tableName) throws AccumuloException, AccumuloSecurityException {
      if (simulate) {
        log.info("Simulating adding table: " + tableName);
        return;
      }

      log.debug("Adding table: " + tableName);
      BatchWriter bw = null;
      String table = tableName.toString();

      if (createTables && !client.tableOperations().exists(table)) {
        try {
          client.tableOperations().create(table);
        } catch (AccumuloSecurityException e) {
          log.error("Accumulo security violation creating " + table, e);
          throw e;
        } catch (TableExistsException e) {
          // Shouldn't happen
        }
      }

      try {
        bw = mtbw.getBatchWriter(table);
      } catch (TableNotFoundException e) {
        log.error("Accumulo table " + table + " doesn't exist and cannot be created.", e);
        throw new AccumuloException(e);
      } catch (AccumuloException | AccumuloSecurityException e) {
        throw e;
      }

      if (bw != null)
        bws.put(tableName, bw);
    }

    private int printMutation(Text table, Mutation m) {
      if (log.isTraceEnabled()) {
        log.trace(String.format("Table %s row key: %s", table, hexDump(m.getRow())));
        for (ColumnUpdate cu : m.getUpdates()) {
          log.trace(String.format("Table %s column: %s:%s", table, hexDump(cu.getColumnFamily()),
              hexDump(cu.getColumnQualifier())));
          log.trace(String.format("Table %s security: %s", table,
              new ColumnVisibility(cu.getColumnVisibility()).toString()));
          log.trace(String.format("Table %s value: %s", table, hexDump(cu.getValue())));
        }
      }
      return m.getUpdates().size();
    }

    private String hexDump(byte[] ba) {
      StringBuilder sb = new StringBuilder();
      for (byte b : ba) {
        if ((b > 0x20) && (b < 0x7e))
          sb.append((char) b);
        else
          sb.append(String.format("x%02x", b));
      }
      return sb.toString();
    }

    @Override
    public void close(Reporter reporter) throws IOException {
      log.debug("mutations written: " + mutCount + ", values written: " + valCount);
      if (simulate)
        return;

      try {
        mtbw.close();
      } catch (MutationsRejectedException e) {
        if (e.getSecurityErrorCodes().size() >= 0) {
          HashMap<String,Set<SecurityErrorCode>> tables = new HashMap<>();
          for (Entry<TabletId,Set<SecurityErrorCode>> ke : e.getSecurityErrorCodes().entrySet()) {
            String tableId = ke.getKey().getTableId().toString();
            Set<SecurityErrorCode> secCodes = tables.get(tableId);
            if (secCodes == null) {
              secCodes = new HashSet<>();
              tables.put(tableId, secCodes);
            }
            secCodes.addAll(ke.getValue());
          }

          log.error("Not authorized to write to tables : " + tables);
        }

        if (e.getConstraintViolationSummaries().size() > 0) {
          log.error("Constraint violations : " + e.getConstraintViolationSummaries().size());
        }
        throw new IOException(e);
      } finally {
        client.close();
      }
    }
  }

}
