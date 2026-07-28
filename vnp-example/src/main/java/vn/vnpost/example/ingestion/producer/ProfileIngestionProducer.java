package vn.vnpost.example.ingestion.producer;

import vn.vnpost.example.ingestion.dto.ProfileIngestionRequest;
import vn.vnpost.example.ingestion.dto.ProfileIngestionResponse;

public interface ProfileIngestionProducer {
    ProfileIngestionResponse send(ProfileIngestionRequest request);
}
