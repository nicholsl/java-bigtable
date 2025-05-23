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
package com.google.cloud.bigtable.data.v2;

import static com.google.cloud.bigtable.data.v2.models.Filters.FILTERS;

import com.google.api.core.ApiFunction;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.api.gax.batching.Batcher;
import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.rpc.ApiExceptions;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStream;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.bigtable.data.v2.internal.PrepareQueryRequest;
import com.google.cloud.bigtable.data.v2.internal.PrepareResponse;
import com.google.cloud.bigtable.data.v2.internal.PreparedStatementImpl;
import com.google.cloud.bigtable.data.v2.internal.ResultSetImpl;
import com.google.cloud.bigtable.data.v2.models.BulkMutation;
import com.google.cloud.bigtable.data.v2.models.ChangeStreamRecord;
import com.google.cloud.bigtable.data.v2.models.ConditionalRowMutation;
import com.google.cloud.bigtable.data.v2.models.Filters;
import com.google.cloud.bigtable.data.v2.models.Filters.Filter;
import com.google.cloud.bigtable.data.v2.models.KeyOffset;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.bigtable.data.v2.models.Range.ByteStringRange;
import com.google.cloud.bigtable.data.v2.models.ReadChangeStreamQuery;
import com.google.cloud.bigtable.data.v2.models.ReadModifyWriteRow;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowAdapter;
import com.google.cloud.bigtable.data.v2.models.RowMutation;
import com.google.cloud.bigtable.data.v2.models.RowMutationEntry;
import com.google.cloud.bigtable.data.v2.models.SampleRowKeysRequest;
import com.google.cloud.bigtable.data.v2.models.TableId;
import com.google.cloud.bigtable.data.v2.models.TargetId;
import com.google.cloud.bigtable.data.v2.models.sql.BoundStatement;
import com.google.cloud.bigtable.data.v2.models.sql.PreparedStatement;
import com.google.cloud.bigtable.data.v2.models.sql.ResultSet;
import com.google.cloud.bigtable.data.v2.models.sql.SqlType;
import com.google.cloud.bigtable.data.v2.stub.EnhancedBigtableStub;
import com.google.cloud.bigtable.data.v2.stub.sql.SqlServerStream;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Client for reading from and writing to existing Bigtable tables.
 *
 * <p>This class provides the ability to make remote calls to the backing service. Sample code to
 * get started:
 *
 * <pre>{@code
 * // One instance per application.
 * BigtableDataClient client = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")
 *
 * for(Row row : client.readRows(Query.create("[TABLE]"))) {
 *   // Do something with row
 * }
 *
 * // Cleanup during application shutdown.
 * client.close();
 * }</pre>
 *
 * <p>Creating a new client is a very expensive operation and should only be done once and shared in
 * an application. However, close() needs to be called on the client object to clean up resources
 * such as threads during application shutdown.
 *
 * <p>This client can be safely shared across multiple threads except for the Batcher instances
 * returned from bulk operations, eg. `newBulkMutationBatcher()`, `newBulkReadRowsBatcher()`.
 *
 * <p>The surface of this class includes several types of Java methods for each of the API's
 * methods:
 *
 * <ol>
 *   <li>A "flattened" method, like `readRows()`. With this type of method, the fields of the
 *       request type have been converted into function parameters. It may be the case that not all
 *       fields are available as parameters, and not every API method will have a flattened method
 *       entry point.
 *   <li>A "callable" method, like `readRowsCallable()`. This type of method takes no parameters and
 *       returns an immutable API callable object, which can be used to initiate calls to the
 *       service.
 * </ol>
 *
 * <p>Taking ReadRows as an example for callable:
 *
 * <pre>{@code
 * // These two invocation are equivalent
 * ServerStream<Row> stream1 = client.readRows(query);
 * ServerStream<Row> stream2 = client.readRowsCallable().call(query);
 *
 * // These two invocation are also equivalent
 * client.readRowsAsync(query, observer);
 * client.readRowsCallable().call(query, observer);
 * }</pre>
 *
 * <p>All RPC related errors are represented as subclasses of {@link
 * com.google.api.gax.rpc.ApiException}. For example, a nonexistent table will trigger a {@link
 * com.google.api.gax.rpc.NotFoundException}. Async methods will wrap the error inside the future.
 * Synchronous methods will re-throw the async error but will try to preserve the caller's
 * stacktrace by attaching a suppressed exception at the callsite. This allows callers to use
 * typesafe exceptions, without losing their callsite. Streaming methods (ie. readRows) will
 * re-throw the async exception (like sync methods) when starting iteration.
 *
 * <p>See the individual methods for example code.
 *
 * <p>This class can be customized by passing in a custom instance of BigtableDataSettings to
 * create(). For example:
 *
 * <p>To customize credentials:
 *
 * <pre>{@code
 * BigtableDataSettings settings =
 *     BigtableDataSettings.newBuilder()
 *         .setProjectId("[PROJECT]")
 *         .setInstanceId("[INSTANCE]")
 *         .setCredentialsProvider(FixedCredentialsProvider.create(myCredentials))
 *         .build();
 * BigtableDataClient client = BigtableDataClient.create(settings);
 * }</pre>
 *
 * To customize the endpoint:
 *
 * <pre>{@code
 * BigtableDataSettings.Builder settingsBuilder =
 *     BigtableDataSettings.newBuilder()
 *       .setProjectId("[PROJECT]")
 *       .setInstanceId("[INSTANCE]");
 *
 * settingsBuilder.stubSettings()
 *     .setEndpoint(myEndpoint).build();
 *
 * BigtableDataClient client = BigtableDataClient.create(settings.build());
 * }</pre>
 */
public class BigtableDataClient implements AutoCloseable {
  private final EnhancedBigtableStub stub;

  /**
   * Constructs an instance of BigtableDataClient with default settings.
   *
   * @param projectId The project id of the instance to connect to.
   * @param instanceId The id of the instance to connect to.
   * @return A new client.
   * @throws IOException If any.
   */
  public static BigtableDataClient create(String projectId, String instanceId) throws IOException {
    BigtableDataSettings settings =
        BigtableDataSettings.newBuilder().setProjectId(projectId).setInstanceId(instanceId).build();
    return create(settings);
  }

  /**
   * Constructs an instance of BigtableDataClient, using the given settings. The channels are
   * created based on the settings passed in, or defaults for any settings that are not set.
   */
  public static BigtableDataClient create(BigtableDataSettings settings) throws IOException {
    EnhancedBigtableStub stub = EnhancedBigtableStub.create(settings.getStubSettings());
    return new BigtableDataClient(stub);
  }

  /**
   * Constructs an instance of BigtableDataClient with the provided client context. This is used by
   * {@link BigtableDataClientFactory} and the client context will not be closed unless {@link
   * BigtableDataClientFactory#close()} is called.
   */
  static BigtableDataClient createWithClientContext(
      BigtableDataSettings settings, ClientContext context) throws IOException {
    EnhancedBigtableStub stub =
        EnhancedBigtableStub.createWithClientContext(settings.getStubSettings(), context);
    return new BigtableDataClient(stub);
  }

  @InternalApi("Visible for testing")
  BigtableDataClient(EnhancedBigtableStub stub) {
    this.stub = stub;
  }

  /**
   * Confirms synchronously if given row key exists or not.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *   String key = "key";
   *
   *   boolean isRowPresent = bigtableDataClient.exists(tableId, key);
   *
   *   // Do something with result, for example, display a message
   *   if(isRowPresent) {
   *     System.out.println(key + " is present");
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @deprecated Please use {@link BigtableDataClient#exists(TargetId, String)} instead.
   */
  @Deprecated
  public boolean exists(String tableId, String rowKey) {
    return ApiExceptions.callAndTranslateApiException(existsAsync(tableId, rowKey));
  }

  /**
   * Confirms synchronously if given row key exists or not.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *   ByteString key = ByteString.copyFromUtf8("key");
   *
   *   boolean isRowPresent = bigtableDataClient.exists(tableId, key);
   *
   *   // Do something with result, for example, display a message
   *   if(isRowPresent) {
   *     System.out.println(key.toStringUtf8() + " is present");
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @deprecated Please use {@link BigtableDataClient#exists(TargetId, ByteString)} instead.
   */
  @Deprecated
  public boolean exists(String tableId, ByteString rowKey) {
    return ApiExceptions.callAndTranslateApiException(existsAsync(tableId, rowKey));
  }

  /**
   * Confirms synchronously if given row key exists or not on the specified {@link TargetId}.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *   String key = "key";
   *
   *   boolean isRowPresent = bigtableDataClient.exists(TableId.of(tableId), key);
   *
   *   // Do something with result, for example, display a message
   *   if(isRowPresent) {
   *     System.out.println(key + " is present");
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public boolean exists(TargetId targetId, String rowKey) {
    return ApiExceptions.callAndTranslateApiException(existsAsync(targetId, rowKey));
  }

  /**
   * Confirms synchronously if given row key exists or not on the specified {@link TargetId}.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *   ByteString key = ByteString.copyFromUtf8("key");
   *
   *   boolean isRowPresent = bigtableDataClient.exists(TableId.of(tableId), key);
   *
   *   // Do something with result, for example, display a message
   *   if(isRowPresent) {
   *     System.out.println(key.toStringUtf8() + " is present");
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public boolean exists(TargetId targetId, ByteString rowKey) {
    return ApiExceptions.callAndTranslateApiException(existsAsync(targetId, rowKey));
  }

  /**
   * Confirms asynchronously if given row key exists or not.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *   final String key = "key";
   *
   *   ApiFuture<Boolean> futureResult = bigtableDataClient.existsAsync(tableId, key);
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Boolean>() {
   *     public void onFailure(Throwable t) {
   *       t.printStackTrace();
   *     }
   *     public void onSuccess(Boolean isRowPresent) {
   *       if(isRowPresent) {
   *         System.out.println(key + " is present");
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#existsAsync(TargetId, String)} instead.
   */
  @Deprecated
  public ApiFuture<Boolean> existsAsync(String tableId, String rowKey) {
    return existsAsync(tableId, ByteString.copyFromUtf8(rowKey));
  }

  /**
   * Confirms asynchronously if given row key exists or not.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *   final ByteString key = ByteString.copyFromUtf8("key");
   *
   *   ApiFuture<Boolean> futureResult = bigtableDataClient.existsAsync(tableId, key);
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Boolean>() {
   *     public void onFailure(Throwable t) {
   *       t.printStackTrace();
   *     }
   *     public void onSuccess(Boolean isRowPresent) {
   *       if(isRowPresent) {
   *         System.out.println(key.toStringUtf8() + " is present");
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#existsAsync(TargetId, ByteString)} instead.
   */
  @Deprecated
  public ApiFuture<Boolean> existsAsync(String tableId, ByteString rowKey) {
    return existsAsync(TableId.of(tableId), rowKey);
  }

  /**
   * Confirms asynchronously if given row key exists or not on the specified {@link TargetId}.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *   final String key = "key";
   *
   *   ApiFuture<Boolean> futureResult = bigtableDataClient.existsAsync(TableId.of(tableId), key);
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Boolean>() {
   *     public void onFailure(Throwable t) {
   *       t.printStackTrace();
   *     }
   *     public void onSuccess(Boolean isRowPresent) {
   *       if(isRowPresent) {
   *         System.out.println(key + " is present");
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public ApiFuture<Boolean> existsAsync(TargetId targetId, String rowKey) {
    return existsAsync(targetId, ByteString.copyFromUtf8(rowKey));
  }

  /**
   * Confirms asynchronously if given row key exists or not on the specified {@link TargetId}.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *   final ByteString key = ByteString.copyFromUtf8("key");
   *
   *   ApiFuture<Boolean> futureResult = bigtableDataClient.existsAsync(TableId.of(tableId), key);
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Boolean>() {
   *     public void onFailure(Throwable t) {
   *       t.printStackTrace();
   *     }
   *     public void onSuccess(Boolean isRowPresent) {
   *       if(isRowPresent) {
   *         System.out.println(key.toStringUtf8() + " is present");
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public ApiFuture<Boolean> existsAsync(TargetId targetId, ByteString rowKey) {
    Query query =
        Query.create(targetId)
            .rowKey(rowKey)
            .filter(
                FILTERS
                    .chain()
                    .filter(FILTERS.limit().cellsPerRow(1))
                    .filter(FILTERS.value().strip()));
    ApiFuture<Row> resultFuture = stub.readRowCallable().futureCall(query);
    return ApiFutures.transform(
        resultFuture,
        new ApiFunction<Row, Boolean>() {
          @Override
          public Boolean apply(Row row) {
            return row != null;
          }
        },
        MoreExecutors.directExecutor());
  }

  /**
   * Convenience method for synchronously reading a single row. If the row does not exist, the value
   * will be null.
   *
   * <p>Sample code:
   *
   * <pre>{code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   * String tableId = "[TABLE]";
   *
   * Row row = bigtableDataClient.readRow(tableId, ByteString.copyFromUtf8("key"));
   * // Do something with row, for example, display all cells
   * if(row != null) {
   *   System.out.println(row.getKey().toStringUtf8());
   *   for(RowCell cell : row.getCells()) {
   *      System.out.printf("Family: %s   Qualifier: %s   Value: %s", cell.getFamily(),
   *         cell.getQualifier().toStringUtf8(), cell.getValue().toStringUtf8());
   *   }
   * }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @deprecated Please use {@link BigtableDataClient#readRow(TargetId, ByteString)} instead.
   */
  @Deprecated
  public Row readRow(String tableId, ByteString rowKey) {
    return ApiExceptions.callAndTranslateApiException(readRowAsync(tableId, rowKey, null));
  }

  /**
   * Convenience method for synchronously reading a single row. If the row does not exist, the value
   * will be null.
   *
   * <p>Sample code:
   *
   * <pre>{code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Row row = bigtableDataClient.readRow(tableId, "key");
   *   // Do something with row, for example, display all cells
   *   if(row != null) {
   *     System.out.println(row.getKey().toStringUtf8());
   *      for(RowCell cell : row.getCells()) {
   *        System.out.printf("Family: %s   Qualifier: %s   Value: %s", cell.getFamily(),
   *           cell.getQualifier().toStringUtf8(), cell.getValue().toStringUtf8());
   *      }
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @deprecated Please use {@link BigtableDataClient#readRow(TargetId, String)} instead.
   */
  @Deprecated
  public Row readRow(String tableId, String rowKey) {
    return ApiExceptions.callAndTranslateApiException(
        readRowAsync(tableId, ByteString.copyFromUtf8(rowKey), null));
  }

  /**
   * Convenience method for synchronously reading a single row. If the row does not exist, the value
   * will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *  // Build the filter expression
   *  Filter filter = FILTERS.chain()
   *         .filter(FILTERS.qualifier().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   Row row = bigtableDataClient.readRow(tableId, "key", filter);
   *   // Do something with row, for example, display all cells
   *   if(row != null) {
   *     System.out.println(row.getKey().toStringUtf8());
   *      for(RowCell cell : row.getCells()) {
   *        System.out.printf("Family: %s   Qualifier: %s   Value: %s", cell.getFamily(),
   *           cell.getQualifier().toStringUtf8(), cell.getValue().toStringUtf8());
   *      }
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @deprecated Please use {@link BigtableDataClient#readRow(TargetId, String, Filter)} instead.
   */
  @Deprecated
  public Row readRow(String tableId, String rowKey, @Nullable Filter filter) {
    return ApiExceptions.callAndTranslateApiException(
        readRowAsync(tableId, ByteString.copyFromUtf8(rowKey), filter));
  }

  /**
   * Convenience method for synchronously reading a single row. If the row does not exist, the value
   * will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *  // Build the filter expression
   *  Filter filter = FILTERS.chain()
   *         .filter(FILTERS.qualifier().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   Row row = bigtableDataClient.readRow(tableId, ByteString.copyFromUtf8("key"), filter);
   *   // Do something with row, for example, display all cells
   *   if(row != null) {
   *     System.out.println(row.getKey().toStringUtf8());
   *      for(RowCell cell : row.getCells()) {
   *        System.out.printf("Family: %s   Qualifier: %s   Value: %s", cell.getFamily(),
   *           cell.getQualifier().toStringUtf8(), cell.getValue().toStringUtf8());
   *      }
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @deprecated Please use {@link BigtableDataClient#readRow(TargetId, ByteString, Filter)}
   *     instead.
   */
  @Deprecated
  public Row readRow(String tableId, ByteString rowKey, @Nullable Filter filter) {
    return ApiExceptions.callAndTranslateApiException(readRowAsync(tableId, rowKey, filter));
  }

  /**
   * Convenience method for synchronously reading a single row on the specified {@link TargetId}. If
   * the row does not exist, the value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Row row = bigtableDataClient.readRow(TableId.of(tableId), ByteString.copyFromUtf8("key"));
   *   // Do something with row, for example, display all cells
   *   if(row != null) {
   *     System.out.println(row.getKey().toStringUtf8());
   *      for(RowCell cell : row.getCells()) {
   *        System.out.printf("Family: %s   Qualifier: %s   Value: %s", cell.getFamily(),
   *           cell.getQualifier().toStringUtf8(), cell.getValue().toStringUtf8());
   *      }
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public Row readRow(TargetId targetId, ByteString rowKey) {
    return ApiExceptions.callAndTranslateApiException(readRowAsync(targetId, rowKey, null));
  }

  /**
   * Convenience method for synchronously reading a single row on the specified {@link TargetId}. If
   * the row does not exist, the value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Row row = bigtableDataClient.readRow(TableId.of(tableId), "key");
   *   // Do something with row, for example, display all cells
   *   if(row != null) {
   *     System.out.println(row.getKey().toStringUtf8());
   *      for(RowCell cell : row.getCells()) {
   *        System.out.printf("Family: %s   Qualifier: %s   Value: %s", cell.getFamily(),
   *           cell.getQualifier().toStringUtf8(), cell.getValue().toStringUtf8());
   *      }
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public Row readRow(TargetId targetId, String rowKey) {
    return ApiExceptions.callAndTranslateApiException(
        readRowAsync(targetId, ByteString.copyFromUtf8(rowKey), null));
  }

  /**
   * Convenience method for synchronously reading a single row on the specified {@link TargetId}. If
   * the row does not exist, the value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   // Build the filter expression
   *   Filter filter = FILTERS.chain()
   *         .filter(FILTERS.qualifier().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   Row row = bigtableDataClient.readRow(TableId.of(tableId), "key", filter);
   *   // Do something with row, for example, display all cells
   *   if(row != null) {
   *     System.out.println(row.getKey().toStringUtf8());
   *      for(RowCell cell : row.getCells()) {
   *        System.out.printf("Family: %s   Qualifier: %s   Value: %s", cell.getFamily(),
   *           cell.getQualifier().toStringUtf8(), cell.getValue().toStringUtf8());
   *      }
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public Row readRow(TargetId targetId, String rowKey, @Nullable Filter filter) {
    return ApiExceptions.callAndTranslateApiException(
        readRowAsync(targetId, ByteString.copyFromUtf8(rowKey), filter));
  }

  /**
   * Convenience method for synchronously reading a single row on the specified {@link TargetId}. If
   * the row does not exist, the value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   // Build the filter expression
   *   Filter filter = FILTERS.chain()
   *         .filter(FILTERS.qualifier().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   Row row = bigtableDataClient.readRow(TableId.of(tableId), ByteString.copyFromUtf8("key"), filter);
   *   // Do something with row, for example, display all cells
   *   if(row != null) {
   *     System.out.println(row.getKey().toStringUtf8());
   *      for(RowCell cell : row.getCells()) {
   *        System.out.printf("Family: %s   Qualifier: %s   Value: %s", cell.getFamily(),
   *           cell.getQualifier().toStringUtf8(), cell.getValue().toStringUtf8());
   *      }
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public Row readRow(TargetId targetId, ByteString rowKey, @Nullable Filter filter) {
    return ApiExceptions.callAndTranslateApiException(readRowAsync(targetId, rowKey, filter));
  }

  /**
   * Convenience method for asynchronously reading a single row. If the row does not exist, the
   * future's value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   ApiFuture<Row> futureResult = bigtableDataClient.readRowAsync(tableId,  "key");
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row result) {
   *       if (result != null) {
   *          System.out.println("Got row: " + result);
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#readRowAsync(TargetId, String)} instead.
   */
  @Deprecated
  public ApiFuture<Row> readRowAsync(String tableId, String rowKey) {
    return readRowAsync(tableId, ByteString.copyFromUtf8(rowKey), null);
  }

  /**
   * Convenience method for asynchronously reading a single row. If the row does not exist, the
   * future's value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   ApiFuture<Row> futureResult = bigtableDataClient.readRowAsync(tableId,  ByteString.copyFromUtf8("key"));
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row row) {
   *       if (result != null) {
   *          System.out.println("Got row: " + result);
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#readRowAsync(TargetId, ByteString)} instead.
   */
  @Deprecated
  public ApiFuture<Row> readRowAsync(String tableId, ByteString rowKey) {
    return readRowAsync(tableId, rowKey, null);
  }

  /**
   * Convenience method for asynchronously reading a single row. If the row does not exist, the
   * future's value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *  // Build the filter expression
   *   Filters.Filter filter = FILTERS.chain()
   *         .filter(FILTERS.qualifier().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   ApiFuture<Row> futureResult = bigtableDataClient.readRowAsync(tableId, "key", filter);
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row row) {
   *       if (result != null) {
   *          System.out.println("Got row: " + result);
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#readRowAsync(TargetId, String, Filter)}
   *     instead.
   */
  @Deprecated
  public ApiFuture<Row> readRowAsync(String tableId, String rowKey, @Nullable Filter filter) {
    return readRowAsync(tableId, ByteString.copyFromUtf8(rowKey), filter);
  }

  /**
   * Convenience method for asynchronously reading a single row. If the row does not exist, the
   * future's value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *  // Build the filter expression
   *  Filters.Filter filter = FILTERS.chain()
   *         .filter(FILTERS.qualifier().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   ApiFuture<Row> futureResult = bigtableDataClient.readRowAsync(tableId, ByteString.copyFromUtf8("key"), filter);
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row row) {
   *       if (result != null) {
   *          System.out.println("Got row: " + result);
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#readRowAsync(TargetId, ByteString, Filter)}
   *     instead.
   */
  @Deprecated
  public ApiFuture<Row> readRowAsync(String tableId, ByteString rowKey, @Nullable Filter filter) {
    return readRowAsync(TableId.of(tableId), rowKey, filter);
  }

  /**
   * Convenience method for asynchronously reading a single row on the specified {@link TargetId}.
   * If the row does not exist, the future's value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   ApiFuture<Row> futureResult = bigtableDataClient.readRowAsync(TableId.of(tableId), "key");
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row result) {
   *       if (result != null) {
   *          System.out.println("Got row: " + result);
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public ApiFuture<Row> readRowAsync(TargetId targetId, String rowKey) {
    return readRowAsync(targetId, ByteString.copyFromUtf8(rowKey), null);
  }

  /**
   * Convenience method for asynchronously reading a single row on the specified {@link TargetId}.
   * If the row does not exist, the future's value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   ApiFuture<Row> futureResult = bigtableDataClient.readRowAsync(TableId.of(tableId), ByteString.copyFromUtf8("key"));
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row result) {
   *       if (result != null) {
   *          System.out.println("Got row: " + result);
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public ApiFuture<Row> readRowAsync(TargetId targetId, ByteString rowKey) {
    return readRowAsync(targetId, rowKey, null);
  }

  /**
   * Convenience method for asynchronously reading a single row on the specified {@link TargetId}.
   * If the row does not exist, the future's value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   // Build the filter expression
   *   Filters.Filter filter = FILTERS.chain()
   *         .filter(FILTERS.qualifier().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   ApiFuture<Row> futureResult = bigtableDataClient.readRowAsync(TableId.of(tableId), "key", filter);
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row result) {
   *       if (result != null) {
   *          System.out.println("Got row: " + result);
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public ApiFuture<Row> readRowAsync(TargetId targetId, String rowKey, @Nullable Filter filter) {
    return readRowAsync(targetId, ByteString.copyFromUtf8(rowKey), filter);
  }

  /**
   * Convenience method for asynchronously reading a single row on the specified {@link TargetId}.
   * If the row does not exist, the value will be null.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   // Build the filter expression
   *   Filters.Filter filter = FILTERS.chain()
   *         .filter(FILTERS.qualifier().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   ApiFuture<Row> futureResult = bigtableDataClient.readRowAsync(TableId.of(tableId), ByteString.copyFromUtf8("key"), filter);
   *
   *   ApiFutures.addCallback(futureResult, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row result) {
   *       if (result != null) {
   *          System.out.println("Got row: " + result);
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public ApiFuture<Row> readRowAsync(
      TargetId targetId, ByteString rowKey, @Nullable Filter filter) {
    Query query = Query.create(targetId).rowKey(rowKey);
    if (filter != null) {
      query = query.filter(filter);
    }
    return readRowCallable().futureCall(query);
  }

  /**
   * Reads a single row. The returned callable object allows for customization of api invocation.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Query query = Query.create(tableId)
   *          .rowKey("[KEY]")
   *          .filter(FILTERS.qualifier().regex("[COLUMN PREFIX].*"));
   *
   *   // Synchronous invocation
   *   try {
   *     Row row = bigtableDataClient.readRowCallable().call(query);
   *     if (row == null) {
   *       System.out.println("Row not found");
   *     }
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // Asynchronous invocation
   *   ApiFuture<Row> rowFuture = bigtableDataClient.readRowCallable().futureCall(query);
   *
   *   ApiFutures.addCallback(rowFuture, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row row) {
   *       if (row == null) {
   *         System.out.println("Row not found");
   *       }
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @see UnaryCallable For call styles.
   * @see Query For query options.
   * @see com.google.cloud.bigtable.data.v2.models.Filters For the filter building DSL.
   */
  public UnaryCallable<Query, Row> readRowCallable() {
    return stub.readRowCallable();
  }

  /**
   * Reads a single row. This callable allows for customization of the logical representation of a
   * row. It's meant for advanced use cases.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Query query = Query.create(tableId)
   *          .rowKey("[KEY]")
   *          .filter(FILTERS.qualifier().regex("[COLUMN PREFIX].*"));
   *
   *   // Synchronous invocation
   *   CustomRow row = bigtableDataClient.readRowCallable(new CustomRowAdapter()).call(query));
   *   // Do something with row
   * }
   * }</pre>
   *
   * @see ServerStreamingCallable For call styles.
   * @see Query For query options.
   * @see com.google.cloud.bigtable.data.v2.models.Filters For the filter building DSL.
   */
  public <RowT> UnaryCallable<Query, RowT> readRowCallable(RowAdapter<RowT> rowAdapter) {
    return stub.createReadRowCallable(rowAdapter);
  }

  /**
   * Convenience method for synchronously streaming the results of a {@link Query}. The returned
   * ServerStream instance is not threadsafe, it can only be used from single thread.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * // Import the filter DSL
   * import static com.google.cloud.bigtable.data.v2.models.Filters.FILTERS;
   *
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Query query = Query.create(tableId)
   *          .range("[START KEY]", "[END KEY]")
   *          .filter(FILTERS.qualifier().regex("[COLUMN PREFIX].*"));
   *
   *   try {
   *     ServerStream<Row> stream = bigtableDataClient.readRows(query);
   *     int count = 0;
   *
   *     // Iterator style
   *     for (Row row : stream) {
   *       if (++count > 10) {
   *         stream.cancel();
   *         break;
   *       }
   *       // Do something with row
   *     }
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   * }
   * }</pre>
   *
   * @see ServerStreamingCallable For call styles.
   * @see Query For query options.
   * @see com.google.cloud.bigtable.data.v2.models.Filters For the filter building DSL.
   */
  public ServerStream<Row> readRows(Query query) {
    return readRowsCallable().call(query);
  }

  /**
   * Convenience method for asynchronously streaming the results of a {@link Query}.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Query query = Query.create(tableId)
   *          .range("[START KEY]", "[END KEY]")
   *          .filter(FILTERS.qualifier().regex("[COLUMN PREFIX].*"));
   *
   *   bigtableDataClient.readRowsAsync(query, new ResponseObserver<Row>() {
   *     StreamController controller;
   *     int count = 0;
   *
   *     public void onStart(StreamController controller) {
   *       this.controller = controller;
   *     }
   *     public void onResponse(Row row) {
   *       if (++count > 10) {
   *         controller.cancel();
   *         return;
   *       }
   *       // Do something with Row
   *     }
   *     public void onError(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onComplete() {
   *       // Handle stream completion
   *     }
   *   });
   * }
   * }</pre>
   */
  public void readRowsAsync(Query query, ResponseObserver<Row> observer) {
    readRowsCallable().call(query, observer);
  }

  /**
   * Streams back the results of the query. The returned callable object allows for customization of
   * api invocation.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Query query = Query.create(tableId)
   *          .range("[START KEY]", "[END KEY]")
   *          .filter(FILTERS.qualifier().regex("[COLUMN PREFIX].*"));
   *
   *   // Iterator style
   *   try {
   *     for(Row row : bigtableDataClient.readRowsCallable().call(query)) {
   *       // Do something with row
   *     }
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // Sync style
   *   try {
   *     List<Row> rows = bigtableDataClient.readRowsCallable().all().call(query);
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // Point look up
   *   ApiFuture<Row> rowFuture = bigtableDataClient.readRowsCallable().first().futureCall(query);
   *
   *   ApiFutures.addCallback(rowFuture, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row result) {
   *       System.out.println("Got row: " + result);
   *     }
   *   }, MoreExecutors.directExecutor());
   *
   *   // etc
   * }
   * }</pre>
   *
   * @see ServerStreamingCallable For call styles.
   * @see Query For query options.
   * @see com.google.cloud.bigtable.data.v2.models.Filters For the filter building DSL.
   */
  public ServerStreamingCallable<Query, Row> readRowsCallable() {
    return stub.readRowsCallable();
  }

  /**
   * Streams back the results of the read query & omits large rows. The returned callable object
   * allows for customization of api invocation.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Query query = Query.create(tableId)
   *          .range("[START KEY]", "[END KEY]")
   *          .filter(FILTERS.qualifier().regex("[COLUMN PREFIX].*"));
   *
   *   // Iterator style
   *   try {
   *     for(Row row : bigtableDataClient.skipLargeRowsCallable().call(query)) {
   *       // Do something with row
   *     }
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // Sync style
   *   try {
   *     List<Row> rows = bigtableDataClient.skipLargeRowsCallable().all().call(query);
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // etc
   * }
   * }</pre>
   *
   * @see ServerStreamingCallable For call styles.
   * @see Query For query options.
   * @see com.google.cloud.bigtable.data.v2.models.Filters For the filter building DSL.
   */
  @InternalApi("only to be used by Bigtable beam connector")
  public ServerStreamingCallable<Query, Row> skipLargeRowsCallable() {
    return stub.skipLargeRowsCallable();
  }

  /**
   * Streams back the results of the query. This callable allows for customization of the logical
   * representation of a row. It's meant for advanced use cases.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Query query = Query.create(tableId)
   *          .range("[START KEY]", "[END KEY]")
   *          .filter(FILTERS.qualifier().regex("[COLUMN PREFIX].*"));
   *
   *   // Iterator style
   *   try {
   *     for(CustomRow row : bigtableDataClient.readRowsCallable(new CustomRowAdapter()).call(query)) {
   *       // Do something with row
   *     }
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   * }
   * }</pre>
   *
   * @see ServerStreamingCallable For call styles.
   * @see Query For query options.
   * @see com.google.cloud.bigtable.data.v2.models.Filters For the filter building DSL.
   */
  public <RowT> ServerStreamingCallable<Query, RowT> readRowsCallable(RowAdapter<RowT> rowAdapter) {
    return stub.createReadRowsCallable(rowAdapter);
  }

  /**
   * This is an internal API, it is subject to breaking changes and should not be relied on by user
   * code
   *
   * <p>Streams back the results of the query skipping the large-rows. This callable allows for
   * customization of the logical representation of a row. It's meant for advanced use cases.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   Query query = Query.create(tableId)
   *          .range("[START KEY]", "[END KEY]")
   *          .filter(FILTERS.qualifier().regex("[COLUMN PREFIX].*"));
   *
   *   // Iterator style
   *   try {
   *     for(CustomRow row : bigtableDataClient.skipLargeRowsCallable(new CustomRowAdapter()).call(query)) {
   *       // Do something with row
   *     }
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   * }
   * }</pre>
   *
   * @see ServerStreamingCallable For call styles.
   * @see Query For query options.
   * @see com.google.cloud.bigtable.data.v2.models.Filters For the filter building DSL.
   */
  @InternalApi("only to be used by Bigtable beam connector")
  public <RowT> ServerStreamingCallable<Query, RowT> skipLargeRowsCallable(
      RowAdapter<RowT> rowAdapter) {
    return stub.createSkipLargeRowsCallable(rowAdapter);
  }

  /**
   * Convenience method to synchronously return a sample of row keys in the table. The returned row
   * keys will delimit contiguous sections of the table of approximately equal size, which can be
   * used to break up the data for distributed tasks like mapreduces.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE_ID]";
   *
   *   List<KeyOffset> keyOffsets = bigtableDataClient.sampleRowKeys(tableId);
   *   for(KeyOffset keyOffset : keyOffsets) {
   *   // Do something with keyOffset
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @deprecated Please use {@link BigtableDataClient#sampleRowKeys(TargetId)} instead.
   */
  @Deprecated
  public List<KeyOffset> sampleRowKeys(String tableId) {
    return ApiExceptions.callAndTranslateApiException(sampleRowKeysAsync(tableId));
  }

  /**
   * Convenience method to synchronously return a sample of row keys on the specified {@link
   * TargetId}.
   *
   * <p>The returned row keys will delimit contiguous sections of the table of approximately equal
   * size, which can be used to break up the data for distributed tasks like mapreduces.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE_ID]";
   *
   *   List<KeyOffset> keyOffsets = bigtableDataClient.sampleRowKeys(TableId.of(tableId));
   *   for(KeyOffset keyOffset : keyOffsets) {
   *   // Do something with keyOffset
   *   }
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   */
  public List<KeyOffset> sampleRowKeys(TargetId targetId) {
    return ApiExceptions.callAndTranslateApiException(sampleRowKeysAsync(targetId));
  }

  /**
   * Convenience method to asynchronously return a sample of row keys in the table. The returned row
   * keys will delimit contiguous sections of the table of approximately equal size, which can be
   * used to break up the data for distributed tasks like mapreduces.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableClient bigtableDataClient = BigtableClient.create("[PROJECT]", "[INSTANCE]")) {
   *   ApiFuture<List<KeyOffset>> keyOffsetsFuture = bigtableClient.sampleRowKeysAsync("[TABLE]");
   *
   *   ApiFutures.addCallback(keyOffsetsFuture, new ApiFutureCallback<List<KeyOffset>>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to sample keys of a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(List<KeyOffset> keyOffsets) {
   *       System.out.println("Got key offsets: " + keyOffsets);
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#sampleRowKeysAsync(TargetId)} instead.
   */
  @Deprecated
  public ApiFuture<List<KeyOffset>> sampleRowKeysAsync(String tableId) {
    return sampleRowKeysAsync(TableId.of(tableId));
  }

  /**
   * Convenience method to asynchronously return a sample of row keys on the specified {@link
   * TargetId}.
   *
   * <p>The returned row keys will delimit contiguous sections of the table of approximately equal
   * size, which can be used to break up the data for distributed tasks like mapreduces.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableClient bigtableDataClient = BigtableClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE_ID]";
   *   ApiFuture<List<KeyOffset>> keyOffsetsFuture = bigtableClient.sampleRowKeysAsync(TableId.of(tableId));
   *
   *   ApiFutures.addCallback(keyOffsetsFuture, new ApiFutureCallback<List<KeyOffset>>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to sample keys of a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(List<KeyOffset> keyOffsets) {
   *       System.out.println("Got key offsets: " + keyOffsets);
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public ApiFuture<List<KeyOffset>> sampleRowKeysAsync(TargetId targetId) {
    return sampleRowKeysCallableWithRequest().futureCall(SampleRowKeysRequest.create(targetId));
  }

  /**
   * Returns a sample of row keys in the table. The returned row keys will delimit contiguous
   * sections of the table of approximately equal size, which can be used to break up the data for
   * distributed tasks like mapreduces. The returned callable object allows for customization of api
   * invocation.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   // Synchronous invocation
   *   try {
   *     List<KeyOffset> keyOffsets = bigtableDataClient.sampleRowKeysCallable().call("[TABLE]");
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to sample keys of a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // Asynchronous invocation
   *   ApiFuture<List<KeyOffset>> keyOffsetsFuture = bigtableDataClient.sampleRowKeysCallable().futureCall("[TABLE]");
   *
   *   ApiFutures.addCallback(keyOffsetsFuture, new ApiFutureCallback<List<KeyOffset>>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to sample keys of a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(List<KeyOffset> keyOffsets) {
   *       System.out.println("Got key offsets: " + keyOffsets);
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#sampleRowKeysCallableWithRequest()} instead.
   */
  @Deprecated
  public UnaryCallable<String, List<KeyOffset>> sampleRowKeysCallable() {
    return stub.sampleRowKeysCallable();
  }

  /**
   * Returns a sample of row keys in the table. This callable allows takes a {@link
   * SampleRowKeysRequest} object rather than a String input, and thus can be used to sample against
   * a specified {@link TargetId}.
   *
   * <p>The returned row keys will delimit contiguous sections of the table of approximately equal
   * size, which can be used to break up the data for distributed tasks like mapreduces. The
   * returned callable object allows for customization of api invocation.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   SampleRowKeysRequest sampleRowKeys = SampleRowKeysRequest.create(TableId.of("[TABLE]"));
   *   // Synchronous invocation
   *   try {
   *     List<KeyOffset> keyOffsets = bigtableDataClient.sampleRowKeysCallableWithRequest().call(sampleRowKeys);
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to sample keys of a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // Asynchronous invocation
   *   ApiFuture<List<KeyOffset>> keyOffsetsFuture = bigtableDataClient.sampleRowKeysCallableWithRequest().futureCall(sampleRowKeys);
   *
   *   ApiFutures.addCallback(keyOffsetsFuture, new ApiFutureCallback<List<KeyOffset>>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to sample keys of a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(List<KeyOffset> keyOffsets) {
   *       System.out.println("Got key offsets: " + keyOffsets);
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public UnaryCallable<SampleRowKeysRequest, List<KeyOffset>> sampleRowKeysCallableWithRequest() {
    return stub.sampleRowKeysCallableWithRequest();
  }

  /**
   * Convenience method to synchronously mutate a single row atomically. Cells already present in
   * the row are left unchanged unless explicitly changed by the {@link RowMutation}.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   RowMutation mutation = RowMutation.create("[TABLE]", "[ROW KEY]")
   *     .setCell("[FAMILY NAME]", "[QUALIFIER]", "[VALUE]");
   *
   *   bigtableDataClient.mutateRow(mutation);
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   */
  public void mutateRow(RowMutation rowMutation) {
    ApiExceptions.callAndTranslateApiException(mutateRowAsync(rowMutation));
  }

  /**
   * Convenience method to asynchronously mutate a single row atomically. Cells already present in
   * the row are left unchanged unless explicitly changed by the {@link RowMutation}.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   RowMutation mutation = RowMutation.create("[TABLE]", "[ROW KEY]")
   *     .setCell("[FAMILY NAME]", "[QUALIFIER]", "[VALUE]");
   *
   *   ApiFuture<Void> future = bigtableDataClient.mutateRowAsync(mutation);
   *
   *   ApiFutures.addCallback(future, new ApiFutureCallback<Void>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to mutate a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Void ignored) {
   *       System.out.println("Successfully applied mutation");
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   */
  public ApiFuture<Void> mutateRowAsync(RowMutation rowMutation) {
    return mutateRowCallable().futureCall(rowMutation);
  }

  /**
   * Mutates a single row atomically. Cells already present in the row are left unchanged unless
   * explicitly changed by the {@link RowMutation}.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   RowMutation mutation = RowMutation.create("[TABLE]", "[ROW KEY]")
   *     .setCell("[FAMILY NAME]", "[QUALIFIER]", "[VALUE]");
   *
   *   // Sync style
   *   try {
   *     bigtableDataClient.mutateRowCallable().call(mutation);
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to mutate a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   * }
   * }</pre>
   */
  public UnaryCallable<RowMutation, Void> mutateRowCallable() {
    return stub.mutateRowCallable();
  }

  /**
   * Convenience method to mutate multiple rows in a batch. Each individual row is mutated
   * atomically as in MutateRow, but the entire batch is not executed atomically. This method
   * expects the mutations to be pre-batched.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   BulkMutation batch = BulkMutation.create("[TABLE]");
   *   for (String someValue : someCollection) {
   *     batch.add("[ROW KEY]", Mutation.create().setCell("[FAMILY NAME]", "[QUALIFIER]", "[VALUE]"));
   *   }
   *   bigtableDataClient.bulkMutateRows(batch);
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * } catch(MutateRowsException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   * @throws com.google.cloud.bigtable.data.v2.models.MutateRowsException if any of the entries
   *     failed to be applied
   */
  public void bulkMutateRows(BulkMutation mutation) {
    ApiExceptions.callAndTranslateApiException(bulkMutateRowsAsync(mutation));
  }

  /**
   * Mutates multiple rows in a batch. Each individual row is mutated atomically as in MutateRow,
   * but the entire batch is not executed atomically. The returned Batcher instance is not
   * threadsafe, it can only be used from single thread.
   *
   * <p>Sample Code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   try (Batcher<RowMutationEntry, Void> batcher = bigtableDataClient.newBulkMutationBatcher("[TABLE]")) {
   *     for (String someValue : someCollection) {
   *       ApiFuture<Void> entryFuture =
   *           batcher.add(
   *               RowMutationEntry.create("[ROW KEY]")
   *                   .setCell("[FAMILY NAME]", "[QUALIFIER]", "[VALUE]"));
   *     }
   *
   *     // Blocks until mutations are applied on all submitted row entries.
   *     batcher.flush();
   *   }
   *   // Before `batcher` is closed, all remaining(If any) mutations are applied.
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#newBulkMutationBatcher(TargetId)} instead.
   */
  @Deprecated
  public Batcher<RowMutationEntry, Void> newBulkMutationBatcher(@Nonnull String tableId) {
    return newBulkMutationBatcher(tableId, null);
  }

  /**
   * Mutates multiple rows in a batch. Each individual row is mutated atomically as in MutateRow,
   * but the entire batch is not executed atomically. The returned Batcher instance is not
   * threadsafe, it can only be used from single thread. This method allows customization of the
   * underlying RPCs by passing in a {@link com.google.api.gax.grpc.GrpcCallContext}. The same
   * context will be reused for all batches. This can be used to customize things like per attempt
   * timeouts.
   *
   * <p>Sample Code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   try (Batcher<RowMutationEntry, Void> batcher = bigtableDataClient.newBulkMutationBatcher("[TABLE]", GrpcCallContext.createDefault().withTimeout(Duration.ofSeconds(10)))) {
   *     for (String someValue : someCollection) {
   *       ApiFuture<Void> entryFuture =
   *           batcher.add(
   *               RowMutationEntry.create("[ROW KEY]")
   *                   .setCell("[FAMILY NAME]", "[QUALIFIER]", "[VALUE]"));
   *     }
   *
   *     // Blocks until mutations are applied on all submitted row entries.
   *     batcher.flush();
   *   }
   *   // Before `batcher` is closed, all remaining(If any) mutations are applied.
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#newBulkMutationBatcher(TargetId,
   *     GrpcCallContext)} instead.
   */
  @Deprecated
  @BetaApi("This surface is likely to change as the batching surface evolves.")
  public Batcher<RowMutationEntry, Void> newBulkMutationBatcher(
      @Nonnull String tableId, @Nullable GrpcCallContext ctx) {
    return stub.newMutateRowsBatcher(tableId, ctx);
  }

  /**
   * Mutates multiple rows in a batch on the specified {@link TargetId}.
   *
   * <p>Each individual row is mutated atomically as in MutateRow, but the entire batch is not
   * executed atomically. The returned Batcher instance is not threadsafe, it can only be used from
   * single thread.
   *
   * <p>Sample Code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   try (Batcher<RowMutationEntry, Void> batcher = bigtableDataClient.newBulkMutationBatcher(TableId.of("[TABLE]"))) {
   *     for (String someValue : someCollection) {
   *       ApiFuture<Void> entryFuture =
   *           batcher.add(
   *               RowMutationEntry.create("[ROW KEY]")
   *                   .setCell("[FAMILY NAME]", "[QUALIFIER]", "[VALUE]"));
   *     }
   *
   *     // Blocks until mutations are applied on all submitted row entries.
   *     batcher.flush();
   *   }
   *   // Before `batcher` is closed, all remaining(If any) mutations are applied.
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  @BetaApi("This surface is likely to change as the batching surface evolves.")
  public Batcher<RowMutationEntry, Void> newBulkMutationBatcher(TargetId targetId) {
    return newBulkMutationBatcher(targetId, null);
  }

  /**
   * Mutates multiple rows in a batch on the specified {@link TargetId}.
   *
   * <p>Each individual row is mutated atomically as in MutateRow, but the entire batch is not
   * executed atomically. The returned Batcher instance is not threadsafe, it can only be used from
   * single thread. This method allows customization of the underlying RPCs by passing in a {@link
   * com.google.api.gax.grpc.GrpcCallContext}. The same context will be reused for all batches. This
   * can be used to customize things like per attempt timeouts.
   *
   * <p>Sample Code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   GrpcCallContext ctx = GrpcCallContext.createDefault().withTimeout(Duration.ofSeconds(10));
   *   try (Batcher<RowMutationEntry, Void> batcher = bigtableDataClient.newBulkMutationBatcher(TableId.of("[TABLE]"), ctx)) {
   *     for (String someValue : someCollection) {
   *       ApiFuture<Void> entryFuture =
   *           batcher.add(
   *               RowMutationEntry.create("[ROW KEY]")
   *                   .setCell("[FAMILY NAME]", "[QUALIFIER]", "[VALUE]"));
   *     }
   *
   *     // Blocks until mutations are applied on all submitted row entries.
   *     batcher.flush();
   *   }
   *   // Before `batcher` is closed, all remaining(If any) mutations are applied.
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  @BetaApi("This surface is likely to change as the batching surface evolves.")
  public Batcher<RowMutationEntry, Void> newBulkMutationBatcher(
      TargetId targetId, @Nullable GrpcCallContext ctx) {
    return stub.newMutateRowsBatcher(targetId, ctx);
  }

  /**
   * Reads rows for given tableId in a batch. If the row does not exist, the value will be null. The
   * returned Batcher instance is not threadsafe, it can only be used from a single thread.
   *
   * <p>Performance notice: The ReadRows protocol requires that rows are sent in ascending key
   * order, which means that the keys are processed sequentially on the server-side, so batching
   * allows improving throughput but not latency. Lower latencies can be achieved by sending smaller
   * requests concurrently.
   *
   * <p>Sample Code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   List<ApiFuture<Row>> rows = new ArrayList<>();
   *
   *   try (Batcher<ByteString, Row> batcher = bigtableDataClient.newBulkReadRowsBatcher("[TABLE]")) {
   *     for (String someValue : someCollection) {
   *       ApiFuture<Row> rowFuture =
   *           batcher.add(ByteString.copyFromUtf8("[ROW KEY]"));
   *       rows.add(rowFuture);
   *     }
   *
   *     // [Optional] Sends collected elements for batching asynchronously.
   *     batcher.sendOutstanding();
   *
   *     // [Optional] Invokes sendOutstanding() and awaits until all pending entries are resolved.
   *     batcher.flush();
   *   }
   *   // batcher.close() invokes `flush()` which will in turn invoke `sendOutstanding()` with await for
   *   pending batches until its resolved.
   *
   *   List<Row> actualRows = ApiFutures.allAsList(rows).get();
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#newBulkReadRowsBatcher(TargetId)} instead.
   */
  @Deprecated
  public Batcher<ByteString, Row> newBulkReadRowsBatcher(String tableId) {
    return newBulkReadRowsBatcher(tableId, null);
  }

  /**
   * Reads rows for given tableId and filter criteria in a batch. If the row does not exist, the
   * value will be null. The returned Batcher instance is not threadsafe, it can only be used from a
   * single thread.
   *
   * <p>Performance notice: The ReadRows protocol requires that rows are sent in ascending key
   * order, which means that the keys are processed sequentially on the server-side, so batching
   * allows improving throughput but not latency. Lower latencies can be achieved by sending smaller
   * requests concurrently.
   *
   * <p>Sample Code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *
   *  // Build the filter expression
   *  Filter filter = FILTERS.chain()
   *         .filter(FILTERS.key().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   List<ApiFuture<Row>> rows = new ArrayList<>();
   *
   *   try (Batcher<ByteString, Row> batcher = bigtableDataClient.newBulkReadRowsBatcher("[TABLE]", filter)) {
   *     for (String someValue : someCollection) {
   *       ApiFuture<Row> rowFuture =
   *           batcher.add(ByteString.copyFromUtf8("[ROW KEY]"));
   *       rows.add(rowFuture);
   *     }
   *
   *     // [Optional] Sends collected elements for batching asynchronously.
   *     batcher.sendOutstanding();
   *
   *     // [Optional] Invokes sendOutstanding() and awaits until all pending entries are resolved.
   *     batcher.flush();
   *   }
   *   // batcher.close() invokes `flush()` which will in turn invoke `sendOutstanding()` with await for
   *   pending batches until its resolved.
   *
   *   List<Row> actualRows = ApiFutures.allAsList(rows).get();
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#newBulkReadRowsBatcher(TargetId, Filter)}
   *     instead.
   */
  @Deprecated
  public Batcher<ByteString, Row> newBulkReadRowsBatcher(
      String tableId, @Nullable Filters.Filter filter) {
    return newBulkReadRowsBatcher(tableId, filter, null);
  }

  /**
   * Reads rows for given tableId and filter criteria in a batch. If the row does not exist, the
   * value will be null. The returned Batcher instance is not threadsafe, it can only be used from a
   * single thread. This method allows customization of the underlying RPCs by passing in a {@link
   * com.google.api.gax.grpc.GrpcCallContext}. The same context will be reused for all batches. This
   * can be used to customize things like per attempt timeouts.
   *
   * <p>Performance notice: The ReadRows protocol requires that rows are sent in ascending key
   * order, which means that the keys are processed sequentially on the server-side, so batching
   * allows improving throughput but not latency. Lower latencies can be achieved by sending smaller
   * requests concurrently.
   *
   * <p>Sample Code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *
   *  // Build the filter expression
   *  Filter filter = FILTERS.chain()
   *         .filter(FILTERS.key().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   List<ApiFuture<Row>> rows = new ArrayList<>();
   *
   *   try (Batcher<ByteString, Row> batcher = bigtableDataClient.newBulkReadRowsBatcher(
   *    "[TABLE]", filter, GrpcCallContext.createDefault().withTimeout(Duration.ofSeconds(10)))) {
   *     for (String someValue : someCollection) {
   *       ApiFuture<Row> rowFuture =
   *           batcher.add(ByteString.copyFromUtf8("[ROW KEY]"));
   *       rows.add(rowFuture);
   *     }
   *
   *     // [Optional] Sends collected elements for batching asynchronously.
   *     batcher.sendOutstanding();
   *
   *     // [Optional] Invokes sendOutstanding() and awaits until all pending entries are resolved.
   *     batcher.flush();
   *   }
   *   // batcher.close() invokes `flush()` which will in turn invoke `sendOutstanding()` with await for
   *   pending batches until its resolved.
   *
   *   List<Row> actualRows = ApiFutures.allAsList(rows).get();
   * }
   * }</pre>
   *
   * @deprecated Please use {@link BigtableDataClient#newBulkReadRowsBatcher(TargetId, Filter,
   *     GrpcCallContext)} instead.
   */
  @Deprecated
  public Batcher<ByteString, Row> newBulkReadRowsBatcher(
      String tableId, @Nullable Filters.Filter filter, @Nullable GrpcCallContext ctx) {
    return newBulkReadRowsBatcher(TableId.of(tableId), filter, ctx);
  }

  /**
   * Reads rows for given tableId in a batch on the specified {@link TargetId}.
   *
   * <p>If the row does not exist, the value will be null. The returned Batcher instance is not
   * threadsafe, it can only be used from a single thread.
   *
   * <p>Performance notice: The ReadRows protocol requires that rows are sent in ascending key
   * order, which means that the keys are processed sequentially on the server-side, so batching
   * allows improving throughput but not latency. Lower latencies can be achieved by sending smaller
   * requests concurrently.
   *
   * <p>Sample Code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *
   *   List<ApiFuture<Row>> rows = new ArrayList<>();
   *
   *   try (Batcher<ByteString, Row> batcher = bigtableDataClient.newBulkReadRowsBatcher(TableId.of("[TABLE]"))) {
   *     for (String someValue : someCollection) {
   *       ApiFuture<Row> rowFuture =
   *           batcher.add(ByteString.copyFromUtf8("[ROW KEY]"));
   *       rows.add(rowFuture);
   *     }
   *
   *     // [Optional] Sends collected elements for batching asynchronously.
   *     batcher.sendOutstanding();
   *
   *     // [Optional] Invokes sendOutstanding() and awaits until all pending entries are resolved.
   *     batcher.flush();
   *   }
   *   // batcher.close() invokes `flush()` which will in turn invoke `sendOutstanding()` with await for
   *   pending batches until its resolved.
   *
   *   List<Row> actualRows = ApiFutures.allAsList(rows).get();
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public Batcher<ByteString, Row> newBulkReadRowsBatcher(TargetId targetId) {
    return newBulkReadRowsBatcher(targetId, null);
  }

  /**
   * Reads rows for given tableId and filter criteria in a batch on the specified {@link TargetId}.
   *
   * <p>If the row does not exist, the value will be null. The returned Batcher instance is not
   * threadsafe, it can only be used from a single thread.
   *
   * <p>Performance notice: The ReadRows protocol requires that rows are sent in ascending key
   * order, which means that the keys are processed sequentially on the server-side, so batching
   * allows improving throughput but not latency. Lower latencies can be achieved by sending smaller
   * requests concurrently.
   *
   * <p>Sample Code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *
   *  // Build the filter expression
   *  Filter filter = FILTERS.chain()
   *         .filter(FILTERS.key().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   List<ApiFuture<Row>> rows = new ArrayList<>();
   *
   *   try (Batcher<ByteString, Row> batcher = bigtableDataClient.newBulkReadRowsBatcher(TableId.of("[TABLE]"), filter)) {
   *     for (String someValue : someCollection) {
   *       ApiFuture<Row> rowFuture =
   *           batcher.add(ByteString.copyFromUtf8("[ROW KEY]"));
   *       rows.add(rowFuture);
   *     }
   *
   *     // [Optional] Sends collected elements for batching asynchronously.
   *     batcher.sendOutstanding();
   *
   *     // [Optional] Invokes sendOutstanding() and awaits until all pending entries are resolved.
   *     batcher.flush();
   *   }
   *   // batcher.close() invokes `flush()` which will in turn invoke `sendOutstanding()` with await for
   *   pending batches until its resolved.
   *
   *   List<Row> actualRows = ApiFutures.allAsList(rows).get();
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public Batcher<ByteString, Row> newBulkReadRowsBatcher(
      TargetId targetId, @Nullable Filter filter) {
    return newBulkReadRowsBatcher(targetId, filter, null);
  }

  /**
   * Reads rows for given tableId and filter criteria in a batch on the specified {@link TargetId}.
   *
   * <p>If the row does not exist, the value will be null. The returned Batcher instance is not
   * threadsafe, it can only be used from a single thread. This method allows customization of the
   * underlying RPCs by passing in a {@link com.google.api.gax.grpc.GrpcCallContext}. The same
   * context will be reused for all batches. This can be used to customize things like per attempt
   * timeouts.
   *
   * <p>Performance notice: The ReadRows protocol requires that rows are sent in ascending key
   * order, which means that the keys are processed sequentially on the server-side, so batching
   * allows improving throughput but not latency. Lower latencies can be achieved by sending smaller
   * requests concurrently.
   *
   * <p>Sample Code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *
   *  // Build the filter expression
   *  Filter filter = FILTERS.chain()
   *         .filter(FILTERS.key().regex("prefix.*"))
   *         .filter(FILTERS.limit().cellsPerRow(10));
   *
   *   List<ApiFuture<Row>> rows = new ArrayList<>();
   *
   *   try (Batcher<ByteString, Row> batcher = bigtableDataClient.newBulkReadRowsBatcher(
   *    TableId.of("[TABLE]"), filter, GrpcCallContext.createDefault().withTimeout(Duration.ofSeconds(10)))) {
   *     for (String someValue : someCollection) {
   *       ApiFuture<Row> rowFuture =
   *           batcher.add(ByteString.copyFromUtf8("[ROW KEY]"));
   *       rows.add(rowFuture);
   *     }
   *
   *     // [Optional] Sends collected elements for batching asynchronously.
   *     batcher.sendOutstanding();
   *
   *     // [Optional] Invokes sendOutstanding() and awaits until all pending entries are resolved.
   *     batcher.flush();
   *   }
   *   // batcher.close() invokes `flush()` which will in turn invoke `sendOutstanding()` with await for
   *   pending batches until its resolved.
   *
   *   List<Row> actualRows = ApiFutures.allAsList(rows).get();
   * }
   * }</pre>
   *
   * @see com.google.cloud.bigtable.data.v2.models.AuthorizedViewId
   * @see TableId
   */
  public Batcher<ByteString, Row> newBulkReadRowsBatcher(
      TargetId targetId, @Nullable Filter filter, @Nullable GrpcCallContext ctx) {
    Query query = Query.create(targetId);
    if (filter != null) {
      query = query.filter(filter);
    }
    return stub.newBulkReadRowsBatcher(query, ctx);
  }

  /**
   * Convenience method to mutate multiple rows in a batch. Each individual row is mutated
   * atomically as in MutateRow, but the entire batch is not executed atomically. This method
   * expects the mutations to be pre-batched.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableClient bigtableClient = BigtableClient.create("[PROJECT]", "[INSTANCE]")) {
   *   BulkMutation batch = BulkMutation.create("[TABLE]");
   *   for (String someValue : someCollection) {
   *     ApiFuture<Void> entryFuture = batch.add("[ROW KEY]",
   *       Mutation.create().setCell("[FAMILY NAME]", "[QUALIFIER]", "[VALUE]"));
   *   }
   *   ApiFuture<Void> resultFuture = bigtableDataClient.bulkMutateRowsAsync(batch);
   *
   *   ApiFutures.addCallback(resultFuture, new ApiFutureCallback<Void>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof BulkMutationFailure) {
   *         System.out.println("Some entries failed to apply");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Void ignored) {
   *       System.out.println("Successfully applied all mutation");
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   */
  public ApiFuture<Void> bulkMutateRowsAsync(BulkMutation mutation) {
    return bulkMutationCallable().futureCall(mutation);
  }

  /**
   * Mutates multiple rows in a batch. Each individual row is mutated atomically as in MutateRow,
   * but the entire batch is not executed atomically. This method expects the mutations to be
   * pre-batched.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   BulkMutation batch = BulkMutation.create("[TABLE]");
   *   for (String someValue : someCollection) {
   *     ApiFuture<Void> entryFuture = batch.add("[ROW KEY]",
   *       Mutation.create().setCell("[FAMILY NAME]", "[QUALIFIER]", "[VALUE]");
   *   }
   *   bigtableDataClient.bulkMutationCallable().call(batch);
   * }
   * }</pre>
   */
  public UnaryCallable<BulkMutation, Void> bulkMutationCallable() {
    return stub.bulkMutateRowsCallable();
  }

  /**
   * Convenience method to synchronously mutate a row atomically based on the output of a filter.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   ConditionalRowMutation mutation = ConditionalRowMutation.create("[TABLE]", "[KEY]")
   *     .condition(FILTERS.value().regex("old-value"))
   *     .then(
   *       Mutation.create()
   *         .setCell("[FAMILY]", "[QUALIFIER]", "[VALUE]")
   *       );
   *
   *   Boolean result = bigtableDataClient.checkAndMutateRow(mutation);
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   */
  public Boolean checkAndMutateRow(ConditionalRowMutation mutation) {
    return ApiExceptions.callAndTranslateApiException(checkAndMutateRowAsync(mutation));
  }

  /**
   * Convenience method to asynchronously mutate a row atomically based on the output of a filter.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   ConditionalRowMutation mutation = ConditionalRowMutation.create("[TABLE]", "[KEY]")
   *     .condition(FILTERS.value().regex("old-value"))
   *     .then(
   *       Mutation.create()
   *         .setCell("[FAMILY]", "[QUALIFIER]", "[VALUE]")
   *       );
   *
   *   ApiFuture<Boolean> future = bigtableDataClient.checkAndMutateRowAsync(mutation);
   *
   *   ApiFutures.addCallback(future, new ApiFutureCallback<Boolean>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to mutate a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Boolean wasApplied) {
   *       System.out.println("Row was modified: " + wasApplied);
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   */
  public ApiFuture<Boolean> checkAndMutateRowAsync(ConditionalRowMutation mutation) {
    return checkAndMutateRowCallable().futureCall(mutation);
  }

  /**
   * Mutates a row atomically based on the output of a filter.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   ConditionalRowMutation mutation = ConditionalRowMutation.create("[TABLE]", "[KEY]")
   *     .condition(FILTERS.value().regex("old-value"))
   *     .then(
   *       Mutation.create()
   *         .setCell("[FAMILY]", "[QUALIFIER]", "[VALUE]")
   *       );
   *
   *   // Sync style
   *   try {
   *     boolean success = bigtableDataClient.checkAndMutateRowCallable().call(mutation);
   *     if (!success) {
   *       System.out.println("Row did not match conditions");
   *     }
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to mutate a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   * }
   * }</pre>
   */
  public UnaryCallable<ConditionalRowMutation, Boolean> checkAndMutateRowCallable() {
    return stub.checkAndMutateRowCallable();
  }

  /**
   * Convenience method that synchronously modifies a row atomically on the server. The method reads
   * the latest existing timestamp and value from the specified columns and writes a new entry. The
   * new value for the timestamp is the greater of the existing timestamp or the current server
   * time. The method returns the new contents of all modified cells.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   ReadModifyWriteRow mutation = ReadModifyWriteRow.create("[TABLE]", "[KEY]")
   *     .increment("[FAMILY]", "[QUALIFIER]", 1)
   *     .append("[FAMILY2]", "[QUALIFIER2]", "suffix");
   *
   *   Row success = bigtableDataClient.readModifyWriteRow(mutation);
   * } catch(ApiException e) {
   *   e.printStackTrace();
   * }
   * }</pre>
   *
   * @throws com.google.api.gax.rpc.ApiException when a serverside error occurs
   */
  public Row readModifyWriteRow(ReadModifyWriteRow mutation) {
    return ApiExceptions.callAndTranslateApiException(readModifyWriteRowAsync(mutation));
  }

  /**
   * Convenience method that asynchronously modifies a row atomically on the server. The method
   * reads the latest existing timestamp and value from the specified columns and writes a new
   * entry. The new value for the timestamp is the greater of the existing timestamp or the current
   * server time. The method returns the new contents of all modified cells.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   ReadModifyWriteRow mutation = ReadModifyWriteRow.create("[TABLE]", "[KEY]")
   *     .increment("[FAMILY]", "[QUALIFIER]", 1)
   *     .append("[FAMILY2]", "[QUALIFIER2]", "suffix");
   *
   *   ApiFuture<Row> rowFuture = bigtableDataClient.readModifyWriteRowAsync(mutation);
   *
   *   ApiFutures.addCallback(rowFuture, new ApiFutureCallback<Row>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to mutate a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(Row resultingRow) {
   *       System.out.println("Resulting row: " + resultingRow);
   *     }
   *   }, MoreExecutors.directExecutor());
   * }
   * }</pre>
   */
  public ApiFuture<Row> readModifyWriteRowAsync(ReadModifyWriteRow mutation) {
    return readModifyWriteRowCallable().futureCall(mutation);
  }

  /**
   * Modifies a row atomically on the server. The method reads the latest existing timestamp and
   * value from the specified columns and writes a new entry. The new value for the timestamp is the
   * greater of the existing timestamp or the current server time. The method returns the new
   * contents of all modified cells.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   ReadModifyWriteRow mutation = ReadModifyWriteRow.create("[TABLE]", "[KEY]")
   *     .increment("[FAMILY]", "[QUALIFIER]", 1)
   *     .append("[FAMILY2]", "[QUALIFIER2]", "suffix");
   *
   *   try {
   *     Row row = bigtableDataClient.readModifyWriteRowCallable().call(mutation);
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to mutate a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   * }
   * }</pre>
   */
  public UnaryCallable<ReadModifyWriteRow, Row> readModifyWriteRowCallable() {
    return stub.readModifyWriteRowCallable();
  }

  /**
   * Convenience method for synchronously streaming the partitions of a table. The returned
   * ServerStream instance is not threadsafe, it can only be used from single thread.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   try {
   *     ServerStream<ByteStringRange> stream = bigtableDataClient.generateInitialChangeStreamPartitions(tableId);
   *     int count = 0;
   *
   *     // Iterator style
   *     for (ByteStringRange partition : stream) {
   *       if (++count > 10) {
   *         stream.cancel();
   *         break;
   *       }
   *       // Do something with partition
   *     }
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   * }
   * }</pre>
   *
   * @see ServerStreamingCallable For call styles.
   */
  @InternalApi("Intended for use by the BigtableIO in apache/beam only.")
  public ServerStream<ByteStringRange> generateInitialChangeStreamPartitions(String tableId) {
    return generateInitialChangeStreamPartitionsCallable().call(tableId);
  }

  /**
   * Convenience method for asynchronously streaming the partitions of a table.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   bigtableDataClient.generateInitialChangeStreamPartitionsAsync(tableId, new ResponseObserver<RowRange>() {
   *     StreamController controller;
   *     int count = 0;
   *
   *     public void onStart(StreamController controller) {
   *       this.controller = controller;
   *     }
   *     public void onResponse(ByteStringRange partition) {
   *       if (++count > 10) {
   *         controller.cancel();
   *         return;
   *       }
   *       // Do something with partition
   *     }
   *     public void onError(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onComplete() {
   *       // Handle stream completion
   *     }
   *   });
   * }
   * }</pre>
   */
  @InternalApi("Intended for use by the BigtableIO in apache/beam only.")
  public void generateInitialChangeStreamPartitionsAsync(
      String tableId, ResponseObserver<ByteStringRange> observer) {
    generateInitialChangeStreamPartitionsCallable().call(tableId, observer);
  }

  /**
   * Streams back the results of the query. The returned callable object allows for customization of
   * api invocation.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   // Iterator style
   *   try {
   *     for(ByteStringRange partition : bigtableDataClient.generateInitialChangeStreamPartitionsCallable().call(tableId)) {
   *       // Do something with partition
   *     }
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // Sync style
   *   try {
   *     List<ByteStringRange> partitions = bigtableDataClient.generateInitialChangeStreamPartitionsCallable().all().call(tableId);
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // Point look up
   *   ApiFuture<ByteStringRange> partitionFuture =
   *     bigtableDataClient.generateInitialChangeStreamPartitionsCallable().first().futureCall(tableId);
   *
   *   ApiFutures.addCallback(partitionFuture, new ApiFutureCallback<ByteStringRange>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(RowRange result) {
   *       System.out.println("Got partition: " + result);
   *     }
   *   }, MoreExecutors.directExecutor());
   *
   *   // etc
   * }
   * }</pre>
   *
   * @see ServerStreamingCallable For call styles.
   */
  @InternalApi("Intended for use by the BigtableIO in apache/beam only.")
  public ServerStreamingCallable<String, ByteStringRange>
      generateInitialChangeStreamPartitionsCallable() {
    return stub.generateInitialChangeStreamPartitionsCallable();
  }

  /**
   * Convenience method for synchronously streaming the results of a {@link ReadChangeStreamQuery}.
   * The returned ServerStream instance is not threadsafe, it can only be used from single thread.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   ReadChangeStreamQuery query = ReadChangeStreamQuery.create(tableId)
   *          .streamPartition("START_KEY", "END_KEY")
   *          .startTime(Timestamp.newBuilder().setSeconds(100).build());
   *
   *   try {
   *     ServerStream<ChangeStreamRecord> stream = bigtableDataClient.readChangeStream(query);
   *     int count = 0;
   *
   *     // Iterator style
   *     for (ChangeStreamRecord record : stream) {
   *       if (++count > 10) {
   *         stream.cancel();
   *         break;
   *       }
   *       // Do something with the change stream record.
   *     }
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   * }
   * }</pre>
   *
   * @see ServerStreamingCallable For call styles.
   * @see ReadChangeStreamQuery For query options.
   */
  @InternalApi("Intended for use by the BigtableIO in apache/beam only.")
  public ServerStream<ChangeStreamRecord> readChangeStream(ReadChangeStreamQuery query) {
    return readChangeStreamCallable().call(query);
  }

  /**
   * Convenience method for asynchronously streaming the results of a {@link ReadChangeStreamQuery}.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   ReadChangeStreamQuery query = ReadChangeStreamQuery.create(tableId)
   *          .streamPartition("START_KEY", "END_KEY")
   *          .startTime(Timestamp.newBuilder().setSeconds(100).build());
   *
   *   bigtableDataClient.readChangeStreamAsync(query, new ResponseObserver<ChangeStreamRecord>() {
   *     StreamController controller;
   *     int count = 0;
   *
   *     public void onStart(StreamController controller) {
   *       this.controller = controller;
   *     }
   *     public void onResponse(ChangeStreamRecord record) {
   *       if (++count > 10) {
   *         controller.cancel();
   *         return;
   *       }
   *       // Do something with the change stream record.
   *     }
   *     public void onError(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onComplete() {
   *       // Handle stream completion
   *     }
   *   });
   * }
   * }</pre>
   */
  @InternalApi("Intended for use by the BigtableIO in apache/beam only.")
  public void readChangeStreamAsync(
      ReadChangeStreamQuery query, ResponseObserver<ChangeStreamRecord> observer) {
    readChangeStreamCallable().call(query, observer);
  }

  /**
   * Streams back the results of the query. The returned callable object allows for customization of
   * api invocation.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String tableId = "[TABLE]";
   *
   *   ReadChangeStreamQuery query = ReadChangeStreamQuery.create(tableId)
   *          .streamPartition("START_KEY", "END_KEY")
   *          .startTime(Timestamp.newBuilder().setSeconds(100).build());
   *
   *   // Iterator style
   *   try {
   *     for(ChangeStreamRecord record : bigtableDataClient.readChangeStreamCallable().call(query)) {
   *       // Do something with record
   *     }
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // Sync style
   *   try {
   *     List<ChangeStreamRecord> records = bigtableDataClient.readChangeStreamCallable().all().call(query);
   *   } catch (NotFoundException e) {
   *     System.out.println("Tried to read a non-existent table");
   *   } catch (RuntimeException e) {
   *     e.printStackTrace();
   *   }
   *
   *   // Point look up
   *   ApiFuture<ChangeStreamRecord> recordFuture =
   *     bigtableDataClient.readChangeStreamCallable().first().futureCall(query);
   *
   *   ApiFutures.addCallback(recordFuture, new ApiFutureCallback<ChangeStreamRecord>() {
   *     public void onFailure(Throwable t) {
   *       if (t instanceof NotFoundException) {
   *         System.out.println("Tried to read a non-existent table");
   *       } else {
   *         t.printStackTrace();
   *       }
   *     }
   *     public void onSuccess(ChangeStreamRecord result) {
   *       System.out.println("Got record: " + result);
   *     }
   *   }, MoreExecutors.directExecutor());
   *
   *   // etc
   * }
   * }</pre>
   *
   * @see ServerStreamingCallable For call styles.
   * @see ReadChangeStreamQuery For query options.
   */
  @InternalApi("Intended for use by the BigtableIO in apache/beam only.")
  public ServerStreamingCallable<ReadChangeStreamQuery, ChangeStreamRecord>
      readChangeStreamCallable() {
    return stub.readChangeStreamCallable();
  }

  /**
   * Executes a SQL Query and returns a ResultSet to iterate over the results. The returned
   * ResultSet instance is not threadsafe, it can only be used from single thread.
   *
   * <p> The {@link BoundStatement} must be built from a {@link PreparedStatement} created using
   * the same instance and app profile.
   *
   * <p>Sample code:
   *
   * <pre>{@code
   * try (BigtableDataClient bigtableDataClient = BigtableDataClient.create("[PROJECT]", "[INSTANCE]")) {
   *   String query = "SELECT CAST(cf['stringCol'] AS STRING) FROM [TABLE]";
   *   Map<String, SqlType<?>> paramTypes = new HashMap<>();
   *   PreparedStatement preparedStatement = bigtableDataClient.prepareStatement(query, paramTypes));
   *   // Ideally one PreparedStatement should be reused across requests
   *   BoundStatement boundStatement = preparedStatement.bind()
   *      // set any query params before calling build
   *      .build();
   *   try (ResultSet resultSet = bigtableDataClient.executeQuery(boundStatement)) {
   *       while (resultSet.next()) {
   *           String s = resultSet.getString("stringCol");
   *            // do something with data
   *       }
   *    } catch (RuntimeException e) {
   *        e.printStackTrace();
   *   }
   * }</pre>
   *
   * @see {@link PreparedStatement} & {@link BoundStatement} for query options.
   */
  public ResultSet executeQuery(BoundStatement boundStatement) {
    boundStatement.assertUsingSameStub(stub);
    SqlServerStream stream = stub.createExecuteQueryCallable().call(boundStatement);
    return ResultSetImpl.create(stream);
  }

  /**
   * Prepares a query for execution. If possible this should be called once and reused across
   * requests. This will amortize the cost of query preparation.
   *
   * <p>A parameterized query should contain placeholders in the form of {@literal @} followed by
   * the parameter name. Parameter names may consist of any combination of letters, numbers, and
   * underscores.
   *
   * <p>Parameters can appear anywhere that a literal value is expected. The same parameter name can
   * be used more than once, for example: {@code WHERE cf["qualifier1"] = @value OR cf["qualifier2"]
   * = @value }
   *
   * @param query sql query string to prepare
   * @param paramTypes a Map of the parameter names and the corresponding {@link SqlType} for all
   *     query parameters in 'query'
   * @return {@link PreparedStatement} which is used to create {@link BoundStatement}s to execute
   */
  public PreparedStatement prepareStatement(String query, Map<String, SqlType<?>> paramTypes) {
    PrepareQueryRequest request = PrepareQueryRequest.create(query, paramTypes);
    PrepareResponse response = stub.prepareQueryCallable().call(request);
    return PreparedStatementImpl.create(response, paramTypes, request, stub);
  }

  /** Close the clients and releases all associated resources. */
  @Override
  public void close() {
    stub.close();
  }
}
