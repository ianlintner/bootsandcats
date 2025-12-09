// Home Page JavaScript

// JSON Syntax Highlighting
function highlightJson(json) {
    if (typeof json !== 'string') {
        json = JSON.stringify(json, null, 2);
    }
    // Try to parse and pretty-print
    try {
        var parsed = JSON.parse(json);
        json = JSON.stringify(parsed, null, 2);
    } catch (e) {
        // Already formatted or invalid
    }
    
    return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
        var cls = 'json-number';
        if (/^"/.test(match)) {
            if (/:$/.test(match)) {
                cls = 'json-key';
                match = match.replace(/"/g, '').replace(':', '');
                return '<span class="' + cls + '">"' + match + '"</span>:';
            } else {
                cls = 'json-string';
            }
        } else if (/true|false/.test(match)) {
            cls = 'json-boolean';
        } else if (/null/.test(match)) {
            cls = 'json-null';
        }
        return '<span class="' + cls + '">' + match + '</span>';
    }).replace(/[{}\[\]]/g, '<span class="json-bracket">$&</span>');
}

// Initialize JSON displays
document.addEventListener('DOMContentLoaded', function() {
    // Claims JSON
    var claimsEl = document.getElementById('claims-json');
    if (claimsEl && claimsEl.dataset.json) {
        claimsEl.innerHTML = highlightJson(claimsEl.dataset.json);
    }
    
    // Decoded JWT payload
    var decodedEl = document.getElementById('id-token-decoded');
    if (decodedEl && decodedEl.dataset.json) {
        decodedEl.innerHTML = highlightJson(decodedEl.dataset.json);
    }

    // JWT parts display
    var jwtPartsEl = document.getElementById('jwt-parts');
    if (jwtPartsEl && jwtPartsEl.dataset.jwt) {
        var jwt = jwtPartsEl.dataset.jwt;
        var parts = jwt.split('.');
        if (parts.length === 3) {
            jwtPartsEl.innerHTML = 
                '<div class="jwt-part jwt-header">' +
                    '<div class="jwt-part-label">Header</div>' +
                    parts[0] +
                '</div>' +
                '<div class="jwt-part jwt-payload">' +
                    '<div class="jwt-part-label">Payload</div>' +
                    parts[1] +
                '</div>' +
                '<div class="jwt-part jwt-signature">' +
                    '<div class="jwt-part-label">Signature</div>' +
                    parts[2] +
                '</div>';
        }
    }
});

// Copy to clipboard with feedback
function copyToClipboard(elementId, button) {
    var element = document.getElementById(elementId);
    var text = element.textContent || element.innerText;
    
    // For JSON blocks, get the raw data
    if (element.dataset.json) {
        try {
            text = JSON.stringify(JSON.parse(element.dataset.json), null, 2);
        } catch (e) {
            text = element.dataset.json;
        }
    }
    
    navigator.clipboard.writeText(text).then(function() {
        var originalHTML = button.innerHTML;
        button.classList.add('copied');
        button.innerHTML = 
            '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                '<polyline points="20 6 9 17 4 12"/>' +
            '</svg>' +
            'Copied!';
        setTimeout(function() {
            button.classList.remove('copied');
            button.innerHTML = originalHTML;
        }, 2000);
    }).catch(function(err) {
        console.error('Failed to copy:', err);
    });
}
