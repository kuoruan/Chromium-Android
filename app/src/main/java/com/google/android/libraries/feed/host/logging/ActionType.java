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

package com.google.android.libraries.feed.host.logging;

import android.support.annotation.IntDef;

/** IntDef representing the different types of actions. */
@IntDef({
  ActionType.UNKNOWN,
  ActionType.OPEN_URL,
  ActionType.OPEN_URL_INCOGNITO,
  ActionType.OPEN_URL_NEW_TAB,
  ActionType.OPEN_URL_NEW_WINDOW,
  ActionType.DOWNLOAD,
  ActionType.LEARN_MORE
})
// LINT.IfChange
public @interface ActionType {
  int UNKNOWN = -1;
  int OPEN_URL = 1;
  int OPEN_URL_INCOGNITO = 2;
  int OPEN_URL_NEW_TAB = 4;
  int OPEN_URL_NEW_WINDOW = 3;
  int DOWNLOAD = 5;
  int LEARN_MORE = 6;
}
// LINT.ThenChange
