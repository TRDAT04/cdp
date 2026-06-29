package vn.vnpost.cdp.ingestion.service;

import vn.vnpost.cdp.ingestion.dto.ProfileIngestionMessage;

public interface ProfileIngestionService {
    void process(ProfileIngestionMessage message);
}
