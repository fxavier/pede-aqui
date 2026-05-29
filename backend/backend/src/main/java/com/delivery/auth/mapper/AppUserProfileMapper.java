package com.delivery.auth.mapper;

import com.delivery.auth.dto.MeResponse;
import com.delivery.auth.entity.AppUserProfile;
import org.springframework.stereotype.Component;

/** Converts user profile entities to response DTOs. */
@Component
public class AppUserProfileMapper {
    public MeResponse toMeResponse(AppUserProfile profile) {
        return new MeResponse(
                profile.getId(),
                profile.getTenantId(),
                profile.getKeycloakUserId(),
                profile.getEmail(),
                profile.getDisplayName(),
                profile.getPhone(),
                profile.getRoles(),
                profile.getFullName(),
                profile.getNif(),
                profile.getDateOfBirth(),
                profile.getAddress(),
                profile.getAvatarStorageKey());
    }
}
