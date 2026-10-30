/**
 * Public API of the notification module (LAC-48, W6-C): {@link com.delivery.notification.api.NotificationDispatcher}
 * turns a domain fact ("order accepted", "order rejected") into per-channel notification deliveries. Other modules
 * call it instead of the notification services/repositories.
 */
@NamedInterface("api")
package com.delivery.notification.api;

import org.springframework.modulith.NamedInterface;
