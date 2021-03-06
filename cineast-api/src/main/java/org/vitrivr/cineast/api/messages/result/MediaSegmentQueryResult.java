package org.vitrivr.cineast.api.messages.result;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.vitrivr.cineast.api.messages.abstracts.AbstractQueryResultMessage;
import org.vitrivr.cineast.api.messages.interfaces.MessageType;
import org.vitrivr.cineast.core.data.entities.MediaSegmentDescriptor;

import java.util.List;

/**
 * @author rgasser
 * @created 12.01.17
 */
public class MediaSegmentQueryResult extends AbstractQueryResultMessage<MediaSegmentDescriptor> {
  /**
   * @param content
   */
  @JsonCreator
  public MediaSegmentQueryResult(String queryId, List<MediaSegmentDescriptor> content) {
    super(queryId, MediaSegmentDescriptor.class, content);
  }
  
  @Override
  public MessageType getMessageType() {
    return MessageType.QR_SEGMENT;
  }
  
  
}
