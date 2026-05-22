package com.delivery.auth.entity;

import com.delivery.common.security.MarketplaceRole;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Stores local profile details for a Keycloak-authenticated marketplace user. */
@Entity
@Table(name = "app_user_profiles")
public class AppUserProfile {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String phone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_profile_roles", joinColumns = @JoinColumn(name = "app_user_profile_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<MarketplaceRole> roles = new LinkedHashSet<>();

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUserProfile() {
    }

    public AppUserProfile(UUID id, UUID tenantId, String keycloakUserId, String email, String displayName, Set<MarketplaceRole> roles) {
        this.id = id;
        this.tenantId = tenantId;
        this.keycloakUserId = keycloakUserId;
        this.email = email;
        this.displayName = displayName;
        this.roles = new LinkedHashSet<>(roles);
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getKeycloakUserId() { return keycloakUserId; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPhone() { return phone; }
    public Set<MarketplaceRole> getRoles() { return roles; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
