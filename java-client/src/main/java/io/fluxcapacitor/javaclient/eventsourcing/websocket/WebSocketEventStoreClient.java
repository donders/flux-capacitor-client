/*
 * Copyright (c) 2016-2017 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.eventsourcing.websocket;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.eventsourcing.AppendEvents;
import io.fluxcapacitor.common.api.eventsourcing.EventBatch;
import io.fluxcapacitor.common.api.eventsourcing.GetEvents;
import io.fluxcapacitor.common.api.eventsourcing.GetEventsResult;
import io.fluxcapacitor.common.serialization.websocket.JsonDecoder;
import io.fluxcapacitor.common.serialization.websocket.JsonEncoder;
import io.fluxcapacitor.javaclient.common.connection.AbstractWebsocketService;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.eventsourcing.EventStoreClient;
import io.fluxcapacitor.javaclient.eventsourcing.Snapshot;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueClient;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueRepository;

import javax.websocket.ClientEndpoint;
import javax.websocket.EncodeException;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.iterate;

@ClientEndpoint(encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WebSocketEventStoreClient extends AbstractWebsocketService implements EventStoreClient {

    private final KeyValueRepository<Snapshot> snapshotRepository;
    private final Backlog<EventBatch> backlog;
    private final int fetchBatchSize;

    public WebSocketEventStoreClient(String endPointUrl, KeyValueClient keyValueClient) {
        this(URI.create(endPointUrl), 1024, 1024, keyValueClient, new JacksonSerializer());
    }

    public WebSocketEventStoreClient(String endPointUrl, int backlogSize,
                                     KeyValueClient keyValueClient) {
        this(URI.create(endPointUrl), backlogSize, 1024, keyValueClient, new JacksonSerializer());
    }

    public WebSocketEventStoreClient(URI endPointUri, int backlogSize, int fetchBatchSize,
                                     KeyValueClient keyValueClient, Serializer serializer) {
        super(endPointUri);
        this.backlog = new Backlog<>(this::doSend, backlogSize);
        this.fetchBatchSize = fetchBatchSize;
        this.snapshotRepository = new KeyValueRepository<>(keyValueClient, serializer);
    }

    @Override
    public Awaitable storeEvents(String aggregateId, String domain, long lastSequenceNumber, List<SerializedMessage> events) {
        return backlog.add(new EventBatch(aggregateId, domain, lastSequenceNumber, events));
    }

    private Awaitable doSend(List<EventBatch> batches) throws IOException, EncodeException {
        getSession().getBasicRemote().sendObject(new AppendEvents(batches));
        return Awaitable.ready();
    }

    @Override
    public Stream<SerializedMessage> getEvents(String aggregateId, long lastSequenceNumber) {
        return iterate((GetEventsResult) sendRequest(new GetEvents(aggregateId, lastSequenceNumber, fetchBatchSize)),
                       r -> sendRequest(new GetEvents(aggregateId, r.getEventBatch().getLastSequenceNumber(), fetchBatchSize)),
                       r -> r.getEventBatch().getEvents().size() < fetchBatchSize)
                .flatMap(r -> r.getEventBatch().getEvents().stream());
    }

    @Override
    public void storeSnapshot(Snapshot snapshot) {
        snapshotRepository.put(snapshot.getAggregateId(), snapshot);
    }

    @Override
    public Optional<Snapshot> getSnapshot(String aggregateId) {
        return Optional.ofNullable(snapshotRepository.get(aggregateId));
    }

    @Override
    public void deleteSnapshot(String aggregateId) {
        snapshotRepository.delete(aggregateId);
    }
}