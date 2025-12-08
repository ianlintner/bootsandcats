package com.bootsandcats.profileui.dto;

import java.util.List;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * DTO for paginated profile list responses.
 */
@Introspected
@Serdeable
public class ProfileListResponse {

    private List<ProfileResponse> profiles;
    private long totalCount;
    private int page;
    private int pageSize;
    private int totalPages;

    public ProfileListResponse() {
    }

    public ProfileListResponse(
            List<ProfileResponse> profiles, long totalCount, int page, int pageSize) {
        this.profiles = profiles;
        this.totalCount = totalCount;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 0;
    }

    public List<ProfileResponse> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<ProfileResponse> profiles) {
        this.profiles = profiles;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
