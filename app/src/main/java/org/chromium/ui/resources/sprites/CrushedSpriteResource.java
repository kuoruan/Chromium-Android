// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.resources.sprites;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.JsonReader;

import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.ui.resources.Resource;


import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * A {@link Resource} that provides an unscaled {@link Bitmap} and corresponding metadata for a
 * crushed sprite. A crushed sprite animation is run by drawing rectangles from a bitmap to a
 * canvas. Each frame in the animation draws its rectangles on top of the previous frame.
 */
public class CrushedSpriteResource implements Resource {
    private static final Rect EMPTY_RECT = new Rect();

    private Bitmap mBitmap;
    private final Rect mBitmapSize = new Rect();
    private float mScaledSpriteWidth;
    private float mScaledSpriteHeight;
    private int mUnscaledSpriteWidth;
    private int mUnscaledSpriteHeight;
    private int[][] mRectangles;

    /**
     * @param bitmapResId The id of the bitmap resource.
     * @param metadataResId The id of the raw resource containing JSON metadata.
     * @param resources A {@link Resources} instance to load assets from.
     */
    public CrushedSpriteResource(int bitmapResId, int metadataResId, Resources resources) {
        mBitmap = loadBitmap(bitmapResId, resources);

        if (mBitmap != null) {
            mBitmapSize.set(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
            try {
                TraceEvent.begin("CrushedSpriteResource.parseMetadata");
                parseMetadata(metadataResId, mBitmap.getDensity(), resources);
                TraceEvent.end("CrushedSpriteResource.parseMetadata");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Convenience method for use in reloading an unscaled {@link Bitmap} without recreating the
     * entire resource.
     * @param bitmapResId The id of the bitmap resource for this crushed sprite.
     * @param resources A {@link Resources} instance to load assets from.
     */
    public static Bitmap loadBitmap(int bitmapResId, Resources resources) {
        TraceEvent.begin("CrushedSpriteResource.loadBitmap");
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(resources, bitmapResId, opts);
        TraceEvent.end("CrushedSpriteResource.loadBitmap");
        return bitmap;
    }

    @Override
    public Bitmap getBitmap() {
        return mBitmap;
    }

    @Override
    public Rect getBitmapSize() {
        return mBitmapSize;
    }

    @Override
    public Rect getPadding() {
        return EMPTY_RECT;
    }

    @Override
    public Rect getAperture() {
        return EMPTY_RECT;
    }

    /**
     * @return The scaled width of an individual sprite in px.
     */
    public float getScaledSpriteWidth() {
        return mScaledSpriteWidth;
    }

    /**
     * @return The scaled height of an individual sprite in px.
     */
    public float getScaledSpriteHeight() {
        return mScaledSpriteHeight;
    }

    /**
     * @return The unscaled width of an individual sprite in px.
     */
    public int getUnscaledSpriteWidth() {
        return mUnscaledSpriteWidth;
    }

    /**
     * @return The unscaled height of an individual sprite in px.
     */
    public int getUnscaledSpriteHeight() {
        return mUnscaledSpriteHeight;
    }

    /**
     * Each sprite frame is represented by a set of rectangles. Most frames consist of small
     * rectangles representing the change from the previous frame. Each rectangle is represented
     * using six consecutive values that specify the values to be used when creating the destination
     * and source rectangles that get painted:
     * 0: destination x   1: destination y   2: source x   3: source y   4: width   5: height
     *
     * @return The unscaled rectangles that need to be painted for each sprite frame in px.
     */
    public int[][] getFrameRectangles() {
        return mRectangles.clone();
    }

    /**
     * Parses the raw JSON resource specified by {@code metadataResId}. The JSON is expected to be
     * in this format:
     * {
     *   "apiVersion": <version number (string)>,
     *   "scaledSpriteWidthDp": <scaled sprite width in dp (int)>,
     *   "scaledSpriteHeightDp": <scaled sprite width in dp (int)>,
     *   "densities":
     *   [
     *     {
     *       "density": <density (int)>,
     *       "width": <unscaled sprite width in px (int)>,
     *       "height": <unscaled sprite height in px (int)>,
     *       "rectangles": [
     *         [<list of ints for frame 0>],
     *         [<list of ints for frame 1>],
     *          ......
     *       ]
     *     },
     *     {
     *       "density": <density (int)>,
     *       "width": <unscaled sprite width in px (int)>,
     *       "height": <unscaled sprite height in px (int)>,
     *       "rectangles": [
     *         [<list of ints for frame 0>],
     *         [<list of ints for frame 1>],
     *         ......
     *       ]
     *     },
     *     ......
     *   ]
     * }
     *
     * @param metadataResId The id of the raw resource containing JSON to parse.
     * @param bitmapDensity The density of the unscaled {@link Bitmap} that was loaded.
     * @param resources A {@link Resources} instance to load assets from.
     * @throws IOException
     */
    @VisibleForTesting
    void parseMetadata(int metadataResId, int bitmapDensity, Resources resources)
            throws IOException {
        InputStream inputStream = resources.openRawResource(metadataResId);
        JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
        try {
            reader.beginObject(); // Start reading top-level object.

            // Check apiVersion.
            String name = reader.nextName();
            assert name.equals("apiVersion");
            String version = reader.nextString();
            assert version.equals("1.0");

            // Get scaled sprite dimensions.
            float dpToPx = resources.getDisplayMetrics().density;
            name = reader.nextName();
            assert name.equals("scaledSpriteWidthDp");
            mScaledSpriteWidth = reader.nextInt() * dpToPx;
            name = reader.nextName();
            assert name.equals("scaledSpriteHeightDp");
            mScaledSpriteHeight = reader.nextInt() * dpToPx;

            // Parse array of densities.
            name = reader.nextName();
            assert name.equals("densities");
            reader.beginArray(); // Start reading array of densities.
            while (reader.hasNext()) {
                reader.beginObject(); // Start reading object for this density.
                boolean foundDensity = parseMetadataForDensity(reader, bitmapDensity);
                reader.endObject(); // Stop reading object for this density.

                if (foundDensity) break;
            }
        } finally {
            reader.close();
            inputStream.close();
        }
    }

    /**
     * Reads a JSON object for a specific density and populates variables if the density matches
     * {@code bitmapDensity}.
     *
     * @param reader The JsonReader reading the JSON metadata.
     * @param bitmapDensity The density of the unscaled {@link Bitmap} that was loaded.
     * @return True if the JSON object being parsed corresponds to bitmapDensity.
     * @throws IOException
     */
    private boolean parseMetadataForDensity(JsonReader reader, int bitmapDensity)
            throws IOException {
        String name = reader.nextName();
        assert name.equals("density");
        int density = reader.nextInt();

        // If this is metadata for a density other than bitmapDensity, skip parsing the rest of the
        // object.
        if (density != bitmapDensity) {
            reader.skipValue(); // Skip width name.
            reader.skipValue(); // Skip width value.
            reader.skipValue(); // Skip height name.
            reader.skipValue(); // Skip height value.
            reader.skipValue(); // Skip rectangles name.
            reader.skipValue(); // Skip rectangles array.
            return false;
        }

        name = reader.nextName();
        assert name.equals("width");
        mUnscaledSpriteWidth = reader.nextInt();

        name = reader.nextName();
        assert name.equals("height");
        mUnscaledSpriteHeight = reader.nextInt();

        name = reader.nextName();
        assert name.equals("rectangles");

        parseFrameRectangles(reader);

        return true;
    }


    /**
     * Parses the 2D JSONArray of frame rectangles and populates {@code mRectangles}.
     * @param reader The JsonReader reading the JSON metadata.
     * @throws IOException
     */
    private void parseFrameRectangles(JsonReader reader) throws IOException {
        ArrayList<ArrayList<Integer>> allFrameRectangles = new ArrayList<ArrayList<Integer>>();
        int frameCount = 0;

        reader.beginArray(); // Start reading 2D rectangles array.
        while (reader.hasNext()) {
            ArrayList<Integer> frameRectangles = new ArrayList<Integer>();
            reader.beginArray(); // Start reading frame array.
            while (reader.hasNext()) {
                frameRectangles.add(reader.nextInt());
            }
            reader.endArray(); // Stop reading frame array.
            allFrameRectangles.add(frameRectangles);
            frameCount++;
        }
        reader.endArray(); // Stop reading 2D rectangles array.

        // Convert 2D ArrayList to int[][].
        mRectangles = new int[frameCount][];
        for (int i = 0; i < frameCount; i++) {
            ArrayList<Integer> frameRectangles = allFrameRectangles.get(i);
            int[] frameRectanglesArray = new int[frameRectangles.size()];
            for (int j = 0; j < frameRectangles.size(); j++) {
                frameRectanglesArray[j] = frameRectangles.get(j);
            }
            mRectangles[i] = frameRectanglesArray;
        }
    }
}
