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

package io.fluxcapacitor.axonclient.common.configuration;

import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.config.SagaConfiguration;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.saga.EndSaga;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.saga.StartSaga;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;

@Slf4j
class FluxCapacitorConfigurationTest {

    private CommandListener commandListener;
    private EventListener eventListener;
    private Configuration configuration;

    @BeforeEach
    void setUp() {
        commandListener = spy(new CommandListener());
        eventListener = spy(new EventListener());

        Configurer configurer = DefaultConfigurer.defaultConfiguration()
                .registerCommandHandler(c -> commandListener)
                .registerModule(new EventHandlingConfiguration().registerEventHandler(c -> eventListener))
                .configureAggregate(Aggregate.class)
                .registerModule(SagaConfiguration.subscribingSagaManager(Saga.class));
        configuration = new InMemoryFluxCapacitorConfiguration().configure(configurer).buildConfiguration();
        configuration.start();
    }

    @AfterEach
    void tearDown() {
        configuration.shutdown();
    }

    @Test
    void testConfiguration() throws Exception {
        CommandGateway commandGateway = configuration.commandGateway();
        assertEquals(100L, commandGateway.send(100L).get());

        String aggregateId = UUID.randomUUID().toString();
        String testValue = "test1";
        assertEquals(aggregateId, commandGateway.send(new CreateAggregate(aggregateId, testValue)).get());
        assertEquals(testValue, commandGateway.send(new UpdateAggregate(aggregateId, testValue)).get());
    }

    @NoArgsConstructor
    private static class Aggregate {

        @AggregateIdentifier
        private String id;

        @CommandHandler
        public Aggregate(CreateAggregate command) {
            apply(command);
        }

        @CommandHandler
        public Object handleCommand(UpdateAggregate command) {
            apply(command);
            return command.getValue();
        }

        @EventSourcingHandler
        void handleEvent(CreateAggregate event) {
            id = event.getTarget();
        }
    }

    private static class EventListener {
        @EventHandler
        void handle(CreateAggregate event) {
            log.info("Event {} handled by event listener", event);
        }
    }

    private static class CommandListener {
        @CommandHandler
        public Object handle(Long command) {
            return command;
        }
    }

    public static class Saga {
        @StartSaga
        @SagaEventHandler(associationProperty = "target")
        void handle(CreateAggregate event) {
            log.info("Event {} handled by saga", event);
        }

        @EndSaga
        @SagaEventHandler(associationProperty = "target")
        void handle(UpdateAggregate event) {
            log.info("Event {} handled by saga", event);
        }
    }

    @Value
    private static class CreateAggregate {
        @TargetAggregateIdentifier
        String target;
        String value;
    }

    @Value
    private static class UpdateAggregate {
        @TargetAggregateIdentifier
        String target;
        String value;
    }

}