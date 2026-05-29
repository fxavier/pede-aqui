# Backend: Flyway V012 migration + extended entities & DTOs for Vendor, Courier, AppUserProfile

## Context

Spec: spec:d6648bb2-0f16-4dd6-83a0-f75e9b0603c8/7dec321e-620b-4153-97fd-68510885a5f5

The current data model for Vendors, Couriers, and AppUserProfiles is minimal. This ticket adds the missing fields at the database and Java entity/DTO layer.

## Files to change

| File | Action |
| --- | --- |
| file:pede-aqui/backend/backend/src/main/resources/db/migration/V012__rich_profiles.sql | **Create** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/vendor/entity/Vendor.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/vendor/dto/CreateVendorRequest.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/vendor/dto/UpdateVendorProfileRequest.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/vendor/dto/VendorResponse.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/vendor/service/VendorService.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/vendor/mapper/VendorMapper.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/dispatch/entity/Courier.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/dispatch/dto/CourierResponse.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/dispatch/mapper/DispatchMapper.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/auth/entity/AppUserProfile.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/auth/dto/MeResponse.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/auth/dto/UserProfileRequest.java | **Extend** |
| file:pede-aqui/backend/backend/src/main/java/com/delivery/auth/mapper/AppUserProfileMapper.java | **Extend** |

## Acceptance Criteria

### 1. Flyway migration `V012__rich_profiles.sql`

Uses `ALTER TABLE … ADD COLUMN IF NOT EXISTS` for safety. Adds:

**`vendors`**** table:**

- `owner_name VARCHAR(160)`
- `nif VARCHAR(30)`
- `phone VARCHAR(40)`
- `address VARCHAR(300)`
- `description TEXT`
- `logo_storage_key VARCHAR(300)`

**`couriers`**** table:**

- `full_name VARCHAR(160)`
- `phone VARCHAR(40)`
- `nif VARCHAR(30)`
- `vehicle_type VARCHAR(60)`
- `vehicle_plate VARCHAR(30)`
- `date_of_birth DATE`

**`app_user_profiles`**** table:**

- `full_name VARCHAR(160)`
- `nif VARCHAR(30)`
- `date_of_birth DATE`
- `address VARCHAR(300)`
- `avatar_storage_key VARCHAR(300)`

**New table ****`courier_documents`****:**

```
id UUID PRIMARY KEY
tenant_id UUID NOT NULL REFERENCES tenants(id)
courier_id UUID NOT NULL REFERENCES couriers(id) ON DELETE CASCADE
document_type VARCHAR(80) NOT NULL
storage_key VARCHAR(300) NOT NULL
status VARCHAR(30) NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

With index: `idx_courier_documents_tenant_courier ON courier_documents(tenant_id, courier_id)`

### 2. `Vendor` entity

New nullable fields with getters: `ownerName`, `nif`, `phone`, `address`, `description`, `logoStorageKey`.

Constructor and `updateProfile()` method updated to accept and persist the new fields.

### 3. Vendor DTOs

**`CreateVendorRequest`** — new optional fields: `ownerName`, `nif`, `phone`, `address`, `description`, `logoStorageKey`. Only `name` and `categoryId` remain required (`@NotBlank` / `@NotNull`).

**`UpdateVendorProfileRequest`** — same optional new fields added.

**`VendorResponse`** — exposes all new fields.

**`VendorMapper`** — maps new fields from entity to response.

**`VendorService.create()`** — passes new fields from request to `Vendor` constructor.

### 4. `Courier` entity

New nullable fields with getters: `fullName`, `phone`, `nif`, `vehicleType`, `vehiclePlate`, `dateOfBirth` (`LocalDate`).

Constructor updated to accept these fields (all optional — can be null).

New method `updateProfile(String fullName, String phone, String nif, String vehicleType, String vehiclePlate, LocalDate dateOfBirth)` that sets the fields and updates `updatedAt`.

### 5. Courier DTOs

**`CourierResponse`** — gains: `fullName`, `phone`, `nif`, `vehicleType`, `vehiclePlate`, `rating`.

**`DispatchMapper`** — maps new fields from `Courier` entity to `CourierResponse`.

### 6. `AppUserProfile` entity

New nullable fields with getters: `fullName`, `nif`, `dateOfBirth` (`LocalDate`), `address`, `avatarStorageKey`.

Constructor unchanged (new fields default to null). New setter/update method: `updateExtendedProfile(String fullName, String nif, LocalDate dateOfBirth, String address, String avatarStorageKey)`.

### 7. AppUserProfile DTOs

**`MeResponse`** — gains: `fullName`, `nif`, `dateOfBirth`, `address`, `avatarStorageKey`.

**`UserProfileRequest`** — gains: `fullName`, `nif`, `dateOfBirth` (`LocalDate`), `address`, `avatarStorageKey`. All new fields optional.

**`AppUserProfileMapper`** — maps new fields.

### 8. No breaking changes

All new fields are optional. Existing API callers sending only the original fields continue to work without modification.