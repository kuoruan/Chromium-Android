// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.modelutil;

import android.support.v4.util.ObjectsCompat;

import org.chromium.base.annotations.RemovableInRelease;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic property model that aims to provide an extensible and efficient model for ease of use.
 */
public class PropertyModel extends PropertyObservable<PropertyKey> {
    /** The key type for read-ony boolean model properties. */
    public static class ReadableBooleanPropertyKey implements PropertyKey {}

    /** The key type for mutable boolean model properties. */
    public final static class WritableBooleanPropertyKey extends ReadableBooleanPropertyKey {}

    /** The key type for read-only float model properties. */
    public static class ReadableFloatPropertyKey implements PropertyKey {}

    /** The key type for mutable float model properties. */
    public final static class WritableFloatPropertyKey extends ReadableFloatPropertyKey {}

    /** The key type for read-only int model properties. */
    public static class ReadableIntPropertyKey implements PropertyKey {}

    /** The key type for mutable int model properties. */
    public final static class WritableIntPropertyKey extends ReadableIntPropertyKey {}

    /**
     * The key type for read-only Object model properties.
     *
     * @param <T> The type of the Object being tracked by the key.
     */
    public static class ReadableObjectPropertyKey<T> implements PropertyKey {}

    /**
     * The key type for mutable Object model properties.
     *
     * @param <T> The type of the Object being tracked by the key.
     */
    public final static class WritableObjectPropertyKey<T>
            extends ReadableObjectPropertyKey<T> implements PropertyKey {}

    private final Map<PropertyKey, ValueContainer> mData;

    /**
     * Constructs a model for the given list of keys.
     *
     * @param keys The key types supported by this model.
     */
    public PropertyModel(PropertyKey... keys) {
        this(buildData(keys));
    }

    private PropertyModel(Map<PropertyKey, ValueContainer> startingValues) {
        mData = startingValues;
    }

    @RemovableInRelease
    private void validateKey(PropertyKey key) {
        if (!mData.containsKey(key)) {
            throw new IllegalArgumentException("Invalid key passed in: " + key);
        }
    }

    /**
     * Get the current value from the float based key.
     */
    public float get(ReadableFloatPropertyKey key) {
        validateKey(key);
        FloatContainer container = (FloatContainer) mData.get(key);
        return container == null ? 0f : container.value;
    }

    /**
     * Set the value for the float based key.
     */
    public void set(WritableFloatPropertyKey key, float value) {
        validateKey(key);
        FloatContainer container = (FloatContainer) mData.get(key);
        if (container == null) {
            container = new FloatContainer();
            mData.put(key, container);
        } else if (container.value == value) {
            return;
        }

        container.value = value;
        notifyPropertyChanged(key);
    }

    /**
     * Get the current value from the int based key.
     */
    public int get(ReadableIntPropertyKey key) {
        validateKey(key);
        IntContainer container = (IntContainer) mData.get(key);
        return container == null ? 0 : container.value;
    }

    /**
     * Set the value for the int based key.
     */
    public void set(WritableIntPropertyKey key, int value) {
        validateKey(key);
        IntContainer container = (IntContainer) mData.get(key);
        if (container == null) {
            container = new IntContainer();
            mData.put(key, container);
        } else if (container.value == value) {
            return;
        }

        container.value = value;
        notifyPropertyChanged(key);
    }

    /**
     * Get the current value from the boolean based key.
     */
    public boolean get(ReadableBooleanPropertyKey key) {
        validateKey(key);
        BooleanContainer container = (BooleanContainer) mData.get(key);
        return container == null ? false : container.value;
    }

    /**
     * Set the value for the boolean based key.
     */
    public void set(WritableBooleanPropertyKey key, boolean value) {
        validateKey(key);
        BooleanContainer container = (BooleanContainer) mData.get(key);
        if (container == null) {
            container = new BooleanContainer();
            mData.put(key, container);
        } else if (container.value == value) {
            return;
        }

        container.value = value;
        notifyPropertyChanged(key);
    }

    /**
     * Get the current value from the object based key.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(ReadableObjectPropertyKey<T> key) {
        validateKey(key);
        ObjectContainer<T> container = (ObjectContainer<T>) mData.get(key);
        return container == null ? null : container.value;
    }

    /**
     * Set the value for the Object based key.
     */
    @SuppressWarnings("unchecked")
    public <T> void set(WritableObjectPropertyKey<T> key, T value) {
        validateKey(key);
        ObjectContainer<T> container = (ObjectContainer<T>) mData.get(key);
        if (container == null) {
            container = new ObjectContainer<T>();
            mData.put(key, container);
        } else if (ObjectsCompat.equals(container.value, value)) {
            return;
        }

        container.value = value;
        notifyPropertyChanged(key);
    }

    @Override
    public Collection<PropertyKey> getAllSetProperties() {
        List<PropertyKey> properties = new ArrayList<>();
        for (Map.Entry<PropertyKey, ValueContainer> entry : mData.entrySet()) {
            if (entry.getValue() != null) properties.add(entry.getKey());
        }
        return properties;
    }

    /**
     * Allows constructing a new {@link PropertyModel} with read-only properties.
     */
    public static class Builder {
        private final Map<PropertyKey, ValueContainer> mData;

        public Builder(PropertyKey... keys) {
            this(buildData(keys));
        }

        private Builder(Map<PropertyKey, ValueContainer> values) {
            mData = values;
        }

        @RemovableInRelease
        private void validateKey(PropertyKey key) {
            if (!mData.containsKey(key)) {
                throw new IllegalArgumentException("Invalid key passed in: " + key);
            }
        }

        public Builder with(ReadableFloatPropertyKey key, float value) {
            validateKey(key);
            FloatContainer container = new FloatContainer();
            container.value = value;
            mData.put(key, container);
            return this;
        }

        public Builder with(ReadableIntPropertyKey key, int value) {
            validateKey(key);
            IntContainer container = new IntContainer();
            container.value = value;
            mData.put(key, container);
            return this;
        }

        public Builder with(ReadableBooleanPropertyKey key, boolean value) {
            validateKey(key);
            BooleanContainer container = new BooleanContainer();
            container.value = value;
            mData.put(key, container);
            return this;
        }

        public <T> Builder with(ReadableObjectPropertyKey<T> key, T value) {
            validateKey(key);
            ObjectContainer<T> container = new ObjectContainer<>();
            container.value = value;
            mData.put(key, container);
            return this;
        }

        public PropertyModel build() {
            return new PropertyModel(mData);
        }
    }

    private static Map<PropertyKey, ValueContainer> buildData(PropertyKey[] keys) {
        Map<PropertyKey, ValueContainer> data = new HashMap<>();
        for (PropertyKey key : keys) {
            if (data.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate key: " + key);
            }
            data.put(key, null);
        }
        return data;
    }

    private interface ValueContainer {}
    private static class FloatContainer implements ValueContainer { public float value; }
    private static class IntContainer implements ValueContainer { public int value; }
    private static class BooleanContainer implements ValueContainer { public boolean value; }
    private static class ObjectContainer<T> implements ValueContainer { public T value; }
}
