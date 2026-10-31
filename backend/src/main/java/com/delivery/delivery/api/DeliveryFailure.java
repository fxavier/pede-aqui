package com.delivery.delivery.api;

import java.util.UUID;

/**
 * What delivery hands to the order module when a delivery ends in FAILED_DELIVERY (LAC-44, W7-B), so the order side can
 * open the delivery incident in the same transaction.
 *
 * @param fromStatus           the delivery status the failure was reported from (name of {@code DeliveryStatus})
 * @param note                 optional free text of the reporter (at most 500 chars) — stored on the incident only,
 *                             never logged or audited
 * @param proofPhotoStorageKey optional proof photo, already validated by {@code StoredUploadValidator}
 * @param reportedBy           JWT subject of the reporter
 * @param reportedByRole       COURIER, OPS or ADMIN
 * @param evidence             the courier's last known position at report time, null when none was received
 */
public record DeliveryFailure(UUID deliveryId, UUID courierId, String fromStatus, DeliveryFailureReason reason, String note,
                              String proofPhotoStorageKey, String reportedBy, String reportedByRole, CourierPosition evidence) {
}
