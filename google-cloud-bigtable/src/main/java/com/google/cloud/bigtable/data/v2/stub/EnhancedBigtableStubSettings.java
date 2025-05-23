/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.bigtable.data.v2.stub;

import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.api.gax.batching.BatchingCallSettings;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.batching.FlowController.LimitExceededBehavior;
import com.google.api.gax.core.GoogleCredentialsProvider;
import com.google.api.gax.grpc.ChannelPoolSettings;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.ServerStreamingCallSettings;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.StubSettings;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.auth.Credentials;
import com.google.bigtable.v2.FeatureFlags;
import com.google.bigtable.v2.PingAndWarmRequest;
import com.google.cloud.bigtable.Version;
import com.google.cloud.bigtable.data.v2.internal.PrepareQueryRequest;
import com.google.cloud.bigtable.data.v2.internal.PrepareResponse;
import com.google.cloud.bigtable.data.v2.internal.SqlRow;
import com.google.cloud.bigtable.data.v2.models.ChangeStreamRecord;
import com.google.cloud.bigtable.data.v2.models.ConditionalRowMutation;
import com.google.cloud.bigtable.data.v2.models.KeyOffset;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.bigtable.data.v2.models.Range.ByteStringRange;
import com.google.cloud.bigtable.data.v2.models.ReadChangeStreamQuery;
import com.google.cloud.bigtable.data.v2.models.ReadModifyWriteRow;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowMutation;
import com.google.cloud.bigtable.data.v2.models.sql.BoundStatement;
import com.google.cloud.bigtable.data.v2.stub.metrics.DefaultMetricsProvider;
import com.google.cloud.bigtable.data.v2.stub.metrics.MetricsProvider;
import com.google.cloud.bigtable.data.v2.stub.metrics.Util;
import com.google.cloud.bigtable.data.v2.stub.mutaterows.MutateRowsBatchingDescriptor;
import com.google.cloud.bigtable.data.v2.stub.readrows.ReadRowsBatchingDescriptor;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

/**
 * Settings class to configure an instance of {@link EnhancedBigtableStub}.
 *
 * <p>Sane defaults are provided for most settings:
 *
 * <ul>
 *   <li>The default service address (bigtable.googleapis.com) and default port (443) are used.
 *   <li>Credentials are acquired automatically through Application Default Credentials.
 *   <li>Retries are configured for idempotent methods but not for non-idempotent methods.
 * </ul>
 *
 * <p>The only required setting is the instance name.
 *
 * <p>The builder of this class is recursive, so contained classes are themselves builders. When
 * build() is called, the tree of builders is called to create the complete settings object.
 *
 * <pre>{@code
 * BigtableDataSettings.Builder settingsBuilder = BigtableDataSettings.newBuilder()
 *   .setProjectId("my-project-id")
 *   .setInstanceId("my-instance-id")
 *   .setAppProfileId("default");
 *
 * settingsBuilder.stubSettings().readRowsSettings()
 *  .setRetryableCodes(Code.DEADLINE_EXCEEDED, Code.UNAVAILABLE);
 *
 * BigtableDataSettings settings = builder.build();
 * }</pre>
 */
public class EnhancedBigtableStubSettings extends StubSettings<EnhancedBigtableStubSettings> {
  private static final Logger logger =
      Logger.getLogger(EnhancedBigtableStubSettings.class.getName());

  // The largest message that can be received is a 256 MB ReadRowsResponse.
  private static final int MAX_MESSAGE_SIZE = 256 * 1024 * 1024;
  private static final String SERVER_DEFAULT_APP_PROFILE_ID = "";

  // TODO(meeral-k): add documentation
  private static final boolean DIRECT_PATH_ENABLED =
      Boolean.parseBoolean(System.getenv("CBT_ENABLE_DIRECTPATH"));

  private static final boolean SKIP_TRAILERS =
      Optional.ofNullable(System.getenv("CBT_SKIP_HEADERS"))
          .map(Boolean::parseBoolean)
          .orElse(true);

  private static final Set<Code> IDEMPOTENT_RETRY_CODES =
      ImmutableSet.of(Code.DEADLINE_EXCEEDED, Code.UNAVAILABLE);

  // Copy of default retrying settings in the yaml
  private static final RetrySettings IDEMPOTENT_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(10))
          .setRetryDelayMultiplier(2)
          .setMaxRetryDelay(Duration.ofMinutes(1))
          .setInitialRpcTimeout(Duration.ofSeconds(20))
          .setRpcTimeoutMultiplier(1.0)
          .setMaxRpcTimeout(Duration.ofSeconds(20))
          .setTotalTimeout(Duration.ofMinutes(10))
          .build();

  // Allow retrying ABORTED statuses. These will be returned by the server when the client is
  // too slow to read the rows. This makes sense for the java client because retries happen
  // after the row merging logic. Which means that the retry will not be invoked until the
  // current buffered chunks are consumed.
  private static final Set<Code> READ_ROWS_RETRY_CODES =
      ImmutableSet.<Code>builder().addAll(IDEMPOTENT_RETRY_CODES).add(Code.ABORTED).build();

  // Priming request should have a shorter timeout
  private static Duration PRIME_REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private static final RetrySettings READ_ROWS_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(10))
          .setRetryDelayMultiplier(2.0)
          .setMaxRetryDelay(Duration.ofMinutes(1))
          .setMaxAttempts(10)
          .setJittered(true)
          .setInitialRpcTimeout(Duration.ofMinutes(30))
          .setRpcTimeoutMultiplier(2.0)
          .setMaxRpcTimeout(Duration.ofMinutes(30))
          .setTotalTimeout(Duration.ofHours(12))
          .build();

  private static final RetrySettings MUTATE_ROWS_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(10))
          .setRetryDelayMultiplier(2)
          .setMaxRetryDelay(Duration.ofMinutes(1))
          .setInitialRpcTimeout(Duration.ofMinutes(1))
          .setRpcTimeoutMultiplier(1.0)
          .setMaxRpcTimeout(Duration.ofMinutes(1))
          .setTotalTimeout(Duration.ofMinutes(10))
          .build();

  private static final Set<Code> GENERATE_INITIAL_CHANGE_STREAM_PARTITIONS_RETRY_CODES =
      ImmutableSet.<Code>builder().addAll(IDEMPOTENT_RETRY_CODES).add(Code.ABORTED).build();

  private static final RetrySettings GENERATE_INITIAL_CHANGE_STREAM_PARTITIONS_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(10))
          .setRetryDelayMultiplier(2.0)
          .setMaxRetryDelay(Duration.ofMinutes(1))
          .setMaxAttempts(10)
          .setJittered(true)
          .setInitialRpcTimeout(Duration.ofMinutes(1))
          .setRpcTimeoutMultiplier(2.0)
          .setMaxRpcTimeout(Duration.ofMinutes(10))
          .setTotalTimeout(Duration.ofMinutes(60))
          .build();

  // Allow retrying ABORTED statuses. These will be returned by the server when the client is
  // too slow to read the change stream records. This makes sense for the java client because
  // retries happen after the mutation merging logic. Which means that the retry will not be
  // invoked until the current buffered change stream mutations are consumed.
  private static final Set<Code> READ_CHANGE_STREAM_RETRY_CODES =
      ImmutableSet.<Code>builder().addAll(IDEMPOTENT_RETRY_CODES).add(Code.ABORTED).build();

  private static final RetrySettings READ_CHANGE_STREAM_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(10))
          .setRetryDelayMultiplier(2.0)
          .setMaxRetryDelay(Duration.ofMinutes(1))
          .setMaxAttempts(10)
          .setJittered(true)
          .setInitialRpcTimeout(Duration.ofMinutes(5))
          .setRpcTimeoutMultiplier(2.0)
          .setMaxRpcTimeout(Duration.ofMinutes(5))
          .setTotalTimeout(Duration.ofHours(12))
          .build();

  // Allow retrying ABORTED statuses. These will be returned by the server when the client is
  // too slow to read the responses.
  private static final Set<Code> EXECUTE_QUERY_RETRY_CODES =
      ImmutableSet.<Code>builder().addAll(IDEMPOTENT_RETRY_CODES).add(Code.ABORTED).build();

  // We use the same configuration as READ_ROWS
  private static final RetrySettings EXECUTE_QUERY_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(10))
          .setRetryDelayMultiplier(2.0)
          .setMaxRetryDelay(Duration.ofMinutes(1))
          .setMaxAttempts(10)
          .setJittered(true)
          .setInitialRpcTimeout(Duration.ofMinutes(30))
          .setRpcTimeoutMultiplier(1.0)
          .setMaxRpcTimeout(Duration.ofMinutes(30))
          .setTotalTimeout(Duration.ofHours(12))
          .build();

  // Similar to IDEMPOTENT but with a lower initial rpc timeout since we expect
  // these calls to be quick in most circumstances
  private static final RetrySettings PREPARE_QUERY_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(10))
          .setRetryDelayMultiplier(2)
          .setMaxRetryDelay(Duration.ofMinutes(1))
          .setInitialRpcTimeout(Duration.ofSeconds(5))
          .setRpcTimeoutMultiplier(1.0)
          .setMaxRpcTimeout(Duration.ofSeconds(20))
          .setTotalTimeout(Duration.ofMinutes(10))
          .build();

  /**
   * Scopes that are equivalent to JWT's audience.
   *
   * <p>When the credentials provider contains any of these scopes (default behavior) and the
   * application default credentials point to a service account, then OAuth2 tokens will be replaced
   * with JWT tokens. This removes the need for access token refreshes.
   */
  private static final ImmutableList<String> JWT_ENABLED_SCOPES =
      ImmutableList.<String>builder()
          .add("https://www.googleapis.com/auth/bigtable.data")
          .add("https://www.googleapis.com/auth/cloud-bigtable.data")
          .add("https://www.googleapis.com/auth/cloud-platform")
          .build();

  /**
   * Default jwt audience is always the service name unless it's override to test / staging for
   * testing.
   */
  private static final String DEFAULT_DATA_JWT_AUDIENCE = "https://bigtable.googleapis.com/";

  private final String projectId;
  private final String instanceId;
  private final String appProfileId;
  private final boolean isRefreshingChannel;
  private ImmutableList<String> primedTableIds;
  private final boolean enableRoutingCookie;
  private final boolean enableRetryInfo;
  private final boolean enableSkipTrailers;

  private final ServerStreamingCallSettings<Query, Row> readRowsSettings;
  private final UnaryCallSettings<Query, Row> readRowSettings;
  private final UnaryCallSettings<String, List<KeyOffset>> sampleRowKeysSettings;
  private final UnaryCallSettings<RowMutation, Void> mutateRowSettings;
  private final BigtableBatchingCallSettings bulkMutateRowsSettings;
  private final BigtableBulkReadRowsCallSettings bulkReadRowsSettings;
  private final UnaryCallSettings<ConditionalRowMutation, Boolean> checkAndMutateRowSettings;
  private final UnaryCallSettings<ReadModifyWriteRow, Row> readModifyWriteRowSettings;
  private final ServerStreamingCallSettings<String, ByteStringRange>
      generateInitialChangeStreamPartitionsSettings;
  private final ServerStreamingCallSettings<ReadChangeStreamQuery, ChangeStreamRecord>
      readChangeStreamSettings;
  private final UnaryCallSettings<PingAndWarmRequest, Void> pingAndWarmSettings;
  private final ServerStreamingCallSettings<BoundStatement, SqlRow> executeQuerySettings;
  private final UnaryCallSettings<PrepareQueryRequest, PrepareResponse> prepareQuerySettings;

  private final FeatureFlags featureFlags;

  private final MetricsProvider metricsProvider;
  @Nullable private final String metricsEndpoint;
  @Nonnull private final InternalMetricsProvider internalMetricsProvider;
  private final String jwtAudience;

  private EnhancedBigtableStubSettings(Builder builder) {
    super(builder);

    // Since point reads, streaming reads, bulk reads share the same base callable that converts
    // grpc errors into ApiExceptions, they must have the same retry codes.
    Preconditions.checkState(
        builder
            .readRowSettings
            .getRetryableCodes()
            .equals(builder.readRowsSettings.getRetryableCodes()),
        "Single ReadRow retry codes must match ReadRows retry codes");
    Preconditions.checkState(
        builder
            .bulkReadRowsSettings
            .getRetryableCodes()
            .equals(builder.readRowsSettings.getRetryableCodes()),
        "Bulk ReadRow retry codes must match ReadRows retry codes");

    projectId = builder.projectId;
    instanceId = builder.instanceId;
    appProfileId = builder.appProfileId;
    isRefreshingChannel = builder.isRefreshingChannel;
    primedTableIds = builder.primedTableIds;
    enableRoutingCookie = builder.enableRoutingCookie;
    enableRetryInfo = builder.enableRetryInfo;
    enableSkipTrailers = builder.enableSkipTrailers;
    metricsProvider = builder.metricsProvider;
    metricsEndpoint = builder.metricsEndpoint;
    internalMetricsProvider = builder.internalMetricsProvider;
    jwtAudience = builder.jwtAudience;

    // Per method settings.
    readRowsSettings = builder.readRowsSettings.build();
    readRowSettings = builder.readRowSettings.build();
    sampleRowKeysSettings = builder.sampleRowKeysSettings.build();
    mutateRowSettings = builder.mutateRowSettings.build();
    bulkMutateRowsSettings = builder.bulkMutateRowsSettings.build();
    bulkReadRowsSettings = builder.bulkReadRowsSettings.build();
    checkAndMutateRowSettings = builder.checkAndMutateRowSettings.build();
    readModifyWriteRowSettings = builder.readModifyWriteRowSettings.build();
    generateInitialChangeStreamPartitionsSettings =
        builder.generateInitialChangeStreamPartitionsSettings.build();
    readChangeStreamSettings = builder.readChangeStreamSettings.build();
    pingAndWarmSettings = builder.pingAndWarmSettings.build();
    executeQuerySettings = builder.executeQuerySettings.build();
    prepareQuerySettings = builder.prepareQuerySettings.build();
    featureFlags = builder.featureFlags.build();
  }

  /** Create a new builder. */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** Returns the project id of the target instance. */
  public String getProjectId() {
    return projectId;
  }

  /** Returns the target instance id. */
  public String getInstanceId() {
    return instanceId;
  }

  /** Returns the configured AppProfile to use */
  public String getAppProfileId() {
    return appProfileId;
  }

  /**
   * Returns if channels will gracefully refresh connections to Cloud Bigtable service
   *
   * @deprecated Channel refreshing is enabled by default and this method will be deprecated.
   */
  @Deprecated
  public boolean isRefreshingChannel() {
    return isRefreshingChannel;
  }

  /**
   * @deprecated This field is ignored. If {@link #isRefreshingChannel()} is enabled, warm up
   *     requests will be sent to all table ids of the instance.
   */
  @Deprecated
  public List<String> getPrimedTableIds() {
    return primedTableIds;
  }

  /**
   * @deprecated This is a no op and will always return an empty map. Audience is always set to
   *     bigtable service name.
   */
  @InternalApi("Used for internal testing")
  @Deprecated
  public Map<String, String> getJwtAudienceMapping() {
    return ImmutableMap.of();
  }

  public MetricsProvider getMetricsProvider() {
    return metricsProvider;
  }

  /**
   * Gets if routing cookie is enabled. If true, client will retry a request with extra metadata
   * server sent back.
   */
  @BetaApi("Routing cookie is not currently stable and may change in the future")
  public boolean getEnableRoutingCookie() {
    return enableRoutingCookie;
  }

  /**
   * Gets if RetryInfo is enabled. If true, client bases retry decision and back off time on server
   * returned RetryInfo value. Otherwise, client uses {@link RetrySettings}.
   */
  @BetaApi("RetryInfo is not currently stable and may change in the future")
  public boolean getEnableRetryInfo() {
    return enableRetryInfo;
  }

  boolean getEnableSkipTrailers() {
    return enableSkipTrailers;
  }

  /**
   * Gets the Google Cloud Monitoring endpoint for publishing client side metrics. If it's null,
   * client will publish metrics to the default monitoring endpoint.
   */
  @Nullable
  public String getMetricsEndpoint() {
    return metricsEndpoint;
  }

  public boolean areInternalMetricsEnabled() {
    return internalMetricsProvider == DEFAULT_INTERNAL_OTEL_PROVIDER;
  }

  InternalMetricsProvider getInternalMetricsProvider() {
    return internalMetricsProvider;
  }

  /** Returns a builder for the default ChannelProvider for this service. */
  public static InstantiatingGrpcChannelProvider.Builder defaultGrpcTransportProviderBuilder() {
    InstantiatingGrpcChannelProvider.Builder grpcTransportProviderBuilder =
        BigtableStubSettings.defaultGrpcTransportProviderBuilder();
    if (DIRECT_PATH_ENABLED) {
      // Attempts direct access to CBT service over gRPC to improve throughput,
      // whether the attempt is allowed is totally controlled by service owner.
      grpcTransportProviderBuilder
          .setAttemptDirectPathXds()
          .setAttemptDirectPath(true)
          // Allow using non-default service account in DirectPath.
          .setAllowNonDefaultServiceAccount(true);
    }
    return grpcTransportProviderBuilder
        .setChannelPoolSettings(
            ChannelPoolSettings.builder()
                .setInitialChannelCount(10)
                .setMinRpcsPerChannel(1)
                .setMaxRpcsPerChannel(50)
                .setPreemptiveRefreshEnabled(true)
                .build())
        .setMaxInboundMessageSize(MAX_MESSAGE_SIZE)
        .setKeepAliveTime(Duration.ofSeconds(30)) // sends ping in this interval
        .setKeepAliveTimeout(
            Duration.ofSeconds(10)); // wait this long before considering the connection dead
  }

  @SuppressWarnings("WeakerAccess")
  public static TransportChannelProvider defaultTransportChannelProvider() {
    return defaultGrpcTransportProviderBuilder().build();
  }

  /** Returns a builder for the default credentials for this service. */
  public static GoogleCredentialsProvider.Builder defaultCredentialsProviderBuilder() {
    return BigtableStubSettings.defaultCredentialsProviderBuilder()
        .setJwtEnabledScopes(JWT_ENABLED_SCOPES);
  }

  @Override
  public String getServiceName() {
    return "bigtable";
  }

  /**
   * Returns the object with the settings used for calls to ReadRows.
   *
   * <p>This is idempotent and streaming operation.
   *
   * <p>Default retry and timeout settings:
   *
   * <ul>
   *   <li>{@link ServerStreamingCallSettings.Builder#setIdleTimeout Default idle timeout} is set to
   *       5 mins. Idle timeout is how long to wait before considering the stream orphaned by the
   *       user and closing it.
   *   <li>{@link ServerStreamingCallSettings.Builder#setWaitTimeout Default wait timeout} is set to
   *       5 mins. Wait timeout is the maximum amount of time to wait for the next message from the
   *       server.
   *   <li>Retry {@link ServerStreamingCallSettings.Builder#setRetryableCodes error codes} are:
   *       {@link Code#DEADLINE_EXCEEDED}, {@link Code#UNAVAILABLE} and {@link Code#ABORTED}.
   *   <li>RetryDelay between failed attempts {@link RetrySettings.Builder#setInitialRetryDelay
   *       starts} at 10ms and {@link RetrySettings.Builder#setRetryDelayMultiplier increases
   *       exponentially} by a factor of 2 until a {@link RetrySettings.Builder#setMaxRetryDelay
   *       maximum of} 1 minute.
   *   <li>The default read timeout for {@link RetrySettings.Builder#setMaxRpcTimeout each attempt}
   *       is 30 minutes with {@link RetrySettings.Builder#setMaxAttempts maximum attempt} count of
   *       10 times and the timeout to read the {@link RetrySettings.Builder#setTotalTimeout entire
   *       stream} is 12 hours.
   * </ul>
   */
  public ServerStreamingCallSettings<Query, Row> readRowsSettings() {
    return readRowsSettings;
  }

  /**
   * Returns the object with the settings used for calls to SampleRowKeys.
   *
   * <p>This is idempotent and non-streaming operation.
   *
   * <p>Default retry and timeout settings:
   *
   * <ul>
   *   <li>Retry {@link UnaryCallSettings.Builder#setRetryableCodes error codes} are: {@link
   *       Code#DEADLINE_EXCEEDED} and {@link Code#UNAVAILABLE}.
   *   <li>RetryDelay between failed attempts {@link RetrySettings.Builder#setInitialRetryDelay
   *       starts} at 10ms and {@link RetrySettings.Builder#setRetryDelayMultiplier increases
   *       exponentially} by a factor of 2 until a {@link RetrySettings.Builder#setMaxRetryDelay
   *       maximum of} 1 minute.
   *   <li>The default timeout for {@link RetrySettings.Builder#setMaxRpcTimeout each attempt} is 5
   *       minutes and the timeout for the {@link RetrySettings.Builder#setTotalTimeout entire
   *       operation} across all of the attempts is 10 mins.
   * </ul>
   */
  public UnaryCallSettings<String, List<KeyOffset>> sampleRowKeysSettings() {
    return sampleRowKeysSettings;
  }

  /**
   * Returns the object with the settings used for point reads via ReadRows.
   *
   * <p>This is an idempotent and non-streaming operation.
   *
   * <p>Default retry and timeout settings:
   *
   * <ul>
   *   <li>Retry {@link UnaryCallSettings.Builder#setRetryableCodes error codes} are: {@link
   *       Code#DEADLINE_EXCEEDED}, {@link Code#UNAVAILABLE} and {@link Code#ABORTED}.
   *   <li>RetryDelay between failed attempts {@link RetrySettings.Builder#setInitialRetryDelay
   *       starts} at 10ms and {@link RetrySettings.Builder#setRetryDelayMultiplier increases
   *       exponentially} by a factor of 2 until a {@link RetrySettings.Builder#setMaxRetryDelay
   *       maximum of} 1 minute.
   *   <li>The default timeout for {@link RetrySettings.Builder#setMaxRpcTimeout each attempt} is 20
   *       seconds and the timeout for the {@link RetrySettings.Builder#setTotalTimeout entire
   *       operation} across all of the attempts is 10 mins.
   * </ul>
   *
   * @see RetrySettings for more explanation.
   */
  public UnaryCallSettings<Query, Row> readRowSettings() {
    return readRowSettings;
  }

  /**
   * Returns the object with the settings used for calls to MutateRow.
   *
   * <p>This is an idempotent and non-streaming operation.
   *
   * <p>Default retry and timeout settings:
   *
   * <ul>
   *   <li>Retry {@link UnaryCallSettings.Builder#setRetryableCodes error codes} are: {@link
   *       Code#DEADLINE_EXCEEDED} and {@link Code#UNAVAILABLE}.
   *   <li>RetryDelay between failed attempts {@link RetrySettings.Builder#setInitialRetryDelay
   *       starts} at 10ms and {@link RetrySettings.Builder#setRetryDelayMultiplier increases
   *       exponentially} by a factor of 2 until a {@link RetrySettings.Builder#setMaxRetryDelay
   *       maximum of} 60 seconds.
   *   <li>The default timeout for {@link RetrySettings.Builder#setMaxRpcTimeout each attempt} is 20
   *       seconds and the timeout for the {@link RetrySettings.Builder#setTotalTimeout entire
   *       operation} across all of the attempts is 10 mins.
   * </ul>
   *
   * @see RetrySettings for more explanation.
   */
  public UnaryCallSettings<RowMutation, Void> mutateRowSettings() {
    return mutateRowSettings;
  }

  /**
   * Returns the object with the settings used for calls to MutateRows.
   *
   * <p>Please note that these settings will affect both manually batched calls
   * (bulkMutateRowsCallable) and automatic batched calls (bulkMutateRowsBatchingCallable). The
   * {@link RowMutation} request signature is ignored for the manual batched calls.
   *
   * <p>Default retry and timeout settings:
   *
   * <ul>
   *   <li>Retry {@link BatchingCallSettings.Builder#setRetryableCodes error codes} are: {@link
   *       Code#DEADLINE_EXCEEDED} and {@link Code#UNAVAILABLE}.
   *   <li>RetryDelay between failed attempts {@link RetrySettings.Builder#setInitialRetryDelay
   *       starts} at 10ms and {@link RetrySettings.Builder#setRetryDelayMultiplier increases
   *       exponentially} by a factor of 2 until a {@link RetrySettings.Builder#setMaxRetryDelay
   *       maximum of} 1 minute.
   *   <li>The default timeout for {@link RetrySettings.Builder#setMaxRpcTimeout each attempt} is 1
   *       minute and the timeout for the {@link RetrySettings.Builder#setTotalTimeout entire
   *       operation} across all of the attempts is 10 mins.
   * </ul>
   *
   * <p>On breach of certain triggers, the operation initiates processing of accumulated request for
   * which the default settings are:
   *
   * <ul>
   *   <li>When the {@link BatchingSettings.Builder#setElementCountThreshold request count} reaches
   *       100.
   *   <li>When accumulated {@link BatchingSettings.Builder#setRequestByteThreshold request size}
   *       reaches to 20MB.
   *   <li>When an {@link BatchingSettings.Builder#setDelayThreshold interval of} 1 second passes
   *       after batching initialization or last processed batch.
   * </ul>
   *
   * <p>A {@link FlowController} will be set up with {@link BigtableBatchingCallSettings.Builder
   * #getDynamicFlowControlSettings()} for throttling in-flight requests. When the pending request
   * count or accumulated request size reaches {@link FlowController} thresholds, then this
   * operation will be throttled until some of the pending batches are resolved.
   *
   * @see RetrySettings for more explanation.
   * @see BatchingSettings for batch related configuration explanation.
   * @see BigtableBatchingCallSettings.Builder#getDynamicFlowControlSettings() for flow control
   *     related configuration explanation.
   */
  public BigtableBatchingCallSettings bulkMutateRowsSettings() {
    return bulkMutateRowsSettings;
  }

  /**
   * Returns the call settings used for bulk read rows.
   *
   * <p>Default retry and timeout settings:
   *
   * <ul>
   *   <li>Retry {@link BatchingCallSettings.Builder#setRetryableCodes error codes} are: {@link
   *       Code#DEADLINE_EXCEEDED}, {@link Code#UNAVAILABLE} and {@link Code#ABORTED}.
   *   <li>RetryDelay between failed attempts {@link RetrySettings.Builder#setInitialRetryDelay
   *       starts} at 10ms and {@link RetrySettings.Builder#setRetryDelayMultiplier increases
   *       exponentially} by a factor of 2 until a {@link RetrySettings.Builder#setMaxRetryDelay
   *       maximum of} 1 minute.
   *   <li>The default timeout for {@link RetrySettings.Builder#setMaxRpcTimeout each attempt} is 5
   *       minute and the timeout for the {@link RetrySettings.Builder#setTotalTimeout entire
   *       operation} across all of the attempts is 10 mins.
   * </ul>
   *
   * <p>On breach of certain triggers, the operation initiates processing of accumulated request for
   * which the default settings are:
   *
   * <ul>
   *   <li>When the {@link BatchingSettings.Builder#setElementCountThreshold request count} reaches
   *       100.
   *   <li>When accumulated {@link BatchingSettings.Builder#setRequestByteThreshold request size}
   *       reaches to 400KB.
   *   <li>When an {@link BatchingSettings.Builder#setDelayThreshold interval of} 1 second passes
   *       after batching initialization or last processed batch.
   * </ul>
   *
   * <p>When the pending {@link FlowControlSettings.Builder#setMaxOutstandingElementCount request
   * count} reaches a default of 1000 outstanding row keys per channel then this operation will by
   * default be {@link FlowControlSettings.Builder#setLimitExceededBehavior blocked} until some of
   * the pending batch are resolved.
   *
   * @see RetrySettings for more explanation.
   * @see BatchingSettings for batch related configuration explanation.
   */
  public BigtableBulkReadRowsCallSettings bulkReadRowsSettings() {
    return bulkReadRowsSettings;
  }

  /**
   * Returns the object with the settings used for calls to CheckAndMutateRow.
   *
   * <p>This is a non-idempotent and non-streaming operation.
   *
   * <p>By default this operation does not reattempt in case of RPC failure. The default timeout for
   * the {@link RetrySettings.Builder#setTotalTimeout entire operation} is 20 seconds.
   *
   * @see RetrySettings for more explanation.
   */
  public UnaryCallSettings<ConditionalRowMutation, Boolean> checkAndMutateRowSettings() {
    return checkAndMutateRowSettings;
  }

  /**
   * Returns the object with the settings used for calls to ReadModifyWriteRow.
   *
   * <p>This is a non-idempotent and non-streaming operation.
   *
   * <p>By default this operation does not reattempt in case of RPC failure. The default timeout for
   * the {@link RetrySettings.Builder#setTotalTimeout entire operation} is 20 seconds.
   *
   * @see RetrySettings for more explanation.
   */
  public UnaryCallSettings<ReadModifyWriteRow, Row> readModifyWriteRowSettings() {
    return readModifyWriteRowSettings;
  }

  public ServerStreamingCallSettings<String, ByteStringRange>
      generateInitialChangeStreamPartitionsSettings() {
    return generateInitialChangeStreamPartitionsSettings;
  }

  public ServerStreamingCallSettings<ReadChangeStreamQuery, ChangeStreamRecord>
      readChangeStreamSettings() {
    return readChangeStreamSettings;
  }

  public ServerStreamingCallSettings<BoundStatement, SqlRow> executeQuerySettings() {
    return executeQuerySettings;
  }

  /**
   * Returns the object with the settings used for a PrepareQuery request. This is used by
   * PreparedStatement to manage PreparedQueries.
   *
   * <p>This is an idempotent and non-streaming operation.
   *
   * <p>Default retry and timeout settings:
   *
   * <ul>
   *   <li>Retry {@link UnaryCallSettings.Builder#setRetryableCodes error codes} are: {@link
   *       Code#DEADLINE_EXCEEDED} and {@link Code#UNAVAILABLE}
   *   <li>RetryDelay between failed attempts {@link RetrySettings.Builder#setInitialRetryDelay
   *       starts} at 10ms and {@link RetrySettings.Builder#setRetryDelayMultiplier increases
   *       exponentially} by a factor of 2 until a {@link RetrySettings.Builder#setMaxRetryDelay
   *       maximum of} 1 minute.
   *   <li>The default timeout for {@link RetrySettings.Builder#setMaxRpcTimeout each attempt} is 5
   *       seconds and the timeout for the {@link RetrySettings.Builder#setTotalTimeout entire
   *       operation} across all of the attempts is 10 mins.
   * </ul>
   *
   * @see RetrySettings for more explanation.
   */
  public UnaryCallSettings<PrepareQueryRequest, PrepareResponse> prepareQuerySettings() {
    return prepareQuerySettings;
  }

  /**
   * Returns the object with the settings used for calls to PingAndWarm.
   *
   * <p>By default the retries are disabled for PingAndWarm and deadline is set to 30 seconds.
   */
  UnaryCallSettings<PingAndWarmRequest, Void> pingAndWarmSettings() {
    return pingAndWarmSettings;
  }

  /** Returns a builder containing all the values of this settings class. */
  public Builder toBuilder() {
    return new Builder(this);
  }

  /** Builder for BigtableDataSettings. */
  public static class Builder extends StubSettings.Builder<EnhancedBigtableStubSettings, Builder> {

    private String projectId;
    private String instanceId;
    private String appProfileId;
    private boolean isRefreshingChannel;
    private ImmutableList<String> primedTableIds;
    private String jwtAudience;
    private boolean enableRoutingCookie;
    private boolean enableRetryInfo;
    private boolean enableSkipTrailers;

    private final ServerStreamingCallSettings.Builder<Query, Row> readRowsSettings;
    private final UnaryCallSettings.Builder<Query, Row> readRowSettings;
    private final UnaryCallSettings.Builder<String, List<KeyOffset>> sampleRowKeysSettings;
    private final UnaryCallSettings.Builder<RowMutation, Void> mutateRowSettings;
    private final BigtableBatchingCallSettings.Builder bulkMutateRowsSettings;
    private final BigtableBulkReadRowsCallSettings.Builder bulkReadRowsSettings;
    private final UnaryCallSettings.Builder<ConditionalRowMutation, Boolean>
        checkAndMutateRowSettings;
    private final UnaryCallSettings.Builder<ReadModifyWriteRow, Row> readModifyWriteRowSettings;
    private final ServerStreamingCallSettings.Builder<String, ByteStringRange>
        generateInitialChangeStreamPartitionsSettings;
    private final ServerStreamingCallSettings.Builder<ReadChangeStreamQuery, ChangeStreamRecord>
        readChangeStreamSettings;
    private final UnaryCallSettings.Builder<PingAndWarmRequest, Void> pingAndWarmSettings;
    private final ServerStreamingCallSettings.Builder<BoundStatement, SqlRow> executeQuerySettings;
    private final UnaryCallSettings.Builder<PrepareQueryRequest, PrepareResponse>
        prepareQuerySettings;

    private FeatureFlags.Builder featureFlags;

    private MetricsProvider metricsProvider;
    @Nullable private String metricsEndpoint;
    private InternalMetricsProvider internalMetricsProvider;

    /**
     * Initializes a new Builder with sane defaults for all settings.
     *
     * <p>Most defaults are extracted from BaseBigtableDataSettings, however some of the more
     * complex defaults are configured explicitly here. Once the overlayed defaults are configured,
     * the base settings are augmented to work with overlayed functionality (like disabling retries
     * in the underlying GAPIC client for batching).
     */
    private Builder() {
      this.appProfileId = SERVER_DEFAULT_APP_PROFILE_ID;
      this.isRefreshingChannel = true;
      primedTableIds = ImmutableList.of();
      setCredentialsProvider(defaultCredentialsProviderBuilder().build());
      this.enableRoutingCookie = true;
      this.enableRetryInfo = true;
      this.enableSkipTrailers = SKIP_TRAILERS;
      metricsProvider = DefaultMetricsProvider.INSTANCE;
      this.internalMetricsProvider = DEFAULT_INTERNAL_OTEL_PROVIDER;
      this.jwtAudience = DEFAULT_DATA_JWT_AUDIENCE;

      // Defaults provider
      BigtableStubSettings.Builder baseDefaults = BigtableStubSettings.newBuilder();

      setEndpoint(baseDefaults.getEndpoint());
      setTransportChannelProvider(defaultTransportChannelProvider());
      setStreamWatchdogCheckInterval(baseDefaults.getStreamWatchdogCheckInterval());
      setStreamWatchdogProvider(baseDefaults.getStreamWatchdogProvider());

      // Per-method settings using baseSettings for defaults.
      readRowsSettings = ServerStreamingCallSettings.newBuilder();

      readRowsSettings
          .setRetryableCodes(READ_ROWS_RETRY_CODES)
          .setRetrySettings(READ_ROWS_RETRY_SETTINGS)
          .setIdleTimeout(Duration.ofMinutes(5))
          .setWaitTimeout(Duration.ofMinutes(5));

      readRowSettings = UnaryCallSettings.newUnaryCallSettingsBuilder();
      readRowSettings
          .setRetryableCodes(readRowsSettings.getRetryableCodes())
          .setRetrySettings(IDEMPOTENT_RETRY_SETTINGS);

      sampleRowKeysSettings = UnaryCallSettings.newUnaryCallSettingsBuilder();
      sampleRowKeysSettings
          .setRetryableCodes(IDEMPOTENT_RETRY_CODES)
          .setRetrySettings(
              IDEMPOTENT_RETRY_SETTINGS.toBuilder()
                  .setInitialRpcTimeout(Duration.ofMinutes(5))
                  .setMaxRpcTimeout(Duration.ofMinutes(5))
                  .build());

      mutateRowSettings = UnaryCallSettings.newUnaryCallSettingsBuilder();
      copyRetrySettings(baseDefaults.mutateRowSettings(), mutateRowSettings);

      long maxBulkMutateElementPerBatch = 100L;
      long maxBulkMutateOutstandingElementCount = 20_000L;

      bulkMutateRowsSettings =
          BigtableBatchingCallSettings.newBuilder(new MutateRowsBatchingDescriptor())
              .setRetryableCodes(IDEMPOTENT_RETRY_CODES)
              .setRetrySettings(MUTATE_ROWS_RETRY_SETTINGS)
              .setBatchingSettings(
                  BatchingSettings.newBuilder()
                      .setIsEnabled(true)
                      .setElementCountThreshold(maxBulkMutateElementPerBatch)
                      .setRequestByteThreshold(20L * 1024 * 1024)
                      .setDelayThreshold(Duration.ofSeconds(1))
                      .setFlowControlSettings(
                          FlowControlSettings.newBuilder()
                              .setLimitExceededBehavior(LimitExceededBehavior.Block)
                              .setMaxOutstandingRequestBytes(100L * 1024 * 1024)
                              .setMaxOutstandingElementCount(maxBulkMutateOutstandingElementCount)
                              .build())
                      .build());

      long maxBulkReadElementPerBatch = 100L;
      long maxBulkReadRequestSizePerBatch = 400L * 1024L;
      long maxBulkReadOutstandingElementCount = 20_000L;

      bulkReadRowsSettings =
          BigtableBulkReadRowsCallSettings.newBuilder(new ReadRowsBatchingDescriptor())
              .setRetryableCodes(readRowsSettings.getRetryableCodes())
              .setRetrySettings(IDEMPOTENT_RETRY_SETTINGS)
              .setBatchingSettings(
                  BatchingSettings.newBuilder()
                      .setElementCountThreshold(maxBulkReadElementPerBatch)
                      .setRequestByteThreshold(maxBulkReadRequestSizePerBatch)
                      .setDelayThreshold(Duration.ofSeconds(1))
                      .setFlowControlSettings(
                          FlowControlSettings.newBuilder()
                              .setLimitExceededBehavior(LimitExceededBehavior.Block)
                              .setMaxOutstandingElementCount(maxBulkReadOutstandingElementCount)
                              .build())
                      .build());

      checkAndMutateRowSettings = UnaryCallSettings.newUnaryCallSettingsBuilder();
      copyRetrySettings(baseDefaults.checkAndMutateRowSettings(), checkAndMutateRowSettings);

      readModifyWriteRowSettings = UnaryCallSettings.newUnaryCallSettingsBuilder();
      copyRetrySettings(baseDefaults.readModifyWriteRowSettings(), readModifyWriteRowSettings);

      generateInitialChangeStreamPartitionsSettings = ServerStreamingCallSettings.newBuilder();
      generateInitialChangeStreamPartitionsSettings
          .setRetryableCodes(GENERATE_INITIAL_CHANGE_STREAM_PARTITIONS_RETRY_CODES)
          .setRetrySettings(GENERATE_INITIAL_CHANGE_STREAM_PARTITIONS_RETRY_SETTINGS)
          .setIdleTimeout(Duration.ofMinutes(5))
          .setWaitTimeout(Duration.ofMinutes(1));

      readChangeStreamSettings = ServerStreamingCallSettings.newBuilder();
      readChangeStreamSettings
          .setRetryableCodes(READ_CHANGE_STREAM_RETRY_CODES)
          .setRetrySettings(READ_CHANGE_STREAM_RETRY_SETTINGS)
          .setIdleTimeout(Duration.ofMinutes(5))
          .setWaitTimeout(Duration.ofMinutes(1));

      pingAndWarmSettings = UnaryCallSettings.newUnaryCallSettingsBuilder();
      pingAndWarmSettings.setRetrySettings(
          RetrySettings.newBuilder()
              .setMaxAttempts(1)
              .setInitialRpcTimeout(PRIME_REQUEST_TIMEOUT)
              .setMaxRpcTimeout(PRIME_REQUEST_TIMEOUT)
              .setTotalTimeout(PRIME_REQUEST_TIMEOUT)
              .build());

      executeQuerySettings = ServerStreamingCallSettings.newBuilder();
      executeQuerySettings
          .setRetryableCodes(EXECUTE_QUERY_RETRY_CODES)
          .setRetrySettings(EXECUTE_QUERY_RETRY_SETTINGS)
          .setIdleTimeout(Duration.ofMinutes(5))
          .setWaitTimeout(Duration.ofMinutes(5));

      prepareQuerySettings = UnaryCallSettings.newUnaryCallSettingsBuilder();
      prepareQuerySettings
          .setRetryableCodes(IDEMPOTENT_RETRY_CODES)
          .setRetrySettings(PREPARE_QUERY_RETRY_SETTINGS);

      featureFlags =
          FeatureFlags.newBuilder()
              .setReverseScans(true)
              .setLastScannedRowResponses(true)
              .setDirectAccessRequested(DIRECT_PATH_ENABLED)
              .setTrafficDirectorEnabled(DIRECT_PATH_ENABLED);
    }

    private Builder(EnhancedBigtableStubSettings settings) {
      super(settings);
      projectId = settings.projectId;
      instanceId = settings.instanceId;
      appProfileId = settings.appProfileId;
      isRefreshingChannel = settings.isRefreshingChannel;
      primedTableIds = settings.primedTableIds;
      enableRoutingCookie = settings.enableRoutingCookie;
      enableRetryInfo = settings.enableRetryInfo;
      metricsProvider = settings.metricsProvider;
      metricsEndpoint = settings.getMetricsEndpoint();
      internalMetricsProvider = settings.internalMetricsProvider;
      jwtAudience = settings.jwtAudience;

      // Per method settings.
      readRowsSettings = settings.readRowsSettings.toBuilder();
      readRowSettings = settings.readRowSettings.toBuilder();
      sampleRowKeysSettings = settings.sampleRowKeysSettings.toBuilder();
      mutateRowSettings = settings.mutateRowSettings.toBuilder();
      bulkMutateRowsSettings = settings.bulkMutateRowsSettings.toBuilder();
      bulkReadRowsSettings = settings.bulkReadRowsSettings.toBuilder();
      checkAndMutateRowSettings = settings.checkAndMutateRowSettings.toBuilder();
      readModifyWriteRowSettings = settings.readModifyWriteRowSettings.toBuilder();
      generateInitialChangeStreamPartitionsSettings =
          settings.generateInitialChangeStreamPartitionsSettings.toBuilder();
      readChangeStreamSettings = settings.readChangeStreamSettings.toBuilder();
      pingAndWarmSettings = settings.pingAndWarmSettings.toBuilder();
      executeQuerySettings = settings.executeQuerySettings().toBuilder();
      prepareQuerySettings = settings.prepareQuerySettings().toBuilder();
      featureFlags = settings.featureFlags.toBuilder();
    }

    // <editor-fold desc="Private Helpers">

    /**
     * Copies settings from unary RPC to another. This is necessary when modifying request and
     * response types while trying to retain retry settings.
     */
    private static void copyRetrySettings(
        UnaryCallSettings.Builder<?, ?> source, UnaryCallSettings.Builder<?, ?> dest) {
      dest.setRetryableCodes(source.getRetryableCodes());
      dest.setRetrySettings(source.getRetrySettings());
    }

    // </editor-fold>

    // <editor-fold desc="Public API">
    /**
     * Sets the project id of that target instance. This setting is required. All RPCs will be made
     * in the context of this setting.
     */
    public Builder setProjectId(@Nonnull String projectId) {
      Preconditions.checkNotNull(projectId);
      this.projectId = projectId;
      return this;
    }

    /** Gets the project id of the target instance that was previously set on this Builder. */
    public String getProjectId() {
      return projectId;
    }

    /**
     * Sets the target instance id. This setting is required. All RPCs will be made in the context
     * of this setting.
     */
    public Builder setInstanceId(@Nonnull String instanceId) {
      Preconditions.checkNotNull(instanceId);
      this.instanceId = instanceId;
      return this;
    }

    /** Gets the target instance id that was previously set on this Builder. */
    public String getInstanceId() {
      return instanceId;
    }

    /**
     * Sets the AppProfile to use. An application profile (sometimes also shortened to "app
     * profile") is a group of configuration parameters for an individual use case. A client will
     * identify itself with an application profile ID at connection time, and the requests will be
     * handled according to that application profile.
     */
    public Builder setAppProfileId(@Nonnull String appProfileId) {
      Preconditions.checkNotNull(appProfileId, "AppProfileId can't be null");
      this.appProfileId = appProfileId;
      return this;
    }

    /**
     * Resets the AppProfile id to the default for the instance.
     *
     * <p>An application profile (sometimes also shortened to "app profile") is a group of
     * configuration parameters for an individual use case. A client will identify itself with an
     * application profile ID at connection time, and the requests will be handled according to that
     * application profile.
     *
     * <p>Every Bigtable Instance has a default application profile associated with it, this method
     * configures the client to use it.
     */
    public Builder setDefaultAppProfileId() {
      setAppProfileId(SERVER_DEFAULT_APP_PROFILE_ID);
      return this;
    }

    /** Gets the app profile id that was previously set on this Builder. */
    public String getAppProfileId() {
      return appProfileId;
    }

    /**
     * Sets if channels will gracefully refresh connections to Cloud Bigtable service.
     *
     * <p>When enabled, this will wait for the connection to complete the SSL handshake and warm up
     * serverside caches for all the tables of the instance. This feature is enabled by default.
     *
     * @see com.google.cloud.bigtable.data.v2.BigtableDataSettings.Builder#setRefreshingChannel
     * @deprecated Channel refreshing is enabled by default and this method will be deprecated.
     */
    @Deprecated
    public Builder setRefreshingChannel(boolean isRefreshingChannel) {
      this.isRefreshingChannel = isRefreshingChannel;
      return this;
    }

    /**
     * @deprecated This field is ignored. If {@link #isRefreshingChannel()} is enabled, warm up
     *     requests will be sent to all table ids of the instance.
     */
    @Deprecated
    public Builder setPrimedTableIds(String... tableIds) {
      this.primedTableIds = ImmutableList.copyOf(tableIds);
      return this;
    }

    /**
     * Gets if channels will gracefully refresh connections to Cloud Bigtable service.
     *
     * @deprecated Channel refreshing is enabled by default and this method will be deprecated.
     */
    @Deprecated
    public boolean isRefreshingChannel() {
      return isRefreshingChannel;
    }

    /**
     * @deprecated This field is ignored. If {@link #isRefreshingChannel()} is enabled, warm up
     *     requests will be sent to all table ids of the instance.
     */
    @Deprecated
    public List<String> getPrimedTableIds() {
      return primedTableIds;
    }

    /**
     * @deprecated This is a no op. Audience is always set to bigtable service name.
     * @see #setJwtAudience(String) to override the audience.
     */
    @InternalApi("Used for internal testing")
    @Deprecated
    public Builder setJwtAudienceMapping(Map<String, String> jwtAudienceMapping) {
      return this;
    }

    /** Set the jwt audience override. */
    @InternalApi("Used for internal testing")
    public Builder setJwtAudience(String audience) {
      this.jwtAudience = audience;
      return this;
    }

    /**
     * Sets the {@link MetricsProvider}.
     *
     * <p>By default, this is set to {@link
     * com.google.cloud.bigtable.data.v2.stub.metrics.DefaultMetricsProvider#INSTANCE} which will
     * collect and export client side metrics.
     *
     * <p>To disable client side metrics, set it to {@link
     * com.google.cloud.bigtable.data.v2.stub.metrics.NoopMetricsProvider#INSTANCE}.
     *
     * <p>To use a custom OpenTelemetry instance, refer to {@link
     * com.google.cloud.bigtable.data.v2.stub.metrics.CustomOpenTelemetryMetricsProvider} on how to
     * set it up.
     */
    public Builder setMetricsProvider(MetricsProvider metricsProvider) {
      this.metricsProvider = Preconditions.checkNotNull(metricsProvider);
      return this;
    }

    /** Gets the {@link MetricsProvider}. */
    public MetricsProvider getMetricsProvider() {
      return this.metricsProvider;
    }

    /**
     * Built-in client side metrics are published through Google Cloud Monitoring endpoint. This
     * setting overrides the default endpoint for publishing the metrics.
     */
    public Builder setMetricsEndpoint(String endpoint) {
      this.metricsEndpoint = endpoint;
      return this;
    }

    /**
     * Get the Google Cloud Monitoring endpoint for publishing client side metrics. If it's null,
     * client will publish metrics to the default monitoring endpoint.
     */
    @Nullable
    public String getMetricsEndpoint() {
      return metricsEndpoint;
    }

    /** Disable collection of internal metrics that help google detect issues accessing Bigtable. */
    public Builder disableInternalMetrics() {
      return setInternalMetricsProvider(DISABLED_INTERNAL_OTEL_PROVIDER);
    }

    // For testing
    @InternalApi
    public Builder setInternalMetricsProvider(InternalMetricsProvider internalMetricsProvider) {
      this.internalMetricsProvider = internalMetricsProvider;
      return this;
    }

    /** Checks if internal metrics are disabled */
    public boolean areInternalMetricsEnabled() {
      return internalMetricsProvider == DISABLED_INTERNAL_OTEL_PROVIDER;
    }

    /**
     * @deprecated This is a no op and will always return an empty map. Audience is always set to
     *     bigtable service name.
     * @see #getJwtAudience() to get the audience.
     */
    @InternalApi("Used for internal testing")
    @Deprecated
    public Map<String, String> getJwtAudienceMapping() {
      return ImmutableMap.of();
    }

    /** Return the jwt audience override. */
    String getJwtAudience() {
      return this.jwtAudience;
    }

    /**
     * Sets if routing cookie is enabled. If true, client will retry a request with extra metadata
     * server sent back.
     */
    @BetaApi("Routing cookie is not currently stable and may change in the future")
    public Builder setEnableRoutingCookie(boolean enableRoutingCookie) {
      this.enableRoutingCookie = enableRoutingCookie;
      return this;
    }

    /**
     * Gets if routing cookie is enabled. If true, client will retry a request with extra metadata
     * server sent back.
     */
    @BetaApi("Routing cookie is not currently stable and may change in the future")
    public boolean getEnableRoutingCookie() {
      return enableRoutingCookie;
    }

    /**
     * Sets if RetryInfo is enabled. If true, client bases retry decision and back off time on
     * server returned RetryInfo value. Otherwise, client uses {@link RetrySettings}.
     */
    @BetaApi("RetryInfo is not currently stable and may change in the future")
    public Builder setEnableRetryInfo(boolean enableRetryInfo) {
      this.enableRetryInfo = enableRetryInfo;
      return this;
    }

    /**
     * Gets if RetryInfo is enabled. If true, client bases retry decision and back off time on
     * server returned RetryInfo value. Otherwise, client uses {@link RetrySettings}.
     */
    @BetaApi("RetryInfo is not currently stable and may change in the future")
    public boolean getEnableRetryInfo() {
      return enableRetryInfo;
    }

    Builder setEnableSkipTrailers(boolean enabled) {
      this.enableSkipTrailers = enabled;
      return this;
    }

    /** Returns the builder for the settings used for calls to readRows. */
    public ServerStreamingCallSettings.Builder<Query, Row> readRowsSettings() {
      return readRowsSettings;
    }

    /** Returns the builder for the settings used for point reads using readRow. */
    public UnaryCallSettings.Builder<Query, Row> readRowSettings() {
      return readRowSettings;
    }

    /** Returns the builder for the settings used for calls to SampleRowKeysSettings. */
    public UnaryCallSettings.Builder<String, List<KeyOffset>> sampleRowKeysSettings() {
      return sampleRowKeysSettings;
    }

    /** Returns the builder for the settings used for calls to MutateRow. */
    public UnaryCallSettings.Builder<RowMutation, Void> mutateRowSettings() {
      return mutateRowSettings;
    }

    /** Returns the builder for the settings used for calls to MutateRows. */
    public BigtableBatchingCallSettings.Builder bulkMutateRowsSettings() {
      return bulkMutateRowsSettings;
    }

    /** Returns the builder for the settings used for calls to MutateRows. */
    public BigtableBulkReadRowsCallSettings.Builder bulkReadRowsSettings() {
      return bulkReadRowsSettings;
    }

    /** Returns the builder for the settings used for calls to CheckAndMutateRow. */
    public UnaryCallSettings.Builder<ConditionalRowMutation, Boolean> checkAndMutateRowSettings() {
      return checkAndMutateRowSettings;
    }

    /** Returns the builder with the settings used for calls to ReadModifyWriteRow. */
    public UnaryCallSettings.Builder<ReadModifyWriteRow, Row> readModifyWriteRowSettings() {
      return readModifyWriteRowSettings;
    }

    /** Returns the builder for the settings used for calls to ReadChangeStream. */
    public ServerStreamingCallSettings.Builder<ReadChangeStreamQuery, ChangeStreamRecord>
        readChangeStreamSettings() {
      return readChangeStreamSettings;
    }

    /**
     * Returns the builder for the settings used for calls to GenerateInitialChangeStreamPartitions.
     */
    public ServerStreamingCallSettings.Builder<String, ByteStringRange>
        generateInitialChangeStreamPartitionsSettings() {
      return generateInitialChangeStreamPartitionsSettings;
    }

    /** Returns the builder with the settings used for calls to PingAndWarm. */
    public UnaryCallSettings.Builder<PingAndWarmRequest, Void> pingAndWarmSettings() {
      return pingAndWarmSettings;
    }

    /**
     * Returns the builder for the settings used for calls to ExecuteQuery
     *
     * <p>Note that this will currently ignore any retry settings other than deadlines. ExecuteQuery
     * requests will not be retried currently.
     */
    @BetaApi
    public ServerStreamingCallSettings.Builder<BoundStatement, SqlRow> executeQuerySettings() {
      return executeQuerySettings;
    }

    /** Returns the builder with the settings used for calls to PrepareQuery */
    @BetaApi
    public UnaryCallSettings.Builder<PrepareQueryRequest, PrepareResponse> prepareQuerySettings() {
      return prepareQuerySettings;
    }

    @SuppressWarnings("unchecked")
    public EnhancedBigtableStubSettings build() {
      Preconditions.checkState(projectId != null, "Project id must be set");
      Preconditions.checkState(instanceId != null, "Instance id must be set");

      if (this.bulkMutateRowsSettings().isServerInitiatedFlowControlEnabled()) {
        // only set mutate rows feature flag when this feature is enabled
        featureFlags.setMutateRowsRateLimit(true);
        featureFlags.setMutateRowsRateLimit2(true);
      }

      featureFlags.setRoutingCookie(this.getEnableRoutingCookie());
      featureFlags.setRetryInfo(this.getEnableRetryInfo());
      // client_Side_metrics_enabled feature flag is only set when a user is running with a
      // DefaultMetricsProvider. This may cause false negatives when a user registered the
      // metrics on their CustomOpenTelemetryMetricsProvider.
      featureFlags.setClientSideMetricsEnabled(
          this.getMetricsProvider() instanceof DefaultMetricsProvider);

      // Serialize the web64 encode the bigtable feature flags
      ByteArrayOutputStream boas = new ByteArrayOutputStream();
      try {
        featureFlags.build().writeTo(boas);
      } catch (IOException e) {
        throw new IllegalStateException(
            "Unexpected IOException while serializing feature flags", e);
      }
      byte[] serializedFlags = boas.toByteArray();
      byte[] encodedFlags = Base64.getUrlEncoder().encode(serializedFlags);

      // Inject the UserAgent in addition to api-client header
      Map<String, String> headers =
          ImmutableMap.<String, String>builder()
              .putAll(
                  BigtableStubSettings.defaultApiClientHeaderProviderBuilder().build().getHeaders())
              // GrpcHeaderInterceptor treats the `user-agent` as a magic string
              .put("user-agent", "bigtable-java/" + Version.VERSION)
              .put("bigtable-features", new String(encodedFlags, StandardCharsets.UTF_8))
              .build();
      setInternalHeaderProvider(FixedHeaderProvider.create(headers));

      return new EnhancedBigtableStubSettings(this);
    }
    // </editor-fold>
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("projectId", projectId)
        .add("instanceId", instanceId)
        .add("appProfileId", appProfileId)
        .add("isRefreshingChannel", isRefreshingChannel)
        .add("primedTableIds", primedTableIds)
        .add("enableRoutingCookie", enableRoutingCookie)
        .add("enableRetryInfo", enableRetryInfo)
        .add("enableSkipTrailers", enableSkipTrailers)
        .add("readRowsSettings", readRowsSettings)
        .add("readRowSettings", readRowSettings)
        .add("sampleRowKeysSettings", sampleRowKeysSettings)
        .add("mutateRowSettings", mutateRowSettings)
        .add("bulkMutateRowsSettings", bulkMutateRowsSettings)
        .add("bulkReadRowsSettings", bulkReadRowsSettings)
        .add("checkAndMutateRowSettings", checkAndMutateRowSettings)
        .add("readModifyWriteRowSettings", readModifyWriteRowSettings)
        .add(
            "generateInitialChangeStreamPartitionsSettings",
            generateInitialChangeStreamPartitionsSettings)
        .add("readChangeStreamSettings", readChangeStreamSettings)
        .add("pingAndWarmSettings", pingAndWarmSettings)
        .add("executeQuerySettings", executeQuerySettings)
        .add("prepareQuerySettings", prepareQuerySettings)
        .add("metricsProvider", metricsProvider)
        .add("metricsEndpoint", metricsEndpoint)
        .add("areInternalMetricsEnabled", internalMetricsProvider == DEFAULT_INTERNAL_OTEL_PROVIDER)
        .add("jwtAudience", jwtAudience)
        .add("parent", super.toString())
        .toString();
  }

  @InternalApi
  @FunctionalInterface
  public interface InternalMetricsProvider {
    @Nullable
    OpenTelemetrySdk createOtelProvider(
        EnhancedBigtableStubSettings userSettings, Credentials creds) throws IOException;
  }

  private static final InternalMetricsProvider DEFAULT_INTERNAL_OTEL_PROVIDER =
      Util::newInternalOpentelemetry;
  private static final InternalMetricsProvider DISABLED_INTERNAL_OTEL_PROVIDER =
      (ignored1, ignored2) -> null;
}
