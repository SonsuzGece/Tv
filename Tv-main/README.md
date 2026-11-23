# TV Launcher 4.0 (Vestel Google TV)

Apple TV havasında, Google TV ana ekranından hızlı bir launcher.

## Neden hızlı
- **Tek resim sahne:** Duvar kağıdı, saat, kayalar ve dock camı dakikada bir arka planda tek bir resme çizilir. Ekran her karede sadece bu resmi gösterir.
- **Uygulama açıkken sıfır iş:** Launcher arkadayken müzik, ağ ve saat döngüleri durur, büyük resimler bellekten atılır.
- **Optimize derleme:** APK R8 ile küçültülür ve hata ayıklama kapalı derlenir. Kurulum scripti uygulamayı TV'de tam derler (`speed`).
- **Dock kaydırma GPU'da:** Sadece görünen kutucuklar çizilir, kaydırma tek bir kaydırma değeridir. Kutucuk görselleri bir kez, yuvarlatılmış hazırlanır.

## Ana ekran
- Dock'ta tüm uygulamalar tek sırada, aynı anda 5 tane görünür, sağa/sola kaydırılır.
- Sağ üst: ağ, kontrol merkezi, tüm uygulamalar, ayarlar.
- OK basılı tut: Aç, Taşı, Başa al, Dock'tan gizle, Uygulama bilgisi, Kaldır.

## Kontrol merkezi
Wi-Fi, Bluetooth, Ses, Görüntü, Girişler (HDMI / Canlı TV), Uyku zamanlayıcı, Duvar kağıdı, Saat stili, Derinlik, Hızlandır, Yansıtma, Sesli asistan, Google TV ekranı, Bildirimler; altta Erişilebilirlik, Saat modu, Play Store, Hesap, Güç, Ayarlar.

Yansıtma, asistan, Google TV ekranı, uyku, hızlandır ve güç, TV'nin ağ hata ayıklamasını kullanır. İlk kullanımda TV "Hata ayıklamaya izin ver?" sorar: **Her zaman izin ver**.

## Kumanda
| Tuş | İş |
|---|---|
| Menü / Kırmızı | Kontrol merkezi |
| Yeşil | Tüm uygulamalar |
| Sarı | Sonraki duvar kağıdı |
| Mavi | Saat modu |
| Home (ana ekrandayken) | Dock'un başına dön |

## Kurulum
1. Bu zip'i depo ana dizinine yükle; GitHub Actions APK'yi derler ve Releases'e koyar.
2. Telefonda Pydroid ile `launcher_kur.py`'yi çalıştır, **1**'i seç. APK'yi indirir, TV'ye kurar, ana ekran yapar, müzik kartı iznini verir ve tam derler.

4.0 yeni bir imzayla geliyor: eski 3.x sürümü script otomatik kaldırıp yenisini kurar (dock sırası sıfırlanır). Bundan sonraki güncellemeler üstüne kurulur.

## Duvar kağıdı
`app/src/main/res/drawable-nodpi/wallpaper.jpg` arka plan, `wallpaper_fg.png` aynı fotoğrafın saydam ön planı (saatin önüne geçen kayalar).
