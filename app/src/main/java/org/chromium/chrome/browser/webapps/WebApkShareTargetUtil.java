// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONException;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.StrictModeContext;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.net.MimeTypeFilter;
import org.chromium.webapk.lib.common.WebApkMetaDataKeys;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Computes data for Post Share Target.
 */
public class WebApkShareTargetUtil {
    // A class containing data required to generate a share target post request.
    protected static class PostData {
        public boolean isMultipartEncoding;
        public String[] names;
        public byte[][] values;
        public String[] filenames;
        public String[] types;
    }

    private static Bundle computeShareTargetMetaData(
            String apkPackageName, WebApkInfo.ShareData shareData) {
        if (shareData == null) {
            return null;
        }
        ActivityInfo shareActivityInfo;
        try {
            shareActivityInfo =
                    ContextUtils.getApplicationContext().getPackageManager().getActivityInfo(
                            new ComponentName(apkPackageName, shareData.shareActivityClassName),
                            PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }

        if (shareActivityInfo == null) {
            return null;
        }
        return shareActivityInfo.metaData;
    }

    private static boolean enctypeFromMetaDataIsMultipart(Bundle metaData) {
        String enctype = IntentUtils.safeGetString(metaData, WebApkMetaDataKeys.SHARE_ENCTYPE);
        return enctype != null && "multipart/form-data".equals(enctype.toLowerCase(Locale.ENGLISH));
    }

    private static byte[] readStringFromContentUri(Uri uri) {
        try (InputStream inputStream =
                        ContextUtils.getApplicationContext().getContentResolver().openInputStream(
                                uri)) {
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];

            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            return byteBuffer.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static String getFileTypeFromContentUri(Uri uri) {
        return ContextUtils.getApplicationContext().getContentResolver().getType(uri);
    }

    private static String getFileNameFromContentUri(Uri uri) {
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = ContextUtils.getApplicationContext().getContentResolver().query(
                         uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String result =
                            cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        return uri.getPath();
    }

    private static ArrayList<String> decodeJsonStringArray(JSONArray jsonArray)
            throws JSONException {
        ArrayList<String> originalData = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            originalData.add(jsonArray.getString(i));
        }
        return originalData;
    }

    private static ArrayList<ArrayList<String>> decodeJsonAccepts(String string)
            throws JSONException {
        JSONArray jsonArray = new JSONArray(string);
        ArrayList<ArrayList<String>> originalData = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            originalData.add(decodeJsonStringArray(jsonArray.getJSONArray(i)));
        }
        return originalData;
    }

    protected static PostData computeMultipartPostData(
            Bundle shareTargetMetaData, WebApkInfo.ShareData shareData) {
        String nameString = IntentUtils.safeGetString(
                shareTargetMetaData, WebApkMetaDataKeys.SHARE_PARAM_NAMES);
        String acceptString = IntentUtils.safeGetString(
                shareTargetMetaData, WebApkMetaDataKeys.SHARE_PARAM_ACCEPTS);
        ArrayList<Uri> fileUris = shareData.files;

        if (nameString == null || acceptString == null) {
            return null;
        }

        if (fileUris == null) {
            return null;
        }
        ArrayList<String> names;
        ArrayList<ArrayList<String>> accepts;

        try {
            names = decodeJsonStringArray(new JSONArray(nameString));
        } catch (JSONException e) {
            return null;
        }

        try {
            accepts = decodeJsonAccepts(acceptString);
        } catch (JSONException e) {
            return null;
        }

        if (names.size() != accepts.size()) {
            return null;
        }

        PostData postData = new PostData();
        postData.isMultipartEncoding = true;
        ArrayList<String> shareNames = new ArrayList<>();
        ArrayList<byte[]> shareValues = new ArrayList<>();
        ArrayList<String> shareFilenames = new ArrayList<>();
        ArrayList<String> shareTypes = new ArrayList<>();

        try (StrictModeContext strictModeContextUnused = StrictModeContext.allowDiskReads()) {
            for (Uri fileUri : fileUris) {
                String fileType = getFileTypeFromContentUri(fileUri);
                String fileName = getFileNameFromContentUri(fileUri);

                if (fileType == null || fileName == null) {
                    continue;
                }

                for (int i = 0; i < names.size(); i++) {
                    List<String> mimeTypeList = accepts.get(i);
                    MimeTypeFilter mimeTypeFilter = new MimeTypeFilter(mimeTypeList, false);
                    if (mimeTypeFilter.accept(fileUri, fileType)) {
                        byte[] fileContent = readStringFromContentUri(fileUri);
                        if (fileContent != null) {
                            shareNames.add(names.get(i));
                            shareValues.add(fileContent);
                            shareFilenames.add(fileName);
                            shareTypes.add(fileType);
                        }
                        break;
                    }
                }
            }
        }
        postData.names = shareNames.toArray(new String[0]);
        postData.values = shareValues.toArray(new byte[0][]);
        postData.filenames = shareFilenames.toArray(new String[0]);
        postData.types = shareTypes.toArray(new String[0]);
        return postData;
    }

    protected static PostData computeUrlEncodedPostData(
            Bundle shareTargetMetaData, WebApkInfo.ShareData shareData) {
        PostData postData = new PostData();
        postData.isMultipartEncoding = false;
        postData.filenames = new String[0];
        postData.types = new String[0];

        ArrayList<String> names = new ArrayList<>();
        ArrayList<byte[]> values = new ArrayList<>();

        String shareTitleName = IntentUtils.safeGetString(
                shareTargetMetaData, WebApkMetaDataKeys.SHARE_PARAM_TITLE);
        String shareTextName =
                IntentUtils.safeGetString(shareTargetMetaData, WebApkMetaDataKeys.SHARE_PARAM_TEXT);

        if (shareTitleName != null && shareData.subject != null) {
            names.add(shareTitleName);
            values.add(ApiCompatibilityUtils.getBytesUtf8(shareData.subject));
        }
        if (shareTextName != null && shareData.text != null) {
            names.add(shareTextName);
            values.add(ApiCompatibilityUtils.getBytesUtf8(shareData.text));
        }

        postData.names = names.toArray(new String[0]);
        postData.values = values.toArray(new byte[0][]);
        return postData;
    }

    protected static boolean methodFromShareTargetMetaDataIsPost(Bundle metaData) {
        String method = IntentUtils.safeGetString(metaData, WebApkMetaDataKeys.SHARE_METHOD);
        return method != null && "POST".equals(method.toUpperCase(Locale.ENGLISH));
    }

    protected static PostData computePostData(
            String apkPackageName, WebApkInfo.ShareData shareData) {
        Bundle shareTargetMetaData = computeShareTargetMetaData(apkPackageName, shareData);
        if (shareTargetMetaData == null
                || !methodFromShareTargetMetaDataIsPost(shareTargetMetaData)) {
            return null;
        }
        if (enctypeFromMetaDataIsMultipart(shareTargetMetaData)) {
            return computeMultipartPostData(shareTargetMetaData, shareData);
        }
        return computeUrlEncodedPostData(shareTargetMetaData, shareData);
    }
}
