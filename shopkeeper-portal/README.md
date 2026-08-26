# AgroVision Shopkeeper Portal

This is an independent web application for agricultural shopkeepers to manage their AgroVision kiosks and view shop-related data.

## Features (Phase 1)
- **Shopkeeper Authentication**: Secure login via Mobile Number + OTP (Firebase Auth).
- **Security Check**: Only pre-registered shopkeeper phone numbers are allowed access.
- **Shop Identification**: View linked shop details.
- **Kiosk Management**: View and link/pair new kiosk devices to the shop.
- **Multi-tenant Architecture**: Secure data access restricted to the shopkeeper's own shop and kiosks.

## Tech Stack
- React + Vite
- Tailwind CSS
- Firebase (Authentication, Firestore)
- Lucide React (Icons)
- React Router DOM

## Getting Started

1. **Install Dependencies**:
   ```bash
   npm install
   ```

2. **Environment Variables**:
   Copy `.env.example` to `.env` and fill in your Firebase configuration.

3. **Development**:
   ```bash
   npm run dev
   ```

4. **Build**:
   ```bash
   npm run build
   ```

## Firebase Data Structure (Phase 1)

### `shopkeepers` Collection
```json
{
  "name": "Shopkeeper Name",
  "phoneNumber": "+91XXXXXXXXXX",
  "shopId": "SHOP_ID",
  "status": "active",
  "createdAt": "timestamp"
}
```

### `shops` Collection
```json
{
  "shopName": "Shop Name",
  "address": "...",
  "status": "active",
  "createdAt": "timestamp"
}
```

### `kiosks` Collection
```json
{
  "kioskId": "AGV-KIOSK-XXXX",
  "pairingCode": "123456",
  "shopId": "SHOP_ID",
  "status": "active",
  "deviceName": "AgroVision Kiosk",
  "linkedAt": "timestamp"
}
```
