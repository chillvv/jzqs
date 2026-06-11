package com.jzqs.app.order.api;
import java.util.List;
public record OrderNotesResponse(
    List<OrderNoteItemResponse> userNotes,
    List<OrderNoteItemResponse> merchantNotes
) {
}
