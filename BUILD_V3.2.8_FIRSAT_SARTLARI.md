# V3.2.8 – Fırsat Kontrolü

Fırsat Kontrolü seçici algoritması ana `ScoringEngine.kt` içinde uygulanmıştır.

Zorunlu kapılar: veri, likidite, trend, yapı, hacim, risk/getiri, aşırı uzama, kritik seviye ve piyasa rejimi.

Skor dağılımı: Trend 20, Momentum 15, Hacim 15, Kırılım/Yapı 15, Göreceli Güç 15, Piyasa Rejimi 10, Risk/Getiri 10.

70+ tarama eşiğidir; 80+ fırsat adayı, 85+ güçlü fırsat, 90+ A+ teknik uyum olarak sınıflandırılır. Demo/sahte veri kullanılmaz.
