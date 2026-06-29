package com.jzqs.app.customer.api;

import java.util.List;

public record RemarkSuggestionResponse(
    String scene,
    List<String> items
) {
}
