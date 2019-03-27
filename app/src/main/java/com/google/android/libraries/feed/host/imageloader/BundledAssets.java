// Copyright 2018 The Feed Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.libraries.feed.host.imageloader;

import android.support.annotation.StringDef;

/** Enumerates the set of bundled assets which could be requested via the {@link ImageLoaderApi}. */
@StringDef({BundledAssets.OFFLINE_INDICATOR_BADGE, BundledAssets.VIDEO_INDICATOR_BADGE})
public @interface BundledAssets {

  /** Badge to show indicating content is available offline. */
  String OFFLINE_INDICATOR_BADGE = "offline_indicator_badge";

  /** Badge to show indicating content links to a video. */
  String VIDEO_INDICATOR_BADGE = "video_indicator_badge";
}
