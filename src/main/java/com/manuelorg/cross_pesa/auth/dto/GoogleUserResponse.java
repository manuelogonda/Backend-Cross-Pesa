package com.manuelorg.cross_pesa.auth.dto;

import java.util.Map;

public record GoogleUserResponse(Map<String, Object> attributes) {
    public String getEmail() { return (String) attributes.get("email"); }
    public String getFirstName() { return (String) attributes.get("given_name"); }
    public String getLastName() { return (String) attributes.get("family_name"); }
}
