/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.core.file.blockfile.cache.lru;

import org.apache.accumulo.core.conf.AccumuloConfiguration;
import org.apache.accumulo.core.file.blockfile.cache.BlockCacheConfiguration;
import org.apache.accumulo.core.file.blockfile.cache.CacheType;

public final class LruBlockCacheConfiguration extends BlockCacheConfiguration {

  public static final String PROPERTY_PREFIX = "lru";

  /** Default Configuration Parameters */

  /** Backing Concurrent Map Configuration */
  public static final Float DEFAULT_LOAD_FACTOR = 0.75f;
  public static final Integer DEFAULT_CONCURRENCY_LEVEL = 16;

  /** Eviction thresholds */
  public static final Float DEFAULT_MIN_FACTOR = 0.75f;
  public static final Float DEFAULT_ACCEPTABLE_FACTOR = 0.85f;

  /** Priority buckets */
  public static final Float DEFAULT_SINGLE_FACTOR = 0.25f;
  public static final Float DEFAULT_MULTI_FACTOR = 0.50f;
  public static final Float DEFAULT_MEMORY_FACTOR = 0.25f;

  // property names
  public static final String ACCEPTABLE_FACTOR_PROPERTY = "acceptable.factor";
  public static final String MIN_FACTOR_PROPERTY = "min.factor";
  public static final String SINGLE_FACTOR_PROPERTY = "single.factor";
  public static final String MULTI_FACTOR_PROPERTY = "multi.factor";
  public static final String MEMORY_FACTOR_PROPERTY = "memory.factor";
  public static final String MAP_LOAD_PROPERTY = "map.load";
  public static final String MAP_CONCURRENCY_PROPERTY = "map.concurrency";
  public static final String EVICTION_THREAD_PROPERTY = "eviction.thread";

  /** Acceptable size of cache (no evictions if size < acceptable) */
  private final float acceptableFactor;

  /** Minimum threshold of cache (when evicting, evict until size < min) */
  private final float minFactor;

  /** Single access bucket size */
  private final float singleFactor;

  /** Multiple access bucket size */
  private final float multiFactor;

  /** In-memory bucket size */
  private final float memoryFactor;

  /** LruBlockCache cache = new LruBlockCache **/
  private final float mapLoadFactor;

  /** LruBlockCache cache = new LruBlockCache **/
  private final int mapConcurrencyLevel;

  private final boolean useEvictionThread;

  public LruBlockCacheConfiguration(AccumuloConfiguration conf, CacheType type) {
    super(conf, type, PROPERTY_PREFIX);

    this.acceptableFactor = this.getHelper().get(ACCEPTABLE_FACTOR_PROPERTY).map(Float::valueOf).filter(f -> f > 0).orElse(DEFAULT_ACCEPTABLE_FACTOR);
    this.minFactor = this.getHelper().get(MIN_FACTOR_PROPERTY).map(Float::valueOf).filter(f -> f > 0).orElse(DEFAULT_MIN_FACTOR);
    this.singleFactor = this.getHelper().get(SINGLE_FACTOR_PROPERTY).map(Float::valueOf).filter(f -> f > 0).orElse(DEFAULT_SINGLE_FACTOR);
    this.multiFactor = this.getHelper().get(MULTI_FACTOR_PROPERTY).map(Float::valueOf).filter(f -> f > 0).orElse(DEFAULT_MULTI_FACTOR);
    this.memoryFactor = this.getHelper().get(MEMORY_FACTOR_PROPERTY).map(Float::valueOf).filter(f -> f > 0).orElse(DEFAULT_MEMORY_FACTOR);
    this.mapLoadFactor = this.getHelper().get(MAP_LOAD_PROPERTY).map(Float::valueOf).filter(f -> f > 0).orElse(DEFAULT_LOAD_FACTOR);
    this.mapConcurrencyLevel = this.getHelper().get(MAP_CONCURRENCY_PROPERTY).map(Integer::valueOf).filter(i -> i > 0).orElse(DEFAULT_CONCURRENCY_LEVEL);
    this.useEvictionThread = this.getHelper().get(EVICTION_THREAD_PROPERTY).map(Boolean::valueOf).orElse(true);

    if (this.getSingleFactor() + this.getMultiFactor() + this.getMemoryFactor() != 1) {
      throw new IllegalArgumentException("Single, multi, and memory factors " + " should total 1.0");
    }
    if (this.getMinFactor() >= this.getAcceptableFactor()) {
      throw new IllegalArgumentException("minFactor must be smaller than acceptableFactor");
    }
    if (this.getMinFactor() >= 1.0f || this.getAcceptableFactor() >= 1.0f) {
      throw new IllegalArgumentException("all factors must be < 1");
    }
  }

  public float getAcceptableFactor() {
    return acceptableFactor;
  }

  public float getMinFactor() {
    return minFactor;
  }

  public float getSingleFactor() {
    return singleFactor;
  }

  public float getMultiFactor() {
    return multiFactor;
  }

  public float getMemoryFactor() {
    return memoryFactor;
  }

  public float getMapLoadFactor() {
    return mapLoadFactor;
  }

  public int getMapConcurrencyLevel() {
    return mapConcurrencyLevel;
  }

  public boolean isUseEvictionThread() {
    return useEvictionThread;
  }

  @Override
  public String toString() {
    return super.toString() + ", acceptableFactor: " + this.getAcceptableFactor() + ", minFactor: " + this.getMinFactor() + ", singleFactor: "
        + this.getSingleFactor() + ", multiFactor: " + this.getMultiFactor() + ", memoryFactor: " + this.getMemoryFactor() + ", mapLoadFactor: "
        + this.getMapLoadFactor() + ", mapConcurrencyLevel: " + this.getMapConcurrencyLevel() + ", useEvictionThread: " + this.isUseEvictionThread();
  }

}