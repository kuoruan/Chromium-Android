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

package com.google.android.libraries.feed.feedactionparser;

import com.google.android.libraries.feed.api.actionparser.ActionParser;
import com.google.android.libraries.feed.api.actionparser.ActionParserFactory;
import com.google.android.libraries.feed.api.knowncontent.ContentMetadata;
import com.google.android.libraries.feed.api.protocoladapter.ProtocolAdapter;
import com.google.android.libraries.feed.common.functional.Supplier;
import com.google.android.libraries.feed.feedactionparser.internal.PietFeedActionPayloadRetriever;

/** Default factory for the default {@link ActionParser} implementation. */
public final class FeedActionParserFactory implements ActionParserFactory {

  private final ProtocolAdapter protocolAdapter;
  private final PietFeedActionPayloadRetriever pietFeedActionPayloadRetriever;

  public FeedActionParserFactory(ProtocolAdapter protocolAdapter) {
    this.protocolAdapter = protocolAdapter;
    this.pietFeedActionPayloadRetriever = new PietFeedActionPayloadRetriever();
  }

  @Override
  public ActionParser build(Supplier</*@Nullable*/ ContentMetadata> contentMetadataSupplier) {
    return new FeedActionParser(
        protocolAdapter, pietFeedActionPayloadRetriever, contentMetadataSupplier);
  }
}
