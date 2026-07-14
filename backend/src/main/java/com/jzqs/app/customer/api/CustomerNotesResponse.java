package com.jzqs.app.customer.api;
import java.util.List;
public record CustomerNotesResponse(
    List<CustomerNoteItemResponse> userNotes,
    List<CustomerNoteItemResponse> longTermMerchantNotes,
    List<CustomerNoteItemResponse> timeBoxedMerchantNotes
) {
}
