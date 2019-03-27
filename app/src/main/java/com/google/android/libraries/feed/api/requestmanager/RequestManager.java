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

package com.google.android.libraries.feed.api.requestmanager;

import com.google.android.libraries.feed.common.Result;
import com.google.android.libraries.feed.common.functional.Consumer;
import com.google.android.libraries.feed.common.functional.Supplier;
import com.google.search.now.feed.client.StreamDataProto.StreamDataOperation;
import com.google.search.now.feed.client.StreamDataProto.StreamToken;
import com.google.search.now.wire.feed.FeedQueryProto.FeedQuery.RequestReason;
import java.util.List;

/** Creates and issues requests to the server. */
public interface RequestManager {

  /**
   * Issues a request for the next page of data. The {@code streamToken} described to the server
   * what the next page means. The response will be sent to a {@link Consumer} a set of {@link
   * StreamDataOperation} created by the ProtocolAdapter.
   */
  void loadMore(StreamToken streamToken, Consumer<Result<List<StreamDataOperation>>> consumer);

  /**
   * Issues a request to refresh the entire feed, with the consumer being called back with the
   * resulting {@link StreamDataOperation}s.
   */
  void triggerRefresh(RequestReason reason, Consumer<Result<List<StreamDataOperation>>> consumer);

  /**
   * Issues a request to refresh the entire feed.
   *
   * @throws IllegalStateException if {@link #setDefaultTriggerRefreshConsumerSupplier} has not been
   *     called.
   */
  void triggerRefresh(RequestReason reason);

  /**
   * Sets a supplier that will generate a consumer to be used for the {@link
   * #triggerRefresh(RequestReason)} method call
   */
  void setDefaultTriggerRefreshConsumerSupplier(
      Supplier<Consumer<Result<List<StreamDataOperation>>>> consumer);
}
