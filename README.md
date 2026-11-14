# TV Launcher v3.2 (derinlik efektli, Apple TV tarzı)

Android TV / Google TV için buzlu cam görünümlü ana ekran.

## Neler var

- **Derinlik efekti:** saat, duvar kağıdındaki kayaların (veya dağların) arkasında durur
- Senin fotoğrafın + 4 hazır manzara (Gece gölü, Aurora, Gün batımı, Derin mavi)
- **Otomatik vurgu rengi:** odak ışığı, kenar parlaması ve dock tonu duvar kağıdından alınır
- Deniz camı dock: odağı izleyen ışık, su yansıması, kartlarda ışık süpürmesi
- Ekran kenarlarında odak değişince nabız atan ışıma
- Kontrol merkezi: duvar kağıdının renginde yarı saydam cam butonlar
- **Geçişler:** uygulama basılan kutucuktan büyüyerek açılır, dönünce dock alttan yaylanır;
  kontrol merkezi sağdan, tüm uygulamalar alttan gelir
- **4K isteği:** TV uygulamalara 4K modu sunuyorsa arayüz o modda çizilir
- Çalan müzik kartı ve oynatma tuşları
- Saat modu: 5 dakika tuşa basılmazsa yalnızca manzara ve saat kalır
- Tüm uygulamalar: yaylanarak açılır, OK tuşuna basılı tutarak çubuğa sabitlenir

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

## Duvar kağıdı ve derinlik katmanı

`app/src/main/res/drawable-nodpi/` içinde iki dosya var:

- `wallpaper.jpg`: arka plan fotoğrafı
- `wallpaper_fg.png`: aynı fotoğrafın saydam zeminli ön planı (kayalar). Saatin üstüne çizilir.

Başka bir fotoğraf kullanmak istersen ikisini de değiştir. `wallpaper_fg.png` olmazsa fotoğraf
yine görünür, yalnızca derinlik efekti kapalı kalır. Ön plan dosyası arka planla aynı boyutta olmalı.

## 4K

Uygulama açılışta TV'nin sunduğu en yüksek çözünürlüklü ekran modunu ister. Birçok TV arayüzü
1080p çizer ve yalnızca videoyu 4K oynatır; o durumda bir şey değişmez. Etkin çözünürlüğü görmek için:

    adb shell dumpsys display | grep mBaseDisplayInfo

Launcher'a girip çıkarken ekran kısa süre kararıyorsa `Data.kt` içinde `PREFER_4K = false` yap.
Fotoğrafın 4K'da keskin görünmesi için `wallpaper.jpg` ve `wallpaper_fg.png` 3840x2160 olmalı
(şu anki fotoğraf 1672x941).

## Müzik kartı izni

TV ayarlarında bu izin genelde görünmez, ADB ile verilir:

    adb shell cmd notification allow_listener com.kizmark.tvlauncher/com.kizmark.tvlauncher.MediaListener

## Ayarlar (kod)

`app/src/main/java/com/kizmark/tvlauncher/Data.kt` başındaki `Config`:

- `DEFAULT_FAVORITES`: ilk açılışta çubuğa konan uygulamalar
- `AMBIENT_AFTER_MIN`: saat moduna geçiş süresi (0 = kapalı)

Saatin dikey yeri: `Ui.kt` başındaki `CLOCK_BASELINE` (0.52 = ekran yüksekliğinin %52'si).

## Kurulum

1. `.github/workflows/build.yml` depoda olsun.
2. ZIP'i deponun ana dizinine yükle. Actions zip'i açar, siler ve APK'yı derler.
3. APK'yı Releases bölümünden indirip TV'ye kur.
