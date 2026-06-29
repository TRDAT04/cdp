package vn.vnpost.cdp.ingestion.producer;

import vn.vnpost.cdp.ingestion.dto.ProfileIngestionRequest;
import vn.vnpost.cdp.ingestion.dto.ProfileIngestionResponse;

public interface ProfileIngestionProducer {
    ProfileIngestionResponse send(ProfileIngestionRequest request);
}
