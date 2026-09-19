package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.exc.InvalidDefinitionException;
import com.guicedee.activitymaster.fsdm.client.services.IInvolvedPartyService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.sessions.services.IUserSession;
import io.smallrye.mutiny.Uni;

import lombok.extern.log4j.Log4j2;

import java.io.Serial;
import java.text.MessageFormat;
import java.util.*;

import static com.guicedee.client.IGuiceContext.*;
import static com.guicedee.client.implementations.ObjectBinderKeys.*;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Log4j2
public class UserSession implements IUserSession<UserSession> {
    @Serial
    private static final long serialVersionUID = 1L;
    @JsonValue
    private final Map<String, Object> values = new LinkedHashMap<>();

    private UUID resourceItemID;
    private UUID dataID;

    @Override
    public String toString() {
        try {
            return get(DefaultObjectMapper).writerWithDefaultPrettyPrinter()
                                           .withoutFeatures(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                           .writeValueAsString(this);
        } catch (JacksonException e) {
            log.error("Couldn't make Session Output", e);
            return "Something went very wrong!" + e.getMessage();
        }
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public IUserSession<?> addValue(String key, Object object) {
        String result;
        if (!(object instanceof String)) {
            try {
                result = get(DefaultObjectMapper).writeValueAsString(object);
            } catch (JacksonException e) {
                throw new RuntimeException(e);
            }
        } else {
            result = object.toString();
        }
        if (values.containsKey(key) && values.get(key).equals(result)) {
            log.trace("No session update required, value is the same");
        } else {
            values.put(key, result);
        }
        return this;
    }

    @Override
    public boolean hasValue(String key) {
        return values.containsKey(key);
    }

    @Override
    public IUserSession<?> removeValue(String key) {
        values.remove(key);
        return this;
    }

    @Override
    public <T> T as(String key, Class<T> type) {
        var value = values.get(key);
        try {
            if (value == null) {
                return null;
            }
            if (type.equals(String.class)) {
                return (T) value;
            }

            return get(DefaultObjectMapper).readerFor(type).readValue(value.toString());
        } catch (InvalidDefinitionException ide) {
            log.debug(MessageFormat.format("Invalid Session Object Deserialization - {0} / {1}",
                                           key,
                                           type.getSimpleName()));
            return null;
        } catch (Throwable e) {
            log.warn("Unable to deserialize session object - ", e);
            return null;
        }
    }

    @Override
    public Map<String, Object> getValues() {
        return values;
    }

    @Override
    public UUID getResourceItemID() {
        return resourceItemID;
    }

    @Override
    public UserSession setResourceItemID(UUID resourceItemID) {
        this.resourceItemID = resourceItemID;
        return this;
    }

    @Override
    public UUID getDataID() {
        return dataID;
    }

    @Override
    public UserSession setDataID(UUID dataID) {
        this.dataID = dataID;
        return this;
    }
}