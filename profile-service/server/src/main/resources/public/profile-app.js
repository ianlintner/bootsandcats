/**
 * Profile Application JavaScript
 * Handles authentication, profile CRUD operations, and admin functionality
 */
(function() {
  'use strict';

  // API endpoints
  const API = {
    login: '/oauth/login/oauth2server',
    logout: '/logout',
    me: '/api/me',
    profile: '/api/profile',
    adminProfiles: '/api/admin/profiles'
  };

  // Application state
  const state = {
    isAuthenticated: false,
    isAdmin: false,
    currentProfile: null,
    adminProfiles: [],
    adminPage: 0,
    adminPageSize: 10,
    adminTotalPages: 0,
    adminSearchQuery: ''
  };

  // DOM Elements
  const elements = {
    authStatus: document.getElementById('auth-status'),
    logoutBtn: document.getElementById('logout-btn'),
    loginSection: document.getElementById('login-section'),
    loginBtn: document.getElementById('login-btn'),
    profileSection: document.getElementById('profile-section'),
    alertContainer: document.getElementById('alert-container'),
    
    // Tabs
    tabContainer: document.getElementById('tab-container'),
    adminTabBtn: document.getElementById('admin-tab-btn'),
    myProfileTab: document.getElementById('my-profile-tab'),
    adminTab: document.getElementById('admin-tab'),
    
    // Profile states
    noProfileState: document.getElementById('no-profile-state'),
    profileViewState: document.getElementById('profile-view-state'),
    profileEditState: document.getElementById('profile-edit-state'),
    
    // Profile view elements
    profileAvatar: document.getElementById('profile-avatar'),
    profileDisplayName: document.getElementById('profile-display-name'),
    profileEmail: document.getElementById('profile-email'),
    viewFirstName: document.getElementById('view-firstName'),
    viewLastName: document.getElementById('view-lastName'),
    viewPreferredName: document.getElementById('view-preferredName'),
    viewPhoneNumber: document.getElementById('view-phoneNumber'),
    viewAddress: document.getElementById('view-address'),
    viewBio: document.getElementById('view-bio'),
    viewSocialMedia: document.getElementById('view-socialMedia'),
    
    // Profile edit elements
    createProfileBtn: document.getElementById('create-profile-btn'),
    editProfileBtn: document.getElementById('edit-profile-btn'),
    cancelEditBtn: document.getElementById('cancel-edit-btn'),
    deleteProfileBtn: document.getElementById('delete-profile-btn'),
    profileForm: document.getElementById('profile-form'),
    formTitle: document.getElementById('form-title'),
    
    // Admin elements
    adminSearch: document.getElementById('admin-search'),
    adminSearchBtn: document.getElementById('admin-search-btn'),
    adminLoading: document.getElementById('admin-loading'),
    adminProfilesContainer: document.getElementById('admin-profiles-container'),
    adminProfilesList: document.getElementById('admin-profiles-list'),
    adminPaginationInfo: document.getElementById('admin-pagination-info'),
    adminPrevBtn: document.getElementById('admin-prev-btn'),
    adminNextBtn: document.getElementById('admin-next-btn'),
    adminEditModal: document.getElementById('admin-edit-modal'),
    closeAdminModal: document.getElementById('close-admin-modal'),
    adminProfileForm: document.getElementById('admin-profile-form'),
    adminEditId: document.getElementById('admin-edit-id'),
    adminDeleteBtn: document.getElementById('admin-delete-btn')
  };

  // Utility functions
  function showAlert(message, type = 'error') {
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type}`;
    alertDiv.textContent = message;
    elements.alertContainer.innerHTML = '';
    elements.alertContainer.appendChild(alertDiv);
    setTimeout(() => alertDiv.remove(), 5000);
  }

  function hideAllProfileStates() {
    elements.noProfileState.classList.add('hidden');
    elements.profileViewState.classList.add('hidden');
    elements.profileEditState.classList.add('hidden');
  }

  function getInitials(firstName, lastName) {
    const first = firstName ? firstName.charAt(0).toUpperCase() : '';
    const last = lastName ? lastName.charAt(0).toUpperCase() : '';
    return first + last || '?';
  }

  function formatDate(dateString) {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  // API functions
  async function fetchWithAuth(url, options = {}) {
    const response = await fetch(url, {
      ...options,
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...options.headers
      }
    });
    return response;
  }

  async function checkAuthentication() {
    try {
      const response = await fetchWithAuth(API.me);
      
      if (response.status === 401 || response.status === 403) {
        state.isAuthenticated = false;
        showLoginSection();
        return;
      }
      
      if (!response.ok) {
        // Server error - log but treat as unauthenticated
        console.warn(`Authentication endpoint returned status ${response.status}. Treating as unauthenticated.`);
        state.isAuthenticated = false;
        showLoginSection();
        return;
      }
      
      const data = await response.json();
      state.isAuthenticated = true;
      
      // Check for admin scope in attributes
      const scopes = data.attributes?.scope || [];
      state.isAdmin = Array.isArray(scopes) 
        ? scopes.includes('profile:admin')
        : String(scopes).includes('profile:admin');
      
      showAuthenticatedState(data);
      loadProfile();
    } catch (error) {
      console.error('Authentication check failed:', error);
      state.isAuthenticated = false;
      showLoginSection();
    }
  }

  function showLoginSection() {
    elements.authStatus.innerHTML = '<span class="text-red-400">Not signed in</span>';
    elements.logoutBtn.classList.add('hidden');
    elements.loginSection.classList.remove('hidden');
    elements.profileSection.classList.add('hidden');
  }

  function showAuthenticatedState(userData) {
    elements.authStatus.innerHTML = `
      <span class="h-2 w-2 rounded-full bg-emerald-300"></span>
      Signed in as ${userData.name || 'User'}
    `;
    elements.authStatus.classList.remove('bg-slate-800', 'text-slate-400');
    elements.authStatus.classList.add('bg-emerald-500/10', 'text-emerald-200', 'ring-1', 'ring-emerald-500/40');
    
    elements.logoutBtn.classList.remove('hidden');
    elements.loginSection.classList.add('hidden');
    elements.profileSection.classList.remove('hidden');
    
    // Show admin tab if user has admin scope
    if (state.isAdmin) {
      elements.adminTabBtn.classList.remove('hidden');
    }
  }

  async function loadProfile() {
    try {
      const response = await fetchWithAuth(API.profile);
      
      if (response.status === 404) {
        // No profile found
        state.currentProfile = null;
        hideAllProfileStates();
        elements.noProfileState.classList.remove('hidden');
        return;
      }
      
      if (!response.ok) {
        throw new Error('Failed to load profile');
      }
      
      state.currentProfile = await response.json();
      displayProfile();
    } catch (error) {
      console.error('Failed to load profile:', error);
      showAlert('Failed to load profile. Please try again.');
    }
  }

  function displayProfile() {
    const profile = state.currentProfile;
    if (!profile) return;
    
    hideAllProfileStates();
    elements.profileViewState.classList.remove('hidden');
    
    // Avatar and header
    elements.profileAvatar.textContent = getInitials(profile.firstName, profile.lastName);
    elements.profileDisplayName.textContent = profile.preferredName || `${profile.firstName} ${profile.lastName}`;
    elements.profileEmail.textContent = profile.email || '-';
    
    // Personal info
    elements.viewFirstName.textContent = profile.firstName || '-';
    elements.viewLastName.textContent = profile.lastName || '-';
    elements.viewPreferredName.textContent = profile.preferredName || '-';
    elements.viewPhoneNumber.textContent = profile.phoneNumber || '-';
    
    // Address
    if (profile.address) {
      const addr = profile.address;
      const parts = [addr.street, addr.city, addr.state, addr.postalCode, addr.country].filter(Boolean);
      elements.viewAddress.innerHTML = parts.length > 0 
        ? parts.join('<br>') 
        : '-';
    } else {
      elements.viewAddress.textContent = '-';
    }
    
    // Bio
    elements.viewBio.textContent = profile.bio || '-';
    
    // Social media
    if (profile.socialMedia) {
      const social = profile.socialMedia;
      const links = [];
      
      if (social.twitter) {
        links.push(`<a href="https://twitter.com/${social.twitter.replace('@', '')}" target="_blank" class="inline-flex items-center gap-1 rounded-lg bg-white/5 px-3 py-1 text-sm text-primary-200 hover:bg-white/10">Twitter: ${social.twitter}</a>`);
      }
      if (social.linkedin) {
        links.push(`<a href="${social.linkedin}" target="_blank" class="inline-flex items-center gap-1 rounded-lg bg-white/5 px-3 py-1 text-sm text-primary-200 hover:bg-white/10">LinkedIn</a>`);
      }
      if (social.github) {
        links.push(`<a href="https://github.com/${social.github}" target="_blank" class="inline-flex items-center gap-1 rounded-lg bg-white/5 px-3 py-1 text-sm text-primary-200 hover:bg-white/10">GitHub: ${social.github}</a>`);
      }
      if (social.website) {
        links.push(`<a href="${social.website}" target="_blank" class="inline-flex items-center gap-1 rounded-lg bg-white/5 px-3 py-1 text-sm text-primary-200 hover:bg-white/10">Website</a>`);
      }
      
      elements.viewSocialMedia.innerHTML = links.length > 0 ? links.join('') : '-';
    } else {
      elements.viewSocialMedia.textContent = '-';
    }
  }

  function showEditForm(isNew = false) {
    hideAllProfileStates();
    elements.profileEditState.classList.remove('hidden');
    elements.formTitle.textContent = isNew ? 'Create Profile' : 'Edit Profile';
    elements.deleteProfileBtn.classList.toggle('hidden', isNew);
    
    // Populate form
    if (state.currentProfile && !isNew) {
      const p = state.currentProfile;
      document.getElementById('firstName').value = p.firstName || '';
      document.getElementById('lastName').value = p.lastName || '';
      document.getElementById('preferredName').value = p.preferredName || '';
      document.getElementById('email').value = p.email || '';
      document.getElementById('phoneNumber').value = p.phoneNumber || '';
      document.getElementById('pictureUrl').value = p.pictureUrl || '';
      document.getElementById('bio').value = p.bio || '';
      
      if (p.address) {
        document.getElementById('street').value = p.address.street || '';
        document.getElementById('city').value = p.address.city || '';
        document.getElementById('state').value = p.address.state || '';
        document.getElementById('postalCode').value = p.address.postalCode || '';
        document.getElementById('country').value = p.address.country || '';
      }
      
      if (p.socialMedia) {
        document.getElementById('twitter').value = p.socialMedia.twitter || '';
        document.getElementById('linkedin').value = p.socialMedia.linkedin || '';
        document.getElementById('github').value = p.socialMedia.github || '';
        document.getElementById('website').value = p.socialMedia.website || '';
      }
    } else {
      elements.profileForm.reset();
    }
  }

  function getFormData() {
    return {
      firstName: document.getElementById('firstName').value,
      lastName: document.getElementById('lastName').value,
      preferredName: document.getElementById('preferredName').value || null,
      email: document.getElementById('email').value,
      phoneNumber: document.getElementById('phoneNumber').value || null,
      pictureUrl: document.getElementById('pictureUrl').value || null,
      bio: document.getElementById('bio').value || null,
      address: {
        street: document.getElementById('street').value || null,
        city: document.getElementById('city').value || null,
        state: document.getElementById('state').value || null,
        postalCode: document.getElementById('postalCode').value || null,
        country: document.getElementById('country').value || null
      },
      socialMedia: {
        twitter: document.getElementById('twitter').value || null,
        linkedin: document.getElementById('linkedin').value || null,
        github: document.getElementById('github').value || null,
        website: document.getElementById('website').value || null
      }
    };
  }

  async function saveProfile(event) {
    event.preventDefault();
    
    const formData = getFormData();
    const isNew = !state.currentProfile;
    
    try {
      const response = await fetchWithAuth(API.profile, {
        method: isNew ? 'POST' : 'PUT',
        body: JSON.stringify(formData)
      });
      
      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.message || 'Failed to save profile');
      }
      
      state.currentProfile = await response.json();
      showAlert(isNew ? 'Profile created successfully!' : 'Profile updated successfully!', 'success');
      displayProfile();
    } catch (error) {
      console.error('Failed to save profile:', error);
      showAlert(error.message || 'Failed to save profile. Please try again.');
    }
  }

  async function deleteProfile() {
    if (!confirm('Are you sure you want to delete your profile? This action cannot be undone.')) {
      return;
    }
    
    try {
      const response = await fetchWithAuth(API.profile, { method: 'DELETE' });
      
      if (!response.ok) {
        throw new Error('Failed to delete profile');
      }
      
      state.currentProfile = null;
      showAlert('Profile deleted successfully.', 'success');
      hideAllProfileStates();
      elements.noProfileState.classList.remove('hidden');
    } catch (error) {
      console.error('Failed to delete profile:', error);
      showAlert('Failed to delete profile. Please try again.');
    }
  }

  // Tab functionality
  function switchTab(tabName) {
    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.tab === tabName);
    });
    
    document.querySelectorAll('.tab-content').forEach(content => {
      content.classList.remove('active');
    });
    
    if (tabName === 'my-profile') {
      elements.myProfileTab.classList.add('active');
    } else if (tabName === 'admin') {
      elements.adminTab.classList.add('active');
      loadAdminProfiles();
    }
  }

  // Admin functionality
  async function loadAdminProfiles() {
    elements.adminLoading.classList.remove('hidden');
    elements.adminProfilesContainer.classList.add('hidden');
    
    try {
      let url = `${API.adminProfiles}?page=${state.adminPage}&size=${state.adminPageSize}`;
      if (state.adminSearchQuery) {
        url += `&search=${encodeURIComponent(state.adminSearchQuery)}`;
      }
      
      const response = await fetchWithAuth(url);
      
      if (response.status === 403) {
        showAlert('You do not have permission to access admin features.');
        return;
      }
      
      if (!response.ok) {
        throw new Error('Failed to load profiles');
      }
      
      const data = await response.json();
      state.adminProfiles = data.profiles || [];
      state.adminTotalPages = data.totalPages || 0;
      
      renderAdminProfiles(data);
    } catch (error) {
      console.error('Failed to load admin profiles:', error);
      showAlert('Failed to load profiles. Please try again.');
    } finally {
      elements.adminLoading.classList.add('hidden');
      elements.adminProfilesContainer.classList.remove('hidden');
    }
  }

  function renderAdminProfiles(data) {
    const tbody = elements.adminProfilesList;
    tbody.innerHTML = '';
    
    if (!data.profiles || data.profiles.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="4" class="text-center text-slate-400 py-8">
            No profiles found
          </td>
        </tr>
      `;
    } else {
      data.profiles.forEach(profile => {
        const row = document.createElement('tr');
        row.innerHTML = `
          <td class="text-white">
            ${profile.preferredName || `${profile.firstName} ${profile.lastName}`}
          </td>
          <td class="text-slate-300">${profile.email || '-'}</td>
          <td class="text-slate-400">${formatDate(profile.createdAt)}</td>
          <td>
            <button class="btn-secondary text-sm py-1 px-3" data-action="edit" data-id="${profile.id}">
              Edit
            </button>
          </td>
        `;
        tbody.appendChild(row);
      });
    }
    
    // Update pagination
    const start = state.adminPage * state.adminPageSize + 1;
    const end = Math.min(start + state.adminPageSize - 1, data.totalCount || 0);
    elements.adminPaginationInfo.textContent = `Showing ${data.totalCount ? `${start}-${end} of ${data.totalCount}` : '0'} profiles`;
    
    elements.adminPrevBtn.disabled = state.adminPage === 0;
    elements.adminNextBtn.disabled = state.adminPage >= state.adminTotalPages - 1;
  }

  async function openAdminEditModal(profileId) {
    try {
      const response = await fetchWithAuth(`${API.adminProfiles}/${profileId}`);
      
      if (!response.ok) {
        throw new Error('Failed to load profile');
      }
      
      const profile = await response.json();
      
      elements.adminEditId.value = profile.id;
      document.getElementById('admin-firstName').value = profile.firstName || '';
      document.getElementById('admin-lastName').value = profile.lastName || '';
      document.getElementById('admin-preferredName').value = profile.preferredName || '';
      document.getElementById('admin-email').value = profile.email || '';
      document.getElementById('admin-phoneNumber').value = profile.phoneNumber || '';
      document.getElementById('admin-pictureUrl').value = profile.pictureUrl || '';
      document.getElementById('admin-bio').value = profile.bio || '';
      
      elements.adminEditModal.classList.remove('hidden');
    } catch (error) {
      console.error('Failed to load profile:', error);
      showAlert('Failed to load profile. Please try again.');
    }
  }

  function closeAdminEditModal() {
    elements.adminEditModal.classList.add('hidden');
    elements.adminProfileForm.reset();
  }

  async function saveAdminProfile(event) {
    event.preventDefault();
    
    const profileId = elements.adminEditId.value;
    const formData = {
      firstName: document.getElementById('admin-firstName').value,
      lastName: document.getElementById('admin-lastName').value,
      preferredName: document.getElementById('admin-preferredName').value || null,
      email: document.getElementById('admin-email').value,
      phoneNumber: document.getElementById('admin-phoneNumber').value || null,
      pictureUrl: document.getElementById('admin-pictureUrl').value || null,
      bio: document.getElementById('admin-bio').value || null
    };
    
    try {
      const response = await fetchWithAuth(`${API.adminProfiles}/${profileId}`, {
        method: 'PUT',
        body: JSON.stringify(formData)
      });
      
      if (!response.ok) {
        throw new Error('Failed to save profile');
      }
      
      showAlert('Profile updated successfully!', 'success');
      closeAdminEditModal();
      loadAdminProfiles();
    } catch (error) {
      console.error('Failed to save profile:', error);
      showAlert('Failed to save profile. Please try again.');
    }
  }

  async function deleteAdminProfile() {
    const profileId = elements.adminEditId.value;
    
    if (!confirm('Are you sure you want to delete this profile? This action cannot be undone.')) {
      return;
    }
    
    try {
      const response = await fetchWithAuth(`${API.adminProfiles}/${profileId}`, {
        method: 'DELETE'
      });
      
      if (!response.ok) {
        throw new Error('Failed to delete profile');
      }
      
      showAlert('Profile deleted successfully.', 'success');
      closeAdminEditModal();
      loadAdminProfiles();
    } catch (error) {
      console.error('Failed to delete profile:', error);
      showAlert('Failed to delete profile. Please try again.');
    }
  }

  // Event listeners
  function initEventListeners() {
    // Authentication
    elements.loginBtn.addEventListener('click', () => {
      window.location.href = API.login;
    });
    
    elements.logoutBtn.addEventListener('click', () => {
      window.location.href = API.logout;
    });
    
    // Tabs
    elements.tabContainer.addEventListener('click', (e) => {
      const tabBtn = e.target.closest('.tab-btn');
      if (tabBtn) {
        switchTab(tabBtn.dataset.tab);
      }
    });
    
    // Profile actions
    elements.createProfileBtn.addEventListener('click', () => showEditForm(true));
    elements.editProfileBtn.addEventListener('click', () => showEditForm(false));
    elements.cancelEditBtn.addEventListener('click', () => {
      if (state.currentProfile) {
        displayProfile();
      } else {
        hideAllProfileStates();
        elements.noProfileState.classList.remove('hidden');
      }
    });
    elements.deleteProfileBtn.addEventListener('click', deleteProfile);
    elements.profileForm.addEventListener('submit', saveProfile);
    
    // Admin actions
    elements.adminSearchBtn.addEventListener('click', () => {
      state.adminSearchQuery = elements.adminSearch.value;
      state.adminPage = 0;
      loadAdminProfiles();
    });
    
    elements.adminSearch.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') {
        state.adminSearchQuery = elements.adminSearch.value;
        state.adminPage = 0;
        loadAdminProfiles();
      }
    });
    
    elements.adminPrevBtn.addEventListener('click', () => {
      if (state.adminPage > 0) {
        state.adminPage--;
        loadAdminProfiles();
      }
    });
    
    elements.adminNextBtn.addEventListener('click', () => {
      if (state.adminPage < state.adminTotalPages - 1) {
        state.adminPage++;
        loadAdminProfiles();
      }
    });
    
    elements.adminProfilesList.addEventListener('click', (e) => {
      const editBtn = e.target.closest('[data-action="edit"]');
      if (editBtn) {
        openAdminEditModal(editBtn.dataset.id);
      }
    });
    
    elements.closeAdminModal.addEventListener('click', closeAdminEditModal);
    elements.adminEditModal.addEventListener('click', (e) => {
      if (e.target === elements.adminEditModal) {
        closeAdminEditModal();
      }
    });
    
    elements.adminProfileForm.addEventListener('submit', saveAdminProfile);
    elements.adminDeleteBtn.addEventListener('click', deleteAdminProfile);
  }

  // Initialize
  function init() {
    initEventListeners();
    checkAuthentication();
  }

  // Start the app when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
