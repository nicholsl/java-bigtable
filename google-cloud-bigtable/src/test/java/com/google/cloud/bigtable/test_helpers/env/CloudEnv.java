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
package com.google.cloud.bigtable.test_helpers.env;

import com.google.api.core.ApiFunction;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.StubSettings;
import com.google.cloud.bigtable.admin.v2.BigtableInstanceAdminClient;
import com.google.cloud.bigtable.admin.v2.BigtableInstanceAdminSettings;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminSettings;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import com.google.cloud.bigtable.data.v2.stub.EnhancedBigtableStubSettings;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Grpc;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Test environment that uses an existing bigtable table. The table must have a pre-existing family
 * {@code cf}. The target table is configured via the system properties:
 *
 * <ul>
 *   <li>{@code bigtable.project}
 *   <li>{@code bigtable.instance}
 *   <li>{@code bigtable.table}
 * </ul>
 */
public class CloudEnv extends AbstractTestEnv {
  private static final Predicate<InetSocketAddress> DIRECT_PATH_IPV6_MATCHER =
      new Predicate<InetSocketAddress>() {
        @Override
        public boolean apply(InetSocketAddress input) {
          return input.toString().startsWith("2001:4860:8040");
        }
      };
  private static final Predicate<InetSocketAddress> DIRECT_PATH_IPV4_MATCHER =
      new Predicate<InetSocketAddress>() {
        @Override
        public boolean apply(InetSocketAddress input) {
          return input.toString().startsWith("34.126");
        }
      };

  private static final String DATA_ENDPOINT_PROPERTY_NAME = "bigtable.data-endpoint";
  private static final String ADMIN_ENDPOINT_PROPERTY_NAME = "bigtable.admin-endpoint";
  private static final String DATA_JWT_OVERRIDE_PROPERTY_NAME = "bigtable.data-jwt-audience";

  private static final String PROJECT_PROPERTY_NAME = "bigtable.project";
  private static final String INSTANCE_PROPERTY_NAME = "bigtable.instance";
  private static final String APP_PROFILE_PROPERTY_NAME = "bigtable.app_profile";
  private static final String TABLE_PROPERTY_NAME = "bigtable.table";
  private static final String CMEK_KMS_KEY_PROPERTY_NAME = "bigtable.kms_key_name";

  private static final String TRACING_COOKIE_PROPERTY_NAME = "bigtable.tracing_cookie";

  private final String projectId;
  private final String instanceId;
  private final String tableId;
  private final String kmsKeyName;

  private final BigtableDataSettings.Builder dataSettings;
  private final BigtableTableAdminSettings.Builder tableAdminSettings;
  private final BigtableInstanceAdminSettings.Builder instanceAdminSettings;
  @Nullable private final String appProfileId;

  private BigtableDataClient dataClient;
  private BigtableTableAdminClient tableAdminClient;
  private BigtableInstanceAdminClient instanceAdminClient;

  static CloudEnv fromSystemProperties() {
    return new CloudEnv(
        getOptionalProperty(DATA_ENDPOINT_PROPERTY_NAME, ""),
        getOptionalProperty(ADMIN_ENDPOINT_PROPERTY_NAME, ""),
        getOptionalProperty(DATA_JWT_OVERRIDE_PROPERTY_NAME, ""),
        getOptionalProperty(CMEK_KMS_KEY_PROPERTY_NAME, ""),
        getRequiredProperty(PROJECT_PROPERTY_NAME),
        getRequiredProperty(INSTANCE_PROPERTY_NAME),
        getOptionalProperty(APP_PROFILE_PROPERTY_NAME),
        getRequiredProperty(TABLE_PROPERTY_NAME),
        getOptionalProperty(TRACING_COOKIE_PROPERTY_NAME));
  }

  private CloudEnv(
      @Nullable String dataEndpoint,
      @Nullable String adminEndpoint,
      @Nullable String jwtAudienceOverride,
      @Nullable String kmsKeyName,
      String projectId,
      String instanceId,
      @Nullable String appProfileId,
      String tableId,
      @Nullable String tracingCookie) {
    this.projectId = projectId;
    this.instanceId = instanceId;
    this.appProfileId = appProfileId;
    this.tableId = tableId;
    this.kmsKeyName = kmsKeyName;

    this.dataSettings =
        BigtableDataSettings.newBuilder().setProjectId(projectId).setInstanceId(instanceId);
    if (!Strings.isNullOrEmpty(dataEndpoint)) {
      dataSettings.stubSettings().setEndpoint(dataEndpoint);
    }
    if (!Strings.isNullOrEmpty(appProfileId)) {
      dataSettings.setAppProfileId(appProfileId);
    }
    if (!Strings.isNullOrEmpty(jwtAudienceOverride)) {
      dataSettings.stubSettings().setJwtAudience(jwtAudienceOverride);
    }

    configureConnection(dataSettings.stubSettings());
    configureUserAgent(dataSettings.stubSettings());

    if (tracingCookie != null) {
      injectTracingCookie(tracingCookie, dataSettings.stubSettings());
    }

    this.tableAdminSettings =
        BigtableTableAdminSettings.newBuilder().setProjectId(projectId).setInstanceId(instanceId);
    if (!Strings.isNullOrEmpty(adminEndpoint)) {
      this.tableAdminSettings.stubSettings().setEndpoint(adminEndpoint);
    }

    this.instanceAdminSettings = BigtableInstanceAdminSettings.newBuilder().setProjectId(projectId);
    if (!Strings.isNullOrEmpty(adminEndpoint)) {
      this.instanceAdminSettings.stubSettings().setEndpoint(adminEndpoint);
    }
  }

  private static void injectTracingCookie(
      String tracingCookie, EnhancedBigtableStubSettings.Builder settings) {
    HeaderProvider oldHeaderProvider = settings.getHeaderProvider();
    settings.setHeaderProvider(
        () ->
            Optional.ofNullable(oldHeaderProvider)
                .map(p -> ImmutableMap.<String, String>builder().putAll(p.getHeaders()))
                .orElse(ImmutableMap.builder())
                .put("cookie", tracingCookie)
                .build());
  }

  private void configureConnection(StubSettings.Builder stubSettings) {
    // Build an remote address restricting interceptor
    final ClientInterceptor interceptor;

    switch (getConnectionMode()) {
      case DEFAULT:
        // nothing special
        return;
      case REQUIRE_DIRECT_PATH:
        interceptor =
            buildRemoteAddrInterceptor(
                "DirectPath IPv4 or IPv6",
                Predicates.or(DIRECT_PATH_IPV4_MATCHER, DIRECT_PATH_IPV6_MATCHER));
        break;
      case REQUIRE_DIRECT_PATH_IPV4:
        interceptor =
            buildRemoteAddrInterceptor("DirectPath IPv4", Predicates.or(DIRECT_PATH_IPV4_MATCHER));
        break;
      case REQUIRE_CFE:
        interceptor =
            buildRemoteAddrInterceptor(
                "a CFE ip",
                Predicates.not(Predicates.or(DIRECT_PATH_IPV4_MATCHER, DIRECT_PATH_IPV6_MATCHER)));
        break;
      default:
        throw new IllegalStateException("Unexpected ConnectionMode: " + getConnectionMode());
    }

    final ClientInterceptor appProfileInterceptor =
        appProfileId != null ? new AppProfileInterceptor() : null;

    // Inject the interceptor into the channel provider, taking care to preserve existing channel
    // configurator
    InstantiatingGrpcChannelProvider.Builder channelProvider =
        ((InstantiatingGrpcChannelProvider) stubSettings.getTransportChannelProvider()).toBuilder();

    @SuppressWarnings("rawtypes")
    final ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> oldConfigurator =
        channelProvider.getChannelConfigurator();

    @SuppressWarnings("rawtypes")
    final ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> newConfigurator =
        new ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder>() {
          @Override
          @SuppressWarnings("rawtypes")
          public ManagedChannelBuilder apply(ManagedChannelBuilder builder) {
            if (oldConfigurator != null) {
              builder = oldConfigurator.apply(builder);
            }
            builder = builder.intercept(interceptor);
            if (appProfileInterceptor != null) {
              builder = builder.intercept(appProfileInterceptor);
            }
            return builder;
          }
        };
    channelProvider.setChannelConfigurator(newConfigurator);
    stubSettings.setTransportChannelProvider(channelProvider.build());
  }

  private ClientInterceptor buildRemoteAddrInterceptor(
      final String msg, final Predicate<InetSocketAddress> predicate) {
    return new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
          MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        final ClientCall<ReqT, RespT> clientCall = next.newCall(method, callOptions);

        return new SimpleForwardingClientCall<ReqT, RespT>(clientCall) {
          @Override
          public void start(Listener<RespT> responseListener, Metadata headers) {
            super.start(
                new SimpleForwardingClientCallListener<RespT>(responseListener) {
                  @Override
                  public void onHeaders(Metadata headers) {
                    // Check peer IP after connection is established.
                    SocketAddress remoteAddr =
                        clientCall.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);

                    if (!predicate.apply((InetSocketAddress) remoteAddr)) {
                      throw new RuntimeException(
                          String.format(
                              "Synthetically aborting the current request because it did not adhere"
                                  + " to the test environment's requirement ."
                                  + " Expected %s, but ip was: %s",
                              msg, remoteAddr));
                    }
                    super.onHeaders(headers);
                  }
                },
                headers);
          }
        };
      }
    };
  }

  private class AppProfileInterceptor implements ClientInterceptor {
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {
      return new SimpleForwardingClientCall<ReqT, RespT>(
          channel.newCall(methodDescriptor, callOptions)) {
        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
          String reqParams =
              headers.get(
                  Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER));
          if (!reqParams.contains("app_profile_id=" + appProfileId)) {
            responseListener.onClose(
                Status.FAILED_PRECONDITION.withDescription(
                    "Integration test was configured to run with app profile: "
                        + appProfileId
                        + ", but found a different app profile in the headers: "
                        + reqParams),
                new Metadata());
            return;
          }
          super.start(responseListener, headers);
        }
      };
    }
  }

  private void configureUserAgent(EnhancedBigtableStubSettings.Builder stubSettings) {
    String newUserAgent = "java-bigtable-int-test";

    // Use the existing user-agent to use as a prefix
    Map<String, String> existingHeaders =
        MoreObjects.firstNonNull(stubSettings.getHeaderProvider(), FixedHeaderProvider.create())
            .getHeaders();
    String existingUserAgent = existingHeaders.get("user-agent");
    if (existingUserAgent != null) {
      newUserAgent = existingUserAgent + " " + newUserAgent;
    }

    Map<String, String> newHeaders = new HashMap<>(existingHeaders);
    newHeaders.put("user-agent", newUserAgent);

    stubSettings.setHeaderProvider(FixedHeaderProvider.create(newHeaders));
  }

  @Override
  void start() throws IOException {
    dataClient = BigtableDataClient.create(dataSettings.build());
    tableAdminClient = BigtableTableAdminClient.create(tableAdminSettings.build());
    instanceAdminClient = BigtableInstanceAdminClient.create(instanceAdminSettings.build());
  }

  @Override
  void stop() {
    dataClient.close();
    tableAdminClient.close();
    instanceAdminClient.close();
  }

  @Override
  public BigtableDataClient getDataClient() {
    return dataClient;
  }

  @Override
  public BigtableTableAdminClient getTableAdminClient() {
    return tableAdminClient;
  }

  @Override
  public BigtableTableAdminClient getTableAdminClientForInstance(String instanceId)
      throws IOException {
    BigtableTableAdminSettings.Builder settings =
        BigtableTableAdminSettings.newBuilder()
            .setProjectId(tableAdminSettings.getProjectId())
            .setInstanceId(instanceId);
    settings.stubSettings().setEndpoint(tableAdminSettings.stubSettings().getEndpoint());
    return BigtableTableAdminClient.create(settings.build());
  }

  @Override
  public BigtableInstanceAdminClient getInstanceAdminClient() {
    return instanceAdminClient;
  }

  @Override
  public BigtableDataSettings getDataClientSettings() {
    return dataSettings.build();
  }

  @Override
  public BigtableInstanceAdminSettings getInstanceAdminClientSettings() {
    try {
      return instanceAdminSettings.build();
    } catch (IOException e) {
      throw new IllegalStateException(
          "Caught unexpected error building instance admin settings", e);
    }
  }

  @Override
  public BigtableTableAdminSettings getTableAdminSettings() {
    try {
      return tableAdminSettings.build();
    } catch (IOException e) {
      throw new IllegalStateException("Caught unexpected error building table admin settings", e);
    }
  }

  @Override
  public String getProjectId() {
    return projectId;
  }

  @Override
  public String getInstanceId() {
    return instanceId;
  }

  @Override
  public String getTableId() {
    return tableId;
  }

  public String getKmsKeyName() {
    return kmsKeyName;
  }

  @Nullable
  private static String getOptionalProperty(String prop) {
    return System.getProperty(prop);
  }

  private static String getOptionalProperty(String prop, String defaultValue) {
    return MoreObjects.firstNonNull(System.getProperty(prop), defaultValue);
  }

  private static String getRequiredProperty(String prop) {
    String value = System.getProperty(prop);
    if (value == null || value.isEmpty()) {
      throw new RuntimeException("Missing system property: " + prop);
    }
    return value;
  }
}
