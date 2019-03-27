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

package com.google.android.libraries.feed.sharedstream.logging;

import com.google.android.libraries.feed.host.logging.ContentLoggingData;
import com.google.search.now.feed.client.StreamDataProto.ClientBasicLoggingMetadata;
import com.google.search.now.ui.stream.StreamStructureProto.BasicLoggingMetadata;
import com.google.search.now.ui.stream.StreamStructureProto.RepresentationData;

/** Implementation of {@link ContentLoggingData} to capture content data when logging events. */
public class StreamContentLoggingData implements ContentLoggingData {

  private final int positionInStream;
  private final long publishedTimeSeconds;
  private final long timeContentBecameAvailable;
  private final float score;
  private final String representationUri;

  public StreamContentLoggingData(
      int positionInStream,
      BasicLoggingMetadata basicLoggingMetadata,
      RepresentationData representationData) {
    this.positionInStream = positionInStream;
    this.publishedTimeSeconds = representationData.getPublishedTimeSeconds();
    this.timeContentBecameAvailable =
        basicLoggingMetadata
            .getExtension(ClientBasicLoggingMetadata.clientBasicLoggingMetadata)
            .getAvailabilityTimeSeconds();
    this.score = basicLoggingMetadata.getScore();
    this.representationUri = representationData.getUri();
  }

  @Override
  public int getPositionInStream() {
    return positionInStream;
  }

  @Override
  public long getPublishedTimeSeconds() {
    return publishedTimeSeconds;
  }

  @Override
  public long getTimeContentBecameAvailable() {
    return timeContentBecameAvailable;
  }

  @Override
  public float getScore() {
    return score;
  }

  @Override
  public String getRepresentationUri() {
    return representationUri;
  }

  @Override
  public boolean equals(/*@Nullable*/ Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StreamContentLoggingData)) {
      return false;
    }

    StreamContentLoggingData that = (StreamContentLoggingData) o;

    if (positionInStream != that.positionInStream) {
      return false;
    }
    if (publishedTimeSeconds != that.publishedTimeSeconds) {
      return false;
    }
    if (timeContentBecameAvailable != that.timeContentBecameAvailable) {
      return false;
    }
    if (Float.compare(that.score, score) != 0) {
      return false;
    }
    return representationUri != null
        ? representationUri.equals(that.representationUri)
        : that.representationUri == null;
  }

  @Override
  public int hashCode() {
    int result = positionInStream;
    result = 31 * result + (int) (publishedTimeSeconds ^ (publishedTimeSeconds >>> 32));
    result = 31 * result + (int) (timeContentBecameAvailable ^ (timeContentBecameAvailable >>> 32));
    result = 31 * result + (score != +0.0f ? Float.floatToIntBits(score) : 0);
    result = 31 * result + (representationUri != null ? representationUri.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "StreamContentLoggingData{"
        + "positionInStream="
        + positionInStream
        + ", publishedTimeSeconds="
        + publishedTimeSeconds
        + ", timeContentBecameAvailable="
        + timeContentBecameAvailable
        + ", score="
        + score
        + ", representationUri='"
        + representationUri
        + '\''
        + '}';
  }
}
