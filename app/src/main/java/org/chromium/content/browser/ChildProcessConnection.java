// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.os.IBinder;

import org.chromium.base.process_launcher.ChildProcessCreationParams;
import org.chromium.base.process_launcher.FileDescriptorInfo;
import org.chromium.base.process_launcher.IChildProcessService;

import javax.annotation.Nullable;

/**
 * Manages a connection between the browser activity and a child service. ChildProcessConnection is
 * responsible for estabilishing the connection (start()), closing it (stop()) and manipulating the
 * bindings held onto the service (addStrongBinding(), removeStrongBinding(),
 * removeInitialBinding()).
 */
public interface ChildProcessConnection {
    /**
     * Used to notify the consumer about disconnection of the service. This callback is provided
     * earlier than ConnectionCallbacks below, as a child process might die before the connection is
     * fully set up.
     */
    interface DeathCallback {
        void onChildProcessDied(ChildProcessConnection connection);
    }

    /**
     * Used to notify the consumer about the process start. These callbacks will be invoked before
     * the ConnectionCallbacks.
     */
    interface StartCallback {
        /**
         * Called when the child process has successfully started and is ready for connection
         * setup.
         */
        void onChildStarted();

        /**
         * Called when the child process failed to start. This can happen if the process is already
         * in use by another client.
         */
        void onChildStartFailed();
    }

    /**
     * Used to notify the consumer about the connection being established.
     */
    interface ConnectionCallback {
        /**
         * Called when the connection to the service is established.
         * @param pid the pid of the child process
         */
        void onConnected(int pid);
    }

    int getServiceNumber();

    boolean isInSandbox();

    String getPackageName();

    ChildProcessCreationParams getCreationParams();

    IChildProcessService getService();

    /**
     * @return the connection pid, or 0 if not yet connected
     */
    int getPid();

    /**
     * Starts a connection to an IChildProcessService. This must be followed by a call to
     * setupConnection() to setup the connection parameters. start() and setupConnection() are
     * separate to allow to pass whatever parameters are available in start(), and complete the
     * remainder later while reducing the connection setup latency.
     * @param startCallback (optional) callback when the child process starts or fails to start.
     */
    void start(StartCallback startCallback);

    /**
     * Setups the connection after it was started with start().
     * @param commandLine (optional) will be ignored if the command line was already sent in start()
     * @param filesToBeMapped a list of file descriptors that should be registered
     * @param callback optional client specified callbacks that the child can use to communicate
     *                 with the parent process
     * @param connectionCallback will be called exactly once after the connection is set up or the
     *                           setup fails
     */
    void setupConnection(String[] commandLine, FileDescriptorInfo[] filesToBeMapped,
            @Nullable IBinder callback, ConnectionCallback connectionCallback);

    /**
     * Terminates the connection to IChildProcessService, closing all bindings. It is safe to call
     * this multiple times.
     */
    void stop();

    /** @return true iff the initial oom binding is currently bound. */
    boolean isInitialBindingBound();

    /** @return true iff the strong oom binding is currently bound. */
    boolean isStrongBindingBound();

    /**
     * Called to remove the strong binding established when the connection was started. It is safe
     * to call this multiple times.
     */
    void removeInitialBinding();

    /**
     * For live connections, this returns true iff either the initial or the strong binding is
     * bound, i.e. the connection has at least one oom binding. For connections that disconnected
     * (did not exit properly), this returns true iff the connection had at least one oom binding
     * when it disconnected.
     */
    boolean isOomProtectedOrWasWhenDied();

    /**
     * Unbinds the bindings that protect the process from oom killing. It is safe to call this
     * multiple times, before as well as after stop().
     */
    void dropOomBindings();

    /**
     * Attaches a strong binding that will make the service as important as the main process. Each
     * call should be succeeded by removeStrongBinding(), but multiple strong bindings can be
     * requested and released independently.
     */
    void addStrongBinding();

    /**
     * Called when the service is no longer in active use of the consumer.
     */
    void removeStrongBinding();

    /**
     * Attaches a moderate binding that will give the service the priority of a visible process, but
     * keep the priority below a strongly bound process.
     */
    void addModerateBinding();

    /**
     * Called when the service is no longer in moderate use of the consumer.
     */
    void removeModerateBinding();

    /** @return true iff the moderate oom binding is currently bound. */
    boolean isModerateBindingBound();
}
