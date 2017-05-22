// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.common;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

import org.chromium.base.annotations.MainDex;
import org.chromium.base.annotations.UsedByReflection;

/**
 * Parcelable class that contains file descriptor and file region information to
 * be passed to child processes.
 */
@MainDex
@UsedByReflection("child_process_launcher_android.cc")
// TODO(crbug.com/635567): Fix this properly.
@SuppressLint("ParcelClassLoader")
public final class FileDescriptorInfo implements Parcelable {
    public final int mId;
    public final ParcelFileDescriptor mFd;
    public final long mOffset;
    public final long mSize;

    public FileDescriptorInfo(int id, ParcelFileDescriptor fd, long offset, long size) {
        mId = id;
        mFd = fd;
        mOffset = offset;
        mSize = size;
    }

    FileDescriptorInfo(Parcel in) {
        mId = in.readInt();
        mFd = in.readParcelable(null);
        mOffset = in.readLong();
        mSize = in.readLong();
    }

    @Override
    public int describeContents() {
        return CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeParcelable(mFd, CONTENTS_FILE_DESCRIPTOR);
        dest.writeLong(mOffset);
        dest.writeLong(mSize);
    }

    public static final Parcelable.Creator<FileDescriptorInfo> CREATOR =
            new Parcelable.Creator<FileDescriptorInfo>() {
        @Override
        public FileDescriptorInfo createFromParcel(Parcel in) {
            return new FileDescriptorInfo(in);
        }

        @Override
        public FileDescriptorInfo[] newArray(int size) {
            return new FileDescriptorInfo[size];
        }
    };
}
