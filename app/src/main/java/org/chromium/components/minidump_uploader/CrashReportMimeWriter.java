// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.minidump_uploader;

import org.chromium.base.annotations.JNINamespace;

import java.io.File;

/**
 * Rewrites minidumps into MIME messages for uploading.
 */
@JNINamespace("minidump_uploader")
public class CrashReportMimeWriter {
    private static final String TAG = "CrashReportMimeWriter";

    /*
     * Rewrites minidumps as MIME multipart messages, extracting embedded Crashpad annotations to
     * include as form data, and including the original minidump as a file attachment.
     *
     * @param srcDir A directory containing a crashpad::CrashReportDatabase.
     * @param destDir The directory in which to write the MIME files.
     */
    public static void rewriteMinidumpsAsMIMEs(File srcDir, File destDir) {
        nativeRewriteMinidumpsAsMIMEs(srcDir.getAbsolutePath(), destDir.getAbsolutePath());
    }

    private static native void nativeRewriteMinidumpsAsMIMEs(String srcDir, String destDir);
}
