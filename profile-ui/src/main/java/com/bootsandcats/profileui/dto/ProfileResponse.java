package com.bootsandcats.profileui.dto;

import java.time.Instant;

import com.bootsandcats.profileui.model.Address;
import com.bootsandcats.profileui.model.SocialMedia;
import com.bootsandcats.profileui.model.UserProfile;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/** DTO for returning profile data to the client. */
@Introspected
@Serdeable
public class ProfileResponse {

    private String id;
    private String oauthSubject;
    private Long oauthUserId;
    private String firstName;
    private String lastName;
    private String preferredName;
    private String displayName;
    private String email;
    private String phoneNumber;
    private Address address;
    private SocialMedia socialMedia;
    private String bio;
    private String pictureUrl;
    private Instant createdAt;
    private Instant updatedAt;

    public ProfileResponse() {}

    /**
     * Create a ProfileResponse from a UserProfile entity.
     *
     * @param profile the user profile entity
     * @return the response DTO
     */
    public static ProfileResponse fromEntity(UserProfile profile) {
        ProfileResponse response = new ProfileResponse();
        response.setId(profile.getId() != null ? profile.getId().toHexString() : null);
        response.setOauthSubject(profile.getOauthSubject());
        response.setOauthUserId(profile.getOauthUserId());
        response.setFirstName(profile.getFirstName());
        response.setLastName(profile.getLastName());
        response.setPreferredName(profile.getPreferredName());
        response.setDisplayName(profile.getDisplayName());
        response.setEmail(profile.getEmail());
        response.setPhoneNumber(profile.getPhoneNumber());
        response.setAddress(profile.getAddress());
        response.setSocialMedia(profile.getSocialMedia());
        response.setBio(profile.getBio());
        response.setPictureUrl(profile.getPictureUrl());
        response.setCreatedAt(profile.getCreatedAt());
        response.setUpdatedAt(profile.getUpdatedAt());
        return response;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOauthSubject() {
        return oauthSubject;
    }

    public void setOauthSubject(String oauthSubject) {
        this.oauthSubject = oauthSubject;
    }

    public Long getOauthUserId() {
        return oauthUserId;
    }

    public void setOauthUserId(Long oauthUserId) {
        this.oauthUserId = oauthUserId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPreferredName() {
        return preferredName;
    }

    public void setPreferredName(String preferredName) {
        this.preferredName = preferredName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public SocialMedia getSocialMedia() {
        return socialMedia;
    }

    public void setSocialMedia(SocialMedia socialMedia) {
        this.socialMedia = socialMedia;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public void setPictureUrl(String pictureUrl) {
        this.pictureUrl = pictureUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
