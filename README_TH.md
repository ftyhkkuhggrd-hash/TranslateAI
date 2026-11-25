# TranslateAI — Android Kotlin

โปรเจกต์ Android ใหม่ที่มีแชต AI และโหมดค้นหาเว็บด้วย **Google Search grounding ผ่าน Gemini API**  
แอปแสดงแหล่งข้อมูลเป็นรายการลิงก์แยก พร้อมปุ่มเปิด Browser และคัดลอก URL

## ความสามารถ
- Kotlin + Jetpack Compose / Material 3
- แชตกับ Gemini 2.5 Flash
- สวิตช์เปิด/ปิด Google Search grounding ในหน้าแชต
- โหมดค้นหาใช้ข้อความที่พิมพ์ในช่องแชตเป็นคำค้น/คำถาม
- แสดงชื่อหน้าเว็บและ URL จาก grounding metadata (ไม่สร้างลิงก์ขึ้นเอง)
- ตรวจ URL ให้เป็น HTTP/HTTPS ก่อนเปิด
- เปิดผ่าน Android `Intent.ACTION_VIEW`; ถ้าเปิดไม่ได้จะแจ้งและคัดลอก URL
- จำกัดผลลิงก์ไม่เกิน 10 รายการและตัด URL ซ้ำ
- จัดเก็บ Gemini API key ด้วย AES-GCM ผ่าน Android Keystore

## สิ่งที่ต้องมี
- Android Studio รุ่นที่รองรับ Android Gradle Plugin 8.7.3
- JDK 17
- Android SDK Platform 35
- Gemini API key จาก https://aistudio.google.com/app/apikey

## วิธีสร้าง APK
1. แตก ZIP นี้
2. เปิดโฟลเดอร์ `TranslateAI` ด้วย Android Studio
3. รอ Gradle Sync ให้เสร็จ
4. ไปที่ **Build > Build Bundle(s) / APK(s) > Build APK(s)**
5. ไฟล์ APK จะอยู่ที่ `app/build/outputs/apk/debug/app-debug.apk`

## สร้าง APK โดยไม่ต้องติดตั้ง Android Studio ในเครื่อง
มี GitHub Actions workflow ที่ `.github/workflows/build-apk.yml`
1. สร้าง repository ส่วนตัวบน GitHub แล้วอัปโหลดไฟล์ทั้งหมดใน ZIP
2. เปิดแท็บ **Actions**
3. เลือก **Build TranslateAI APK** แล้วกด **Run workflow**
4. เมื่อสำเร็จ ดาวน์โหลด Artifact ชื่อ `TranslateAI-debug-apk` ซึ่งมี APK อยู่ภายใน

## ตั้งค่า API ในแอป
1. เปิดแอป
2. กดไอคอนตั้งค่ามุมขวาบน
3. วาง Gemini API key แล้วกดบันทึก
4. กลับหน้าแชต เปิดสวิตช์ค้นหาข้อมูล แล้วถามสิ่งที่ต้องการค้นหา

## หมายเหตุสำคัญเรื่อง Google Search
แอปนี้ใช้ Gemini API พร้อมเครื่องมือ `google_search` (Grounding with Google Search) เพื่อให้เหมาะกับการเริ่มโปรเจกต์ใหม่  
ไม่ได้ใช้ Google Custom Search JSON API เพราะ Google ระบุว่าปิดรับลูกค้าใหม่แล้ว และมีประกาศให้บริการเดิมเปลี่ยนผ่านภายใน 1 มกราคม 2027:
https://developers.google.com/custom-search/v1/overview

การเรียก Gemini API ต้องมี API key ของผู้ใช้เอง และขึ้นกับโควตา/เงื่อนไขของ Google AI Studio และ Gemini API

## ขอบเขตการทดสอบ
ซอร์สโค้ดถูกจัดทำเป็นโปรเจกต์ต้นทาง แต่สภาพแวดล้อมที่สร้าง ZIP นี้ไม่มี Android SDK และไม่มี Gradle ติดตั้ง จึงยังไม่ได้รัน Android build หรือทดสอบบนอุปกรณ์จริง และไม่ได้แนบ APK ที่อ้างว่าผ่านการ Build แล้ว
