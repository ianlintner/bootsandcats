import requests
import sys
import urllib3
from urllib.parse import urlparse, parse_qs

# Disable SSL warnings for self-signed certs or similar issues if needed
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

CHAT_URL = "https://chat.cat-herding.net/"
OAUTH2_LOGIN_URL = "https://oauth2.cat-herding.net/login"
USERNAME = "user"
PASSWORD = "SNPzX6swhA3y4VSdodcFHA7PpNNohYGAmXpVfVZOUBI="

def run_test():
    print(f"Testing Chat App Flow: {CHAT_URL}")
    
    session = requests.Session()
    # Allow redirects but we want to inspect them if needed. 
    # requests handles cookies automatically.
    
    print(f"1. Navigating to {CHAT_URL}...")
    try:
        response = session.get(CHAT_URL, verify=False, allow_redirects=True)
    except requests.exceptions.ConnectionError:
        print(f"❌ Failed to connect to {CHAT_URL}. Is the service running and accessible?")
        sys.exit(1)

    print(f"   Final URL: {response.url}")
    print(f"   Status Code: {response.status_code}")

    # Check if we are already logged in (200 OK on chat app)
    if response.status_code == 200 and "chat.cat-herding.net" in response.url:
        if "login" not in response.url:
            print("✅ Already logged in or no auth required.")
            return

    # If we are at the login page
    if "oauth2.cat-herding.net/login" in response.url:
        print("2. Redirected to Login Page. Attempting to log in...")
        
        # We need to extract the CSRF token if it exists
        # Spring Security usually requires a CSRF token for login
        # It might be in a hidden input field named "_csrf"
        
        csrf_token = None
        if 'name="_csrf"' in response.text:
            import re
            match = re.search(r'name="_csrf" value="([^"]+)"', response.text)
            if match:
                csrf_token = match.group(1)
                print(f"   Found CSRF token: {csrf_token}")
        
        login_data = {
            "username": USERNAME,
            "password": PASSWORD
        }
        if csrf_token:
            login_data["_csrf"] = csrf_token

        print(f"3. Submitting login form to {response.url}...")
        # The login form action is usually the same URL (POST)
        login_response = session.post(response.url, data=login_data, verify=False, allow_redirects=True)
        
        print(f"   Final URL after login: {login_response.url}")
        print(f"   Status Code: {login_response.status_code}")
        
        if login_response.status_code == 200 and "chat.cat-herding.net" in login_response.url:
             print("✅ Login successful! Redirected back to Chat App.")
             
             # Check for cookies
             cookies = session.cookies.get_dict()
             if "_chat_session" in cookies:
                 print("   Found _chat_session cookie.")
             else:
                 print("⚠️  _chat_session cookie NOT found (might be HttpOnly or not set).")
                 
             # Check content
             if "<html" in login_response.text:
                 print("   Page content seems to be HTML.")
                 import re
                 title_match = re.search(r'<title>(.*?)</title>', login_response.text, re.IGNORECASE)
                 if title_match:
                     print(f"   Page Title: {title_match.group(1)}")
                 
                 # Find script tags
                 script_matches = re.findall(r'<script[^>]*src="([^"]+)"', login_response.text)
                 if script_matches:
                     print("   Script tags found:")
                     for src in script_matches:
                         print(f"    - {src}")
                         # Check if script is reachable
                         if src.startswith("/"):
                             script_url = f"https://chat.cat-herding.net{src}"
                         elif src.startswith("http"):
                             script_url = src
                         else:
                             script_url = f"https://chat.cat-herding.net/{src}"
                             
                         print(f"      Checking {script_url}...")
                         try:
                             script_resp = session.get(script_url, verify=False)
                             print(f"      Status: {script_resp.status_code}")
                         except Exception as e:
                             print(f"      Error: {e}")

             else:
                 print("⚠️  Page content does not look like HTML.")
                 
        elif "error" in login_response.url:
            print("❌ Login failed with error parameter in URL.")
            sys.exit(1)
        else:
            print("❌ Login failed or did not redirect back to chat app.")
            print(f"   Current URL: {login_response.url}")
            sys.exit(1)

    else:
        print("❌ Unexpected state. Did not redirect to login page and not on chat app.")
        print(f"   Current URL: {response.url}")
        sys.exit(1)

if __name__ == "__main__":
    run_test()
