# AgroVision Admin Dashboard

Minimal monitoring system for AgroVision kiosks.

## Setup Instructions

1.  Create a new React project: `npx create-react-app agrovision-admin`
2.  Install Firebase: `npm install firebase`
3.  Add your Firebase configuration.
4.  Implement components using the logic below.

## Firestore Collections

### 1. `shops/`
Contains onboarding details.
- Document ID: `phoneNumber`
- Fields: `ownerName`, `shopName`, `phoneNumber`, `onboardingDate`, `status`, `appVersion`.

### 2. `kiosks/`
Contains real-time heartbeat data.
- Document ID: `phoneNumber`
- Fields: `shopName`, `lastActiveTimestamp`, `appVersion`, `status`, `deviceId`.

### 3. `daily_scans/`
Daily usage metrics.
- Document ID: `{phoneNumber}_{YYYY-MM-DD}`
- Fields: `scanCount`, `lastUpdated`, `shopId`, `date`.

### 4. `ad_impressions/`
Advertisement metrics.
- Document ID: `{phoneNumber}_{YYYY-MM-DD}`
- Fields: `[adId]`, `lastUpdated`, `shopId`, `date`.

## Online/Offline Logic

```javascript
const isOnline = (lastActiveTimestamp) => {
  const tenMinutesAgo = Date.now() - (10 * 60 * 1000);
  return lastActiveTimestamp > tenMinutesAgo;
};
```

## Security Rules (Firestore)

```firebase
service cloud.firestore {
  match /databases/{database}/documents {
    // Admin access only for dashboard
    match /{allPaths=**} {
      allow read, write: if request.auth.token.admin == true;
    }
    
    // Kiosk write-only access for specific collections
    match /kiosks/{kioskId} {
      allow write: if true; // In production, use App Check
      allow read: if false;
    }
  }
}
```
