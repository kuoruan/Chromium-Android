// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.remoteobjects;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owns a set of objects on behalf of RemoteObjectHost's client.
 *
 * These objects could contain references which would keep the WebContents alive
 * longer than expected, and so must not be held alive by any other GC root.
 */
final class RemoteObjectRegistry implements RemoteObjectImpl.ObjectIdAllocator {
    private final Set<? super RemoteObjectRegistry> mRetainingSet;
    private final Map<Integer, Object> mObjectsById = new HashMap<>();
    private final Map<Object, Integer> mIdsByObject = new HashMap<>();
    private int mNextId;

    RemoteObjectRegistry(Set<? super RemoteObjectRegistry> retainingSet) {
        retainingSet.add(this);
        mRetainingSet = retainingSet;
    }

    public void close() {
        boolean removed = mRetainingSet.remove(this);
        assert removed;
    }

    @Override
    public int getObjectId(Object object) {
        Integer existingId = mIdsByObject.get(object);
        if (existingId != null) {
            return existingId;
        }

        int newId = mNextId++;
        assert newId >= 0;
        mObjectsById.put(newId, object);
        mIdsByObject.put(object, newId);
        return newId;
    }

    @Override
    public Object getObjectById(int id) {
        return mObjectsById.get(id);
    }

    public void removeObjectById(int id) {
        Object o = mObjectsById.remove(id);
        mIdsByObject.remove(o);
    }
}
