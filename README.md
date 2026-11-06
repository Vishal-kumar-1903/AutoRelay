# WhatsApp Auto Poster (100% Free & On-Device Android App)

Ek automated Android app jo aapke phone par **Images, Videos, ya Stickers** ko aapke bataye gaye WhatsApp groups mein bina manual mehnat ke ek-ek karke send kar deti hai.

---

### ✨ Features (100% Free, No Paid APIs)
- **100% Free & Open Source**: Koi paid API, cloud subscription, ya third-party charge nahi hai. Sab kuch aapke phone par natively chalta hai.
- **Media Picker**: Phone gallery se koi bhi Photo, Video ya Sticker select karein.
- **Group Queue Manager**: Apne target WhatsApp groups add karein (ye phone mein save rehte hain, baar-baar add nahi karne padenge).
- **Anti-Ban Protection Engine**:
  - Har message ke beech randomized delay (default 6–12 seconds).
  - WhatsApp spam bot detection se account ko safe rakhta hai.
  - Live cooldown countdown progress bar.
- **Native Android Accessibility Automation**:
  - Automatically WhatsApp open karta hai.
  - Group name search karta hai aur select karta hai.
  - Send button click karke wapas cooldown queue par aa jata hai.

---

### 📁 Project Structure

```
whatsapp-auto-poster/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── res/
│   │   │   ├── xml/accessibility_service_config.xml
│   │   │   └── values/{strings,themes}.xml
│   │   └── java/com/autoposter/whatsapp/
│   │       ├── data/
│   │       │   └── AutomationState.kt
│   │       ├── service/
│   │       │   ├── AutomationManager.kt
│   │       │   └── WhatsAppAccessibilityService.kt
│   │       └── ui/
│   │           ├── MainActivity.kt
│   │           └── theme/{Color,Theme}.kt
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

### 🚀 How to Run in Android Studio

1. **Android Studio Kholein**:
   - `File -> Open` par click karein.
   - Ye path select karein:
     `C:\Users\lenovo\.gemini\antigravity\scratch\whatsapp-auto-poster`
2. **Gradle Sync**:
   - Android Studio automatically saare free dependencies download kar lega.
3. **Phone Connect Karein**:
   - Phone mein `Developer Options` aur `USB Debugging` ON karein.
   - Run button (Green Play Icon) dabayein ya `Build -> Build Bundle(s) / APK(s) -> Build APK(s)` karke APK direct install kar lein.

---

### ⚙️ First-Time Phone Setup (Bohot Zaroori)

Jab app install ho jaye:
1. App kholte hi upar **"Accessibility Permission Required"** ka banner aayega.
2. **"Enable in Settings"** par click karein.
3. Phone ki Accessibility list mein **"WA Auto Poster"** ko dhoondhein aur toggle **ON** kar dein.
   *(MIUI / Xiaomi / Realme / Vivo phones par "Downloaded apps" ya "Installed services" ke andar dikhta hai).*

---

### 🛡️ Safe Usage Tips (To Avoid WhatsApp Ban)
- Delay ko kam se kam **6–12 seconds** par hi rakhein.
- Ek batch mein **15–20 groups** se zyada mat bhejein.
- Har batch ke baad thoda time gap rakhein.
