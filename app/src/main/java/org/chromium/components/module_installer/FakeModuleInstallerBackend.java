// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.module_installer;

import android.content.Context;
import android.content.pm.PackageManager;

import com.google.android.play.core.splitcompat.SplitCompat;
import com.google.android.play.core.splitcompat.ingestion.Verifier;

import org.chromium.base.BuildInfo;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.task.AsyncTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Backend that looks for module APKs on the device's disk instead of invoking the Play core API to
 * install a feature module. This backend is used for testing purposes where the module is not
 * uploaded to the Play server.
 */
class FakeModuleInstallerBackend extends ModuleInstallerBackend {
    private static final String TAG = "FakeModInBackend";
    private static final String MODULES_SRC_DIRECTORY_PATH = "/data/local/tmp/modules";

    public FakeModuleInstallerBackend(OnFinishedListener listener) {
        super(listener);
    }

    /**
     * Copies {MODULES_SRC_DIRECTORY_PATH}/|moduleName|.apk to the folder where SplitCompat expects
     * downloaded modules to be. Then calls SplitCompat to emulate the module.
     *
     * We copy the module so that this works on non-rooted devices. The path SplitCompat expects
     * module to be is not accessible without rooting.
     */
    @Override
    public void install(String moduleName) {
        ThreadUtils.assertOnUiThread();

        new AsyncTask<Boolean>() {
            @Override
            protected Boolean doInBackground() {
                return installInternal(moduleName);
            }

            @Override
            protected void onPostExecute(Boolean success) {
                onFinished(success, Arrays.asList(moduleName));
            }
        }
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void installDeferred(String moduleName) {}

    @Override
    public void close() {
        // No open resources. Nothing to be done here.
    }

    private boolean installInternal(String moduleName) {
        Context context = ContextUtils.getApplicationContext();
        int versionCode = BuildInfo.getInstance().versionCode;
        // Path where SplitCompat looks for downloaded modules. May change in future releases of
        // the Play Core SDK.
        File dstModuleFile = joinPaths(context.getFilesDir().getPath(), "splitcompat",
                Integer.toString(versionCode), "unverified-splits", moduleName + ".apk");
        File srcModuleFile = joinPaths(MODULES_SRC_DIRECTORY_PATH, moduleName + ".apk");

        // NOTE: Need to give Chrome storage permission for this to work.
        try {
            dstModuleFile.getParentFile().mkdirs();
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to create module dir", e);
            return false;
        }

        try (FileInputStream istream = new FileInputStream(srcModuleFile);
                FileOutputStream ostream = new FileOutputStream(dstModuleFile)) {
            ostream.getChannel().transferFrom(istream.getChannel(), 0, istream.getChannel().size());
        } catch (RuntimeException | IOException e) {
            Log.e(TAG, "Failed to install module", e);
            return false;
        }

        // Check that the module's signature matches Chrome's.
        try {
            Verifier verifier = new Verifier(context);
            if (!verifier.verifySplits()) {
                return false;
            }
        } catch (IOException | PackageManager.NameNotFoundException e) {
            return false;
        }

        // Tell SplitCompat to do a full emulation of the module.
        return SplitCompat.fullInstall(context);
    }

    private File joinPaths(String... paths) {
        File result = new File("");
        for (String path : paths) {
            result = new File(result, path);
        }
        return result;
    }
}
