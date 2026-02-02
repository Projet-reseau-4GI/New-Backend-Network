# Frontend Network Stability & Error Handling Guide

This guide describes how to implement network stability checks and handle the new detailed error responses from the backend.

## 1. Network Stability Check

Before calling the `/upload-analyze` endpoint, check if the user is online and has a stable connection.

### `utils/networkCheck.ts` (Create this file)

```typescript
export const checkNetworkStability = (): boolean => {
  if (typeof navigator !== 'undefined' && 'onLine' in navigator) {
    if (!navigator.onLine) {
      return false;
    }
  }
  // Optional: Check connection type if available (NavigatorNetworkInformation API)
  // Note: This API is not supported in all browsers (mostly Chrome/Android)
  const nav = navigator as any;
  if (nav.connection) {
    // Avoid uploading if on 2g or slow-2g
    if (nav.connection.effectiveType === '2g' || nav.connection.effectiveType === 'slow-2g') {
        console.warn("Connection too slow for upload");
        // You might want to allow it with a warning, or return false to block
        // return false; 
    }
  }
  return true;
};
```

### Usage in Component

```typescript
import { checkNetworkStability } from '../utils/networkCheck';

const handleUpload = async () => {
    if (!checkNetworkStability()) {
        alert("No internet connection or connection is too unstable. Please check your network.");
        return;
    }

    // Proceed with upload...
};
```

## 2. Handling New Error Response

The backend now returns structured JSON errors even for 4xx/5xx responses.

### Error Structure
```json
{
  "timestamp": "2023-10-27T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "User not found with ID: 12345",
  "path": "/api/documents/upload-analyze"
}
```

### Fetch / Axios Implementation

**Using `fetch`:**

```typescript
try {
  const response = await fetch('/api/documents/upload-analyze', {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    const errorData = await response.json();
    throw new Error(errorData.message || 'Unknown server error');
  }

  const result = await response.json();
} catch (error) {
  // Now 'error.message' contains the specific backend message (e.g., "User not found...")
  alert(`Error: ${error.message}`);
}
```

**Using `axios`:**

```typescript
try {
  const response = await axios.post('/api/documents/upload-analyze', formData);
} catch (error) {
  if (error.response && error.response.data) {
    const backendError = error.response.data;
    alert(`Upload Failed: ${backendError.message}`);
  } else {
    alert("Network error or server unreachable");
  }
}
```

## 3. Client-Side Retry Logic (Exponential Backoff)

To handle intermittent network failures, implement a retry mechanism with exponential backoff.

### `utils/fetchWithRetry.ts`

```typescript
const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

export const fetchWithRetry = async (
  url: string,
  options: RequestInit,
  retries: number = 3,
  backoff: number = 1000
): Promise<Response> => {
  try {
    const response = await fetch(url, options);
    
    // Retry on 5xx server errors or if specifically configured
    if (!response.ok && response.status >= 500) {
      throw new Error(`Server Error: ${response.status}`);
    }
    
    return response;
  } catch (error) {
    if (retries > 0) {
      console.warn(`Retrying... (${retries} left). Waiting ${backoff}ms`);
      await wait(backoff);
      return fetchWithRetry(url, options, retries - 1, backoff * 2);
    }
    throw error;
  }
};
```

### Usage

```typescript
import { fetchWithRetry } from '../utils/fetchWithRetry';

try {
    const response = await fetchWithRetry('/api/documents/upload-analyze', {
        method: 'POST',
        body: formData
    });
    // handle response
} catch (error) {
    alert("Upload failed after multiple attempts. Please check your connection.");
}
```
