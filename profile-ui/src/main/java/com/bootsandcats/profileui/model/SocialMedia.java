package com.bootsandcats.profileui.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Social media links and handles for a user profile.
 */
@Introspected
@Serdeable
public class SocialMedia {

    private String twitter;
    private String linkedin;
    private String github;
    private String facebook;
    private String instagram;
    private String website;

    public SocialMedia() {
    }

    public SocialMedia(
            String twitter,
            String linkedin,
            String github,
            String facebook,
            String instagram,
            String website) {
        this.twitter = twitter;
        this.linkedin = linkedin;
        this.github = github;
        this.facebook = facebook;
        this.instagram = instagram;
        this.website = website;
    }

    public String getTwitter() {
        return twitter;
    }

    public void setTwitter(String twitter) {
        this.twitter = twitter;
    }

    public String getLinkedin() {
        return linkedin;
    }

    public void setLinkedin(String linkedin) {
        this.linkedin = linkedin;
    }

    public String getGithub() {
        return github;
    }

    public void setGithub(String github) {
        this.github = github;
    }

    public String getFacebook() {
        return facebook;
    }

    public void setFacebook(String facebook) {
        this.facebook = facebook;
    }

    public String getInstagram() {
        return instagram;
    }

    public void setInstagram(String instagram) {
        this.instagram = instagram;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }
}
