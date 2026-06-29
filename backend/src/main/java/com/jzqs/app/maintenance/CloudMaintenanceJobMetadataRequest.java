package com.jzqs.app.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CloudMaintenanceJobMetadataRequest(
    String cutoff,
    Integer requested,
    List<String> errors,
    List<String> warnings
) {
    public CloudMaintenanceJobMetadataRequest {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
