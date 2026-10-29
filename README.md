# GuideR

Android için oyun rehberi okuyucu. Pixel Guide'dan esinlenildi; en büyük farkı **her okuma ekranında sesli okuma (TTS)** olması.

## Özellikler

**Sesli okuma**
- TXT, HTML, web kısayolu ve PDF rehberlerde Android'in metin okuma motoruyla sesli okuma
- Okunan cümle ve kelime vurgulanır, ekran otomatik kayar
- Bir paragrafa uzun basınca o noktadan okumaya başlar; okuma açıkken dokunmak da aynı işi yapar
- Hız, ton, dil, ses ve motor seçimi; rehberin dili otomatik algılanır (ML Kit)
- `====`/`----` gibi ASCII süslerini atlar
- Kumanda: Y = başlat/duraklat, L2/R2 = önceki/sonraki cümle

**Kütüphane**
- Oyunlar ızgara veya liste görünümünde, rehber sayısı rozetiyle gösterilir
- Oyun ekleme: Wikipedia/Wikidata'dan kapak, çıkış yılı ve platformlar otomatik gelir (API anahtarı gerekmez)
- Özel oyun ekleme ve her oyuna kendi kapağını koyma
- İçe aktarma: TXT, HTML, PDF, resim, CBZ
- Kısayol URL'leri ekleme
- Oyun başına depolama boyutu

**Rehber bulma**
- Uygulama içi tarayıcıda GameFAQs ve Neoseeker
- Rehber sayfası algılanınca tek tuşla indirme: TXT, çok sayfalı HTML ve resimler tek bir dosyada toplanır
- Herhangi bir sayfayı kaydetme; tarayıcıda PDF kılavuzları indirme
- Reklam engelleme

**Okuyucular**
- TXT: akıllı metin biçimlendirme, iki yana yaslama, otomatik içindekiler, arama, kaldığı yerden devam, yazı boyutu ve yazı tipi
- HTML: içindekiler, alt gezinme çubuğu, arama, geri/ileri, tema renkleri, resme dokununca tam ekran zoom
- PDF: sayfa genişliğine sığdırma
- Resim: zoom, kalem/dikdörtgen/daire/metin notları
- CBZ: sayfa çevirme

**Diğer**
- 14 tema
- Türkçe ve İngilizce arayüz
- Kumanda desteği
- Çoklu pencere
- Cihaz üzerinde TXT çevirisi (ML Kit, 59 dil)
- Pixie AI: kendi Gemini anahtarınla, web aramalı
- RetroAchievements ilerleme takibi; filtreleme, sıralama ve kaçırılabilir/ilerleme/bitirme simgeleri

## Derleme
Her `main` push'unda GitHub Actions APK üretir ve Releases'a koyar.
