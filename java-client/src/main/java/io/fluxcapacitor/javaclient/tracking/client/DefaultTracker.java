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

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.TrackingConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static io.fluxcapacitor.javaclient.tracking.BatchInterceptor.join;

/**
 * A tracker keeps reading messages until it is stopped (generally only when the application is shut down).
 * <p>
 * A tracker is always running in a single thread. To balance the processing load over multiple threads create
 * multiple trackers with the same name but different channel.
 * <p>
 * Trackers with different names will receive the same messages. Trackers with the same name will not. (Flux Capacitor
 * will load balance between trackers with the same name).
 * <p>
 * Tracking stops if the provided message consumer throws an exception while handling messages (i.e. the tracker
 * will need to be manually restarted in that case). However, if the tracker encounters an exception while fetching
 * messages it will retry fetching indefinitely until this succeeds.
 * <p>
 * Trackers can choose a desired maximum batch size for consuming. By default this batch size will be the same as the
 * batch size the tracker uses to fetch messages from Flux Capacitor. Each time the consumer has finished
 * consuming a batch the tracker will update its position with Flux Capacitor.
 * <p>
 * Trackers can be configured to use batch interceptors. A batch interceptor manages the invocation of the message
 * consumer. It is therefore typically used to manage a database transaction around the invocation of the consumer.
 * Note that if the interceptor gives rise to an exception the tracker will be stopped.
 */
@Slf4j
public class DefaultTracker implements Runnable, Registration {

    private final String name;
    private final int channel;
    private final TrackingConfiguration configuration;
    private final Consumer<MessageBatch> processor;
    private final Consumer<List<SerializedMessage>> consumer;
    private final TrackingClient trackingClient;

    private final AtomicBoolean running = new AtomicBoolean();

    public DefaultTracker(String name, int channel, TrackingConfiguration configuration,
                          Consumer<List<SerializedMessage>> consumer, TrackingClient trackingClient) {
        this.name = name;
        this.channel = channel;
        this.configuration = configuration;
        this.processor = join(configuration.getBatchInterceptors()).intercept(this::processAll, new Tracker(name, channel));
        this.consumer = consumer;
        this.trackingClient = trackingClient;
    }

    @Override
    public void run() {
        if (running.compareAndSet(false, true)) {
            while (running.get()) {
                MessageBatch batch = fetch();
                processor.accept(batch);
            }
        }
    }

    @Override
    public void cancel() {
        running.compareAndSet(true, false);
    }

    protected MessageBatch fetch() {
        return retryOnFailure(
                () -> trackingClient
                        .read(name, channel, configuration.getMaxFetchBatchSize(), configuration.getMaxWaitDuration(),
                              configuration.getTypeFilter(), configuration.ignoreMessageTarget()),
                configuration.getRetryDelay(), e -> running.get());
    }

    protected void processAll(MessageBatch messageBatch) {
        List<SerializedMessage> messages = messageBatch.getMessages();
        if (messages.isEmpty() || !running.get()) {
            return;
        }
        if (messages.size() > configuration.getMaxConsumerBatchSize()) {
            for (int i = 0; i < messages.size(); i += configuration.getMaxConsumerBatchSize()) {
                List<SerializedMessage> batch =
                        messages.subList(i, Math.min(i + configuration.getMaxConsumerBatchSize(), messages.size()));
                processPart(batch, messageBatch.getSegment());
            }
        } else {
            processPart(messages, messageBatch.getSegment());
        }
    }

    protected void processPart(List<SerializedMessage> batch, int[] segment) {
        try {
            consumer.accept(batch);
        } catch (Exception e) {
            log.error("Consumer {} failed to handle batch of {} messages and did not handle exception. "
                              + "Tracker will be stopped.", name, batch.size(), e);
            cancel();
            throw e;
        }
        retryOnFailure(() -> trackingClient.storePosition(name, segment, batch.get(batch.size() - 1).getIndex()),
                       configuration.getRetryDelay(), e -> running.get());
    }


}
