package com.delivery.rating.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds app.rating.*: how long after delivery an order can be rated and how long a rating stays editable (LAC-28). */
@Component
@ConfigurationProperties(prefix = "app.rating")
public class RatingProperties {
    /** Days after the DELIVERED transition during which the customer can submit a rating. */
    private int windowDays = 7;
    /** Hours after submission during which the customer can edit the rating. */
    private int editWindowHours = 24;

    public int getWindowDays() { return windowDays; }
    public void setWindowDays(int windowDays) { this.windowDays = windowDays; }
    public int getEditWindowHours() { return editWindowHours; }
    public void setEditWindowHours(int editWindowHours) { this.editWindowHours = editWindowHours; }
}
