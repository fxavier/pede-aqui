package com.delivery.common.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Converts Keycloak realm and client roles into Spring Security authorities. */
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        addRealmRoles(jwt, authorities);
        addScopeAuthorities(jwt, authorities);
        return authorities;
    }

    @SuppressWarnings("unchecked")
    private void addRealmRoles(Jwt jwt, List<GrantedAuthority> authorities) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return;
        }
        Object rolesValue = realmAccess.get("roles");
        if (rolesValue instanceof Collection<?> roles) {
            roles.stream()
                    .map(Object::toString)
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
    }

    private void addScopeAuthorities(Jwt jwt, List<GrantedAuthority> authorities) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return;
        }
        for (String value : scope.split(" ")) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
        }
    }
}
