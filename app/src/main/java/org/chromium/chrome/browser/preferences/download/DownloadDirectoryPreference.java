// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.download;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.widget.ListView;

import org.chromium.chrome.R;

/**
 * Preference that allows the user to select which folder they would like to make the default
 * location their downloads will save to.
 */
public class DownloadDirectoryPreference extends PreferenceFragment {
    private ListView mListView;
    private DownloadDirectoryAdapter mDownloadDirectoryAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActivity().setTitle(R.string.downloads_location_selector_title);
        mDownloadDirectoryAdapter = new DownloadDirectoryAdapter(getActivity());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mListView = (ListView) getView().findViewById(android.R.id.list);
        mListView.setAdapter(mDownloadDirectoryAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        mDownloadDirectoryAdapter.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        mDownloadDirectoryAdapter.stop();
    }
}
