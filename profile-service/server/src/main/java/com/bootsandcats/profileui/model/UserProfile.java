package com.bootsandcats.profileui.model;

import java.time.Instant;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * User profile document stored in MongoDB/CosmosDB.
 *
 * <p>This document contains extended profile information for users authenticated via the OAuth2
 * server. The profile is linked to the OAuth2 identity via the {@code oauthSubject} field.
 */
@Introspected
@Serdeable
public class UserProfile {

    @BsonId private ObjectId id;

    /**
     * The OAuth2 subject identifier (sub claim from JWT). This is the primary link to the OAuth2
     * identity.
     */
    @BsonProperty("oauth_subject")
    private String oauthSubject;

    /** The OAuth2 user ID from the authorization server. */
    @BsonProperty("oauth_user_id")
    private Long oauthUserId;

    /** User's first name. */
    @BsonProperty("first_name")
    private String firstName;

    /** User's last name. */
    @BsonProperty("last_name")
    private String lastName;

    /** User's preferred display name. */
    @BsonProperty("preferred_name")
    private String preferredName;

    /** User's email address. */
    private String email;

    /** User's phone number. */
    @BsonProperty("phone_number")
    private String phoneNumber;

    /** User's mailing address. */
    private Address address;

    /** User's social media links and handles. */
    @BsonProperty("social_media")
    private SocialMedia socialMedia;

    /** User's bio or description. */
    private String bio;

    /** URL to user's profile picture. */
    @BsonProperty("picture_url")
    private String pictureUrl;

    /** Timestamp when the profile was created. */
    @BsonProperty("created_at")
    private Instant createdAt;

    /** Timestamp when the profile was last updated. */
    @BsonProperty("updated_at")
    private Instant updatedAt;

    public UserProfile() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
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

    /**
     * Returns the display name to show in the UI.
     *
     * @return the preferred name if set, otherwise first name + last name, or "Anonymous"
     */
    public String getDisplayName() {
        if (preferredName != null && !preferredName.isBlank()) {
            return preferredName;
        }
        if (firstName != null || lastName != null) {
            StringBuilder sb = new StringBuilder();
            if (firstName != null) {
                sb.append(firstName);
            }
            if (lastName != null) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(lastName);
            }
            return sb.toString();
        }
        return "Anonymous";
    }
}
