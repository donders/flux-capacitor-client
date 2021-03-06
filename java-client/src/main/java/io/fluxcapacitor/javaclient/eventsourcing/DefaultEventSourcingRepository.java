package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.model.Model;
import lombok.AllArgsConstructor;

import static java.lang.String.format;

@AllArgsConstructor
public class DefaultEventSourcingRepository<T> implements EventSourcingRepository<T> {
    private final EventSourcing eventSourcing;
    private final Class<T> modelClass;

    @Override
    public Model<T> load(String modelId, Long expectedSequenceNumber) {
        Model<T> result = eventSourcing.load(modelId, modelClass);
        if (expectedSequenceNumber != null && expectedSequenceNumber != result.getSequenceNumber()) {
            throw new EventSourcingException(format(
                    "Failed to load %s of id %s. Expected sequence number %d but model had sequence number %d",
                    modelClass.getSimpleName(), modelId, expectedSequenceNumber, result.getSequenceNumber()));
        }
        return result;
    }

}
