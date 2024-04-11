package com.spotify.confidence;

import static com.spotify.confidence.EventSenderEngineImpl.event;

import com.google.protobuf.Timestamp;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FakeEventSenderEngine implements EventSenderEngine {

  final Clock clock;

  public FakeEventSenderEngine(Clock clock) {
    this.clock = clock;
  }

  List<com.spotify.confidence.events.v1.Event> events = new ArrayList<>();
  boolean closed;

  @Override
  public void close() throws IOException {
    this.closed = true;
  }

  @Override
  public void send(
      String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message) {
    events.add(
        event(name, context, message)
            .setEventTime(Timestamp.newBuilder().setSeconds(clock.currentTimeSeconds()).build())
            .build());
  }
}
