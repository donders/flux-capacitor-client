package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface RequestHandler {

   CompletableFuture<Message> sendRequest(SerializedMessage request, Consumer<SerializedMessage> requestSender);

}
