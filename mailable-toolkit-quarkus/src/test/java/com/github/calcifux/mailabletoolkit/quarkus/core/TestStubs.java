package com.github.calcifux.mailabletoolkit.quarkus.core;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-rolled CDI/MP-Config stubs so {@link MailCdiService}'s wiring can be unit-tested without a CDI
 * container or a running Redis (the heavy Redis Streams path is covered in the backend integration phase).
 */
final class TestStubs {

    private TestStubs() {
    }

    /** A {@link Config} backed by a plain map. Only the methods MailCdiService uses are implemented. */
    static final class MapConfig implements Config {
        private final Map<String, String> values = new LinkedHashMap<>();

        MapConfig put(String key, String value) {
            values.put(key, value);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getValue(String propertyName, Class<T> propertyType) {
            return (T) getOptionalValue(propertyName, propertyType)
                    .orElseThrow(() -> new java.util.NoSuchElementException(propertyName));
        }

        @Override
        public ConfigValue getConfigValue(String propertyName) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
            String raw = values.get(propertyName);
            if (raw == null) {
                return Optional.empty();
            }
            if (propertyType == String.class) {
                return Optional.of((T) raw);
            }
            if (propertyType == Integer.class) {
                return Optional.of((T) Integer.valueOf(raw));
            }
            if (propertyType == Long.class) {
                return Optional.of((T) Long.valueOf(raw));
            }
            return Optional.of((T) raw);
        }

        @Override
        public Iterable<String> getPropertyNames() {
            return values.keySet();
        }

        @Override
        public Iterable<ConfigSource> getConfigSources() {
            return Collections.emptyList();
        }

        @Override
        public <T> Optional<Converter<T>> getConverter(Class<T> forType) {
            return Optional.empty();
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            throw new UnsupportedOperationException();
        }
    }

    /** An empty {@link Instance} of {@link RedisDataSource} — {@code isUnsatisfied()} is true (inmemory path). */
    static final class EmptyRedisInstance implements Instance<RedisDataSource> {
        @Override
        public Instance<RedisDataSource> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends RedisDataSource> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends RedisDataSource> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            return true;
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public void destroy(RedisDataSource instance) {
        }

        @Override
        public Handle<RedisDataSource> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<RedisDataSource>> handles() {
            return List.of();
        }

        @Override
        public RedisDataSource get() {
            throw new UnsupportedOperationException("no RedisDataSource in this test");
        }

        @Override
        public Iterator<RedisDataSource> iterator() {
            return Collections.emptyIterator();
        }
    }
}
