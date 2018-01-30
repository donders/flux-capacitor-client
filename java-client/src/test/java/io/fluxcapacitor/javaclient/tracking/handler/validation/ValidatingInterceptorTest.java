package io.fluxcapacitor.javaclient.tracking.handler.validation;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import lombok.Value;
import org.junit.Test;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class ValidatingInterceptorTest {

    private ValidatingInterceptor subject = new ValidatingInterceptor();
    private Function<Object, DeserializingMessage> messageFactory = payload -> new DeserializingMessage(
            new DeserializingObject<>(new SerializedMessage(new Data<>("test".getBytes(), "test", 0)), () -> payload));

    @Test
    public void testWithConstraintViolations() {
        DeserializingMessage message =
                messageFactory.apply(new ConstrainedObject(null, 3, new ConstrainedObjectMember(false)));
        try {
            subject.interceptHandling(m -> null).apply(message);
        } catch (ValidationException e) {
            assertEquals(3, e.getViolations().size());
        }
    }

    @Test
    public void testWithoutConstraintViolations() {
        DeserializingMessage message =
                messageFactory.apply(new ConstrainedObject("foo", 5, new ConstrainedObjectMember(true)));
        subject.interceptHandling(m -> null).apply(message);
    }

    @Test
    public void testObjectWithoutAnnotations() {
        DeserializingMessage message = messageFactory.apply(new Object());
        subject.interceptHandling(m -> null).apply(message);
    }

    @Value
    private static class ConstrainedObject {
        @NotNull String string;
        @Min(5) long number;
        @Valid ConstrainedObjectMember member;
    }

    @Value
    private static class ConstrainedObjectMember {
        @AssertTrue boolean aBoolean;
    }

}