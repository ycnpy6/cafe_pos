package com.cafepos.model;

public record PrintQueueItem(long id, int orderId, String ticketType, String payload, int attempts) {
}
