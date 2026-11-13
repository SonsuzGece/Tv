# TV Launcher (Apple TV tarzı)

Android TV / Google TV için sade, hızlı bir ana ekran uygulaması.

- Büyük saat, tarih ve Türkçe selamlama
- Cam efektli alt çubuk, seçili kutucuk büyür ve parlar
- Uygulama görselleri TV'de kurulu uygulamalardan otomatik çekilir
- "Tüm uygulamalar" ve "Ayarlar" kutucukları

## Özelleştirme

`app/src/main/java/com/kizmark/tvlauncher/MainActivity.kt` dosyasının en üstündeki `Config` bölümü:

- `FAVORITES`: alt çubukta görünecek uygulamaların paket adları (sırayla)
- `ANIMATE_BACKGROUND`: arka plan ışıklarını hareketlendirir (zayıf TV'lerde kasabilir)

## Kurulum

1. GitHub'da bu ZIP'i deponun ana dizinine yükle (workflow dosyası `.github/workflows/build.yml` yolunda olmalı).
2. GitHub Actions zip'i açar, zip'i siler ve APK'yı derler.
3. APK'yı Releases bölümünden indir, TV'ye kur.
4. Home tuşuna basınca "TV Launcher" seçeneğini seç.
