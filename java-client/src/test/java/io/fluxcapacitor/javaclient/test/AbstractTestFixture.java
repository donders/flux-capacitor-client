/*
 * Copyright (c) 2016-2018 Flux Capacitor. 
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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import io.fluxcapacitor.javaclient.publishing.correlation.ContextualDispatchInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.EVENT;

public abstract class AbstractTestFixture implements Given, When {
    
    private final FluxCapacitor fluxCapacitor;
    private final Registration registration;
    private final GivenWhenThenInterceptor interceptor;

    protected AbstractTestFixture(FluxCapacitorBuilder fluxCapacitorBuilder,
                                  Function<FluxCapacitor, List<?>> handlerFactory) {
        this.interceptor = new GivenWhenThenInterceptor();
        this.fluxCapacitor = fluxCapacitorBuilder.addDispatchInterceptor(interceptor).addHandlerInterceptor(interceptor)
                .build(InMemoryClient.newInstance());
        this.registration = registerHandlers(handlerFactory.apply(fluxCapacitor), fluxCapacitor);
    }
    
    protected abstract Registration registerHandlers(List<?> handlers, FluxCapacitor fluxCapacitor);
    
    protected abstract Then createResultValidator(Object result); 
    
    protected abstract void registerCommand(Message command);

    protected abstract void registerEvent(Message event);
    
    protected abstract Object getDispatchResult(CompletableFuture<?> dispatchResult);

    protected abstract void deregisterHandlers(Registration registration);

    @Override
    public When givenCommands(Object... commands) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            getDispatchResult(CompletableFuture.allOf(Arrays.stream(commands).map(c -> fluxCapacitor.commandGateway().send(c))
                                            .toArray(CompletableFuture[]::new)));
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute givenCommands", e);
        } finally {
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public When givenEvents(Object... events) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            Arrays.stream(events).forEach(c -> fluxCapacitor.eventGateway().publish(c));
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute givenEvents", e);
        } finally {
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public When andGivenCommands(Object... commands) {
        return givenCommands(commands);
    }

    @Override
    public When andGivenEvents(Object... events) {
        return givenEvents(events);
    }

    @Override
    public Then whenCommand(Object command) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            Object result;
            try {
                result = getDispatchResult(fluxCapacitor.commandGateway().send(interceptor.trace(command, COMMAND)));
            } catch (Exception e) {
                result = e;
            }
            return createResultValidator(result);
        } finally {
            deregisterHandlers(registration);
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public Then whenEvent(Object event) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            fluxCapacitor.eventGateway().publish(interceptor.trace(event, EVENT));
            return createResultValidator(null);
        } finally {
            deregisterHandlers(registration);
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public Then whenQuery(Object query) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            Object result;
            try {
                result = getDispatchResult(fluxCapacitor.queryGateway().send(query));
            } catch (Exception e) {
                result = e;
            }
            return createResultValidator(result);
        } finally {
            deregisterHandlers(registration);
            FluxCapacitor.instance.remove();
        }
    }

    protected class GivenWhenThenInterceptor extends ContextualDispatchInterceptor {

        private static final String TAG = "givenWhenThen.tag";
        private static final String TAG_NAME = "givenWhenThen.tagName";
        private static final String TRACE_NAME = "givenWhenThen.trace";

        protected Message trace(Object message, MessageType type) {
            Message result =
                    message instanceof Message ? (Message) message : new Message(message, Metadata.empty(), type);
            result.getMetadata().put(TAG_NAME, TAG);
            return result;
        }

        protected boolean isChildMetadata(Metadata messageMetadata) {
            return TAG.equals(messageMetadata.get(TRACE_NAME));
        }

        protected boolean isDescendantMetadata(Metadata messageMetadata) {
            return TAG.equals(getTrace(messageMetadata).get(0));
        }

        protected List<String> getTrace(Metadata messageMetadata) {
            return Arrays.asList(messageMetadata.getOrDefault(TRACE_NAME, "").split(","));
        }

        @Override
        public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function) {
            return message -> {
                String tag = UUID.randomUUID().toString();
                message.getMetadata().putIfAbsent(TAG_NAME, tag);
                getCurrentMessage().ifPresent(currentMessage -> {
                    if (currentMessage.getMetadata().containsKey(TRACE_NAME)) {
                        message.getMetadata().put(TRACE_NAME, currentMessage.getMetadata().get(
                                TRACE_NAME) + "," + currentMessage.getMetadata().get(TAG_NAME));
                    } else {
                        message.getMetadata().put(TRACE_NAME, currentMessage.getMetadata().get(TAG_NAME));
                    }
                });
                if (isDescendantMetadata(message.getMetadata())) {
                    switch (message.getMessageType()) {
                        case COMMAND:
                            registerCommand(message);
                            break;
                        case EVENT:
                            registerEvent(message);
                            break;
                    }
                }
                return function.apply(message);
            };
        }
    }
}
