package org.vitrivr.cineast.core.db.cottontaildb;

import static org.vitrivr.cineast.core.db.cottontaildb.CottontailMessageBuilder.CINEAST_SCHEMA;

import org.apache.commons.lang3.time.StopWatch;
import org.vitrivr.cottontail.grpc.CottonDDLGrpc;
import org.vitrivr.cottontail.grpc.CottonDDLGrpc.CottonDDLBlockingStub;
import org.vitrivr.cottontail.grpc.CottonDDLGrpc.CottonDDLFutureStub;
import org.vitrivr.cottontail.grpc.CottonDMLGrpc;
import org.vitrivr.cottontail.grpc.CottonDMLGrpc.CottonDMLStub;
import org.vitrivr.cottontail.grpc.CottonDQLGrpc;
import org.vitrivr.cottontail.grpc.CottonDQLGrpc.CottonDQLBlockingStub;
import org.vitrivr.cottontail.grpc.CottontailGrpc;
import org.vitrivr.cottontail.grpc.CottontailGrpc.*;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.vitrivr.cineast.core.config.DatabaseConfig;
import org.vitrivr.cineast.core.util.LogHelper;

public class CottontailWrapper implements AutoCloseable {

  private static final Logger LOGGER = LogManager.getLogger();
  private static final CottontailGrpc.Status INTERRUPTED_INSERT = CottontailGrpc.Status.newBuilder().setSuccess(false).build();

  private final ManagedChannel channel;
  private final CottonDDLFutureStub definitionFutureStub;
  private final CottonDMLStub managementStub;
  private final CottonDMLStub insertStub;
  private final HashSet<String> ensuredSchemas = new HashSet<>();

  private static final int maxMessageSize = 10_000_000;
  private static final long MAX_QUERY_CALL_TIMEOUT = 300_000; //TODO expose to config
  private static final long MAX_CALL_TIMEOUT = 5000; //TODO expose to config
  private final boolean closeWrapper;

  public CottontailWrapper(DatabaseConfig config, boolean closeWrapper) {
    StopWatch watch = StopWatch.createStarted();
    this.closeWrapper = closeWrapper;
    NettyChannelBuilder builder = NettyChannelBuilder.forAddress(config.getHost(), config.getPort()).maxInboundMessageSize(maxMessageSize);
    if (config.getPlaintext()) {
      builder = builder.usePlaintext();
    }
    this.channel = builder.build();
    this.definitionFutureStub = CottonDDLGrpc.newFutureStub(channel);
    this.managementStub = CottonDMLGrpc.newStub(channel);
    this.insertStub = CottonDMLGrpc.newStub(channel);
    watch.stop();
    LOGGER.info("Connected to Cottontail in {} ms at {}:{}", watch.getTime(TimeUnit.MILLISECONDS), config.getHost(), config.getPort());
  }

  public synchronized ListenableFuture<CottontailGrpc.Status> createEntity(EntityDefinition createMessage) {
    final CottonDDLFutureStub stub = CottonDDLGrpc.newFutureStub(this.channel);
    return stub.createEntity(createMessage);
  }

  public synchronized ListenableFuture<CottontailGrpc.EntityDefinition> entityDetails(Entity entity) {
    return CottonDDLGrpc.newFutureStub(this.channel).entityDetails(entity);
  }

  public synchronized CottontailGrpc.EntityDefinition entityDetailsBlocking(Entity entity) {
    final CottonDDLBlockingStub stub = CottonDDLGrpc.newBlockingStub(this.channel);
    try {
      return stub.entityDetails(entity);
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.NOT_FOUND.getCode()) {
        LOGGER.warn("Entity {} was not found", entity);
        return null;
      } else {
        throw LOGGER.throwing(e);
      }
    }
  }

  public static Entity entityByName(String entityName) {
    return CottontailMessageBuilder.entity(CottontailMessageBuilder.CINEAST_SCHEMA, entityName);
  }

  public synchronized boolean createEntityBlocking(EntityDefinition createMessage) {
    final CottonDDLBlockingStub stub = CottonDDLGrpc.newBlockingStub(this.channel);
    try {
      stub.createEntity(createMessage);
      return true;
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.ALREADY_EXISTS.getCode()) {
        LOGGER.warn("Entity {} was not created because it already exists", createMessage.getEntity().getName());
      } else {
        e.printStackTrace();
      }
    }
    return false;
  }

  public synchronized boolean createIndexBlocking(IndexDefinition createMessage) {
    final CottonDDLBlockingStub stub = CottonDDLGrpc.newBlockingStub(this.channel);
    try {
      stub.createIndex(createMessage);
      return true;
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.ALREADY_EXISTS.getCode()) {
        LOGGER.warn("Index on {}.{} was not created because it already exists", createMessage.getIndex().getEntity().getName(), createMessage.getColumnsList().toString());
        return false;
      }
      e.printStackTrace();
    }
    return false;
  }

  public synchronized boolean dropIndexBlocking(Index index) {
    final CottonDDLBlockingStub stub = CottonDDLGrpc.newBlockingStub(this.channel);
    try {
      stub.dropIndex(index);
      return true;
    } catch (StatusRuntimeException e) {
      if (e.getStatus() == Status.NOT_FOUND) {
        LOGGER.warn("Index {} was not dropped because it does not exist", index.getName());
        return false;
      }
      e.printStackTrace();
    }
    return false;
  }

  public synchronized boolean optimizeEntityBlocking(Entity entity) {
    final CottonDDLBlockingStub stub = CottonDDLGrpc.newBlockingStub(this.channel);
    try {
      stub.optimize(entity);
      return true;
    } catch (StatusRuntimeException e) {
      e.printStackTrace();
    }
    return false;
  }

  public synchronized boolean dropEntityBlocking(Entity entity) {
    final CottonDDLBlockingStub stub = CottonDDLGrpc.newBlockingStub(this.channel);
    try {
      stub.dropEntity(entity);
      return true;
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.NOT_FOUND.getCode()) {
        LOGGER.debug("entity {} was not dropped because it does not exist", entity.getName());
      } else {
        e.printStackTrace();
      }
    }
    return false;
  }

  public synchronized ListenableFuture<CottontailGrpc.Status> createSchema(String schama) {
    final CottonDDLFutureStub stub = CottonDDLGrpc.newFutureStub(this.channel);
    return stub.createSchema(CottontailMessageBuilder.schema(schama));
  }

  public synchronized boolean createSchemaBlocking(String schema) {
    ListenableFuture<CottontailGrpc.Status> future = this.createSchema(schema);
    try {
      future.get();
      return true;
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.error("error in createSchemaBlocking: {}", LogHelper.getStackTrace(e));
      return false;
    }
  }

  public synchronized void ensureSchemaBlocking(String schema) {
    if (this.ensuredSchemas.contains(schema)) {
      return;
    }
    final CottonDDLBlockingStub stub = CottonDDLGrpc.newBlockingStub(this.channel);
    Iterator<Schema> existingSchemas = stub.listSchemas(Empty.getDefaultInstance());
    boolean schemaExists = false;
    while (existingSchemas.hasNext()) {
      Schema existingSchema = existingSchemas.next();
      if (existingSchema.getName().equals(schema)) {
        schemaExists = true;
      }
    }
    if (!schemaExists) {
      this.createSchemaBlocking(schema);
    }
    this.ensuredSchemas.add(schema);
  }

  public boolean insert(List<InsertMessage> messages) {

    final boolean[] status = {false, false}; /* {done, error}. */
    final StreamObserver<CottontailGrpc.Status> observer = new StreamObserver<CottontailGrpc.Status>() {

      @Override
      public void onNext(CottontailGrpc.Status value) {
        LOGGER.trace("Tuple received: {}", value.getTimestamp());
      }

      @Override
      public void onError(Throwable t) {
        status[0] = true;
        status[1] = true;
        LOGGER.error("Error during insert. Everything was rolled back: {}", t.getMessage());
      }

      @Override
      public void onCompleted() {
        status[0] = true;
        LOGGER.trace("Insert successful. Changes were committed!");
      }
    };

    /* Start data transfer. */
    final StreamObserver<InsertMessage> sink = this.managementStub.insert(observer);
    for (InsertMessage message : messages) {
      sink.onNext(message);
    }
    sink.onCompleted(); /* Send commit message. */

    while (!status[0]) {
      Thread.yield();
    }
    return !status[1];
  }

  /**
   * Issues a single query to the Cottontail DB endpoint in a blocking fashion.
   *
   * @return The query results (unprocessed).
   */
  public List<QueryResponseMessage> query(QueryMessage query) {
    StopWatch watch = StopWatch.createStarted();
    final ArrayList<QueryResponseMessage> results = new ArrayList<>();
    final CottonDQLBlockingStub stub = CottonDQLGrpc.newBlockingStub(this.channel).withDeadlineAfter(MAX_QUERY_CALL_TIMEOUT, TimeUnit.MILLISECONDS);
    try {
      stub.query(query).forEachRemaining(results::add);
    } catch (StatusRuntimeException e) {
      if (e.getStatus() == Status.DEADLINE_EXCEEDED) {
        LOGGER.error("CottontailWrapper.query has timed out (timeout = {}ms).", MAX_QUERY_CALL_TIMEOUT);
      } else {
        LOGGER.error("Error occurred during invocation of CottontailWrapper.query: {}", e.getMessage());
      }
    }
    LOGGER.trace("Wall time for query {} is {} ms", query.getQueryId(), watch.getTime());
    return results;
  }

  /**
   * Issues a batched query to the Cottontail DB endpoint in a blocking fashion.
   *
   * @return The query results (unprocessed).
   */
  public List<QueryResponseMessage> batchedQuery(BatchedQueryMessage query) {
    final ArrayList<QueryResponseMessage> results = new ArrayList<>();
    final CottonDQLBlockingStub stub = CottonDQLGrpc.newBlockingStub(this.channel).withDeadlineAfter(MAX_QUERY_CALL_TIMEOUT, TimeUnit.MILLISECONDS);
    try {
      stub.batchedQuery(query).forEachRemaining(results::add);
    } catch (StatusRuntimeException e) {
      if (e.getStatus() == Status.DEADLINE_EXCEEDED) {
        LOGGER.error("CottontailWrapper.batchedQuery has timed out (timeout = {}ms).", MAX_QUERY_CALL_TIMEOUT);
      } else {
        LOGGER.error("Error occurred during invocation of CottontailWrapper.batchedQuery: {}", e.getMessage());
      }
    }
    return results;
  }

  /**
   * Pings the Cottontail DB endpoint and returns true on success and false otherwise.
   *
   * @return True on success, false otherwise.
   */
  public boolean ping() {
    final CottonDQLBlockingStub stub = CottonDQLGrpc.newBlockingStub(this.channel).withDeadlineAfter(MAX_CALL_TIMEOUT, TimeUnit.MILLISECONDS);
    try {
      final CottontailGrpc.Status status = stub.ping(Empty.getDefaultInstance());
      return true;
    } catch (StatusRuntimeException e) {
      if (e.getStatus() == Status.DEADLINE_EXCEEDED) {
        LOGGER.error("CottontailWrapper.ping has timed out.");
      } else {
        LOGGER.error("Error occurred during invocation of CottontailWrapper.ping: {}", e.getMessage());

      }
      return false;
    }
  }

  /**
   * Uses the Cottontail DB endpoint to list all entities.
   *
   * @param schema Schema for which to list entities.
   * @return List of entities.
   */
  public List<Entity> listEntities(Schema schema) {
    ArrayList<Entity> entities = new ArrayList<>();
    final CottonDDLBlockingStub stub = CottonDDLGrpc.newBlockingStub(this.channel).withDeadlineAfter(MAX_CALL_TIMEOUT, TimeUnit.MILLISECONDS);
    try {
      stub.listEntities(schema).forEachRemaining(entities::add);
    } catch (StatusRuntimeException e) {
      if (e.getStatus() == Status.DEADLINE_EXCEEDED) {
        LOGGER.error("CottontailWrapper.listEntities has timed out (timeout = {}ms).", MAX_CALL_TIMEOUT);
      } else {
        LOGGER.error("Error occurred during invocation of CottontailWrapper.listEntities: {}", e.getMessage());
      }
    }
    return entities;
  }

  /**
   *
   */
  @Override
  public void close() {
    if (closeWrapper) {
      LOGGER.info("Closing connection to cottontail");
      this.channel.shutdown();
    }
  }

  public boolean existsEntity(String name) {
    List<Entity> entities = this.listEntities(CINEAST_SCHEMA);

    for (Entity entity : entities) {
      if (entity.getName().equals(name)) {
        return true;
      }
    }

    return false;
  }
}
