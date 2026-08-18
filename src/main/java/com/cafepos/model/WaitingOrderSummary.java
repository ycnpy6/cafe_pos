package com.cafepos.model;

public record WaitingOrderSummary(int id,
                                  String customerName,
                                  double total,
                                  int lineCount,
                                  String createdAt,
                                  String itemsSummary) {
}
