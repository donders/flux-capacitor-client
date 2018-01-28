package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.gateway.GatewayClient;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueStore;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
public class DefaultEventStore implements EventStore {

    private final EventStoreClient client;
    private final GatewayClient eventGateway;
    private final KeyValueStore keyValueStore;
    private final MessageSerializer eventSerializer;
    private final SnapshotSerializer snapshotSerializer;

    @Override
    public void storeEvent(Object payload, Metadata metadata) {
        try {
            eventGateway.send(eventSerializer.serialize(payload, metadata)).await();
        } catch (Exception e) {
            throw new EventStoreException(format("Failed to store event %s with metadata %s", payload, metadata), e);
        }
    }

    @Override
    public void storeDomainEvents(String aggregateId, String domain, long lastSequenceNumber, List<Message> events) {
        try {
            client.storeEvents(aggregateId, domain, lastSequenceNumber, events.stream().map(eventSerializer::serialize)
                    .collect(toList())).await();
        } catch (Exception e) {
            throw new EventStoreException(format("Failed to store events %s for aggregate %s", events, aggregateId), e);
        }
    }

    @Override
    public Stream<Message> getDomainEvents(String aggregateId, long lastSequenceNumber) {
        try {
            return client.getEvents(aggregateId, lastSequenceNumber).map(eventSerializer::deserialize);
        } catch (Exception e) {
            throw new EventStoreException(format("Failed to obtain domain events for aggregate %s", aggregateId), e);
        }
    }

    @Override
    public void storeSnapshot(String aggregateId, long sequenceNumber, Object s) {
        try {
            keyValueStore.store(snapshotKey(aggregateId), snapshotSerializer.serialize(aggregateId, sequenceNumber, s));
        } catch (Exception e) {
            throw new EventStoreException(format("Failed to store snapshot %s for aggregate %s", s, aggregateId), e);
        }
    }

    @Override
    public <T> Optional<Snapshot<T>> getSnapshot(String aggregateId) {
        try {
            SerializedSnapshot snapshot = keyValueStore.get(snapshotKey(aggregateId));
            return Optional.ofNullable(snapshot).map(snapshotSerializer::deserialize);
        } catch (Exception e) {
            throw new EventStoreException(format("Failed to obtain snapshot for aggregate %s", aggregateId), e);
        }
    }

    @Override
    public void deleteSnapshot(String aggregateId) {
        try {
            keyValueStore.delete(snapshotKey(aggregateId));
        } catch (Exception e) {
            throw new EventStoreException(format("Failed to delete snapshot for aggregate %s", aggregateId), e);
        }
    }

    protected String snapshotKey(String aggregateId) {
        return "$snapshot_" + aggregateId;
    }
}
