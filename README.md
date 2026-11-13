# TV Launcher v2 (Apple TV tarzı)

Android TV / Google TV için buzlu cam görünümlü ana ekran.

## Neler var

- 4 hazır duvar kağıdı (Gece gölü, Aurora, Gün batımı, Derin mavi) + kendi fotoğrafın
- Ortada büyük saat ve tarih, altta buzlu cam uygulama çubuğu
- Kontrol merkezi: ağ, ses, duvar kağıdı, saat stili, saat modu, ekran, mağaza, ayarlar, boş RAM / depolama
- Çalan müzik kartı (Spotify vb.) ve oynatma tuşları
- Saat modu: 5 dakika tuşa basılmazsa sadece saat ve duvar kağıdı kalır
- Tüm uygulamalar ekranı, OK tuşuna basılı tutarak çubuğa sabitleme

## Kumanda

| Tuş | İşlev |
| --- | --- |
| OK (basılı tut), tüm uygulamalarda | Çubuğa sabitle / kaldır |
| OK (basılı tut), çubukta | Uygulamayı bir sola taşı |
| Kırmızı veya Menü | Kontrol merkezi |
| Yeşil | Tüm uygulamalar |
| Sarı | Sonraki duvar kağıdı |
| Mavi | Saat modu |

Renkli tuşlar yalnızca ana ekrandayken çalışır.

## Müzik kartı izni

TV ayarlarında bu izin genelde görünmez, ADB ile verilir:

    adb shell cmd notification allow_listener com.kizmark.tvlauncher/com.kizmark.tvlauncher.MediaListener

## Kendi duvar kağıdın

Fotoğrafı `app/src/main/res/drawable-nodpi/wallpaper.jpg` olarak depoya ekle ve yeniden derle.
Listede "Fotoğraf" adıyla ilk sırada çıkar.

## Ayarlar (kod)

`app/src/main/java/com/kizmark/tvlauncher/Data.kt` başındaki `Config`:

- `DEFAULT_FAVORITES`: ilk açılışta çubuğa konan uygulamalar
- `AMBIENT_AFTER_MIN`: saat moduna geçiş süresi (0 = kapalı)

## Kurulum

1. `.github/workflows/build.yml` depoda olsun.
2. ZIP'i deponun ana dizinine yükle. Actions zip'i açar, siler ve APK'yı derler.
3. APK'yı Releases bölümünden indirip TV'ye kur.
