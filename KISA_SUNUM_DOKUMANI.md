# MCP Tabanli LLM Destekli Web Uygulamasi - Kisa Sunum Notu

## Projenin Amaci

Bu proje, kullanicilarin PDF dokumanlari yukleyip bu dokumanlar uzerinden yapay zeka destekli soru-cevap yapabilmesini saglayan bir RAG uygulamasidir. Sistem, kullanicinin sorusuna genel internet bilgisiyle degil, yuklenen dokumanlardan bulunan ilgili parcalara dayanarak cevap uretir.

## Kisa Mimari

Proje dort ana katmandan olusur:

1. Frontend
   - Kullanicinin giris yaptigi, PDF yukledigi ve sohbet ettigi arayuzdur.
   - Backend API'leri ile haberlesir.

2. Backend
   - Kimlik dogrulama, dokuman yonetimi, sohbet yonetimi ve kullanici yetkilendirmesinden sorumludur.
   - AI islemlerini dogrudan kendisi yapmaz; ai-service'e MCP uzerinden tool cagrilari yapar.

3. AI Service
   - PDF metnini isler, chunk'lara ayirir, embedding uretir, Qdrant uzerinde arama yapar ve RAG cevabi olusturur.
   - Bu servis MCP server olarak calisir.

4. Veri Katmani
   - MySQL: Kullanici, dokuman, sohbet ve mesaj verilerini tutar.
   - Qdrant: Dokuman chunk'larinin embedding verilerini saklar ve semantic search yapar.

## MCP'nin Projedeki Yeri

Bu projede MCP, frontend ile kullanici sohbeti icin kullanilmaz. MCP, backend ile ai-service arasindaki standart iletisim protokoludur.

Backend, ai-service'e klasik REST mantigiyla "su endpoint'e git" demek yerine MCP tool cagrilari yapar. AI service tarafinda bu tool'lar tanimlidir:

- document.embed
- document.index
- document.delete
- retrieval.search
- rag.answer

Bu sayede backend, AI service'in yeteneklerini standart ve genisletilebilir bir sekilde kullanir.

## RAG Akisi

1. Kullanici PDF yukler.
2. Backend dokumani alir ve ai-service'e MCP uzerinden isleme istegi gonderir.
3. AI service PDF metnini sayfa bazli okur.
4. Metin kucuk chunk'lara ayrilir.
5. Chunk'lar embedding'e donusturulur.
6. Embedding'ler Qdrant vector database'e kaydedilir.
7. Kullanici soru sordugunda soru da embedding'e donusturulur.
8. Qdrant'ta soruya en yakin dokuman parcalari bulunur.
9. LLM sadece bulunan kaynak parcalari kullanarak cevap uretir.
10. Cevap ve kaynak sayfa bilgileri backend uzerinden frontend'e doner.

## Neden Bu Mimari?

Bu mimari sayesinde frontend sade kalir, backend kullanici ve is akisini yonetir, AI service ise sadece yapay zeka ve RAG islemlerinden sorumlu olur. MCP ise backend ile AI service arasinda standart tool tabanli bir iletisim saglar.

## Kisa Sunum Cumlesi

Bu projede frontend kullanici arayuzu, backend auth ve orkestrasyon katmani, ai-service RAG ve dokuman zekasi katmani olarak calisir. MCP ise backend'in ai-service uzerindeki RAG islemlerini standart tool cagrilariyla yonetmesini saglar.
