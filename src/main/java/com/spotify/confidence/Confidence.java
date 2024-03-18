package com.spotify.confidence;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Confidence implements EventSender, Contextual {

  private Map<String, ConfidenceValue> context = Maps.newHashMap();
  private final List<String> removed = new ArrayList<>();

  private final Contextual parent;

  private final EventSenderEngine eventSenderEngine;

  Confidence(@Nullable Contextual parent, EventSenderEngine eventSenderEngine) {
    this.parent = parent;
    this.eventSenderEngine = eventSenderEngine;
  }

  @Override
  public ConfidenceValue.Struct getContext() {
    final ConfidenceValue.Struct parentContext =
        parent != null ? parent.getContext() : ConfidenceValue.Struct.EMPTY;
    // merge the parentContext with context with precendence to context
    final Map<String, ConfidenceValue> mergedContext = Maps.newHashMap(parentContext.asMap());
    mergedContext.putAll(context);
    removed.forEach(mergedContext::remove);
    return ConfidenceValue.of(mergedContext);
  }

  @Override
  public void setContext(ConfidenceValue.Struct context) {
    this.context = Maps.newHashMap(context.asMap());
  }

  @Override
  public void updateContext(String key, ConfidenceValue value) {
    this.context.put(key, value);
  }

  @Override
  public void removeContext(String key) {
    removed.add(key);
  }

  @Override
  public void clearContext() {
    context.clear();
  }

  @Override
  public Confidence withContext(ConfidenceValue.Struct entries) {
    final Confidence confidence = new Confidence(this, eventSenderEngine);
    confidence.setContext(entries);
    return confidence;
  }

  @Override
  public void send(String name, ConfidenceValue.Struct message) {
    eventSenderEngine.send(name, message, getContext());
  }

  public void close() throws IOException {
    eventSenderEngine.close();
  }

  public static Confidence.Builder builder(String clientSecret) {
    return new Confidence.Builder(clientSecret);
  }

  public static class Builder {
    private final String clientSecret;
    private ManagedChannel managedChannel;

    public Builder(@Nonnull String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public Builder managedChannel(String host, int port) {
      this.managedChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
      return this;
    }

    public Builder managedChannel(ManagedChannel managedChannel) {
      this.managedChannel = managedChannel;
      return this;
    }

    public Confidence build() {
      if (managedChannel == null) {
        throw new IllegalStateException("ManagedChannel is not set");
      }
      final GrpcEventUploader uploader =
          new GrpcEventUploader(clientSecret, new SystemClock(), managedChannel);
      final List<FlushPolicy> flushPolicies = ImmutableList.of(new BatchSizeFlushPolicy(5));
      final EventSenderEngine engine = new EventSenderEngineImpl(flushPolicies, uploader);
      return new Confidence(null, engine);
    }
  }
}
