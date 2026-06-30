Merhaba hocam, projemiz MCP tabanlı LLM destekli bir web uygulamasıdır. Bu uygulamada kullanıcılar sisteme giriş yaparak PDF dokümanları yükleyebilir ve yükledikleri dokümanlar üzerinden yapay zeka destekli soru-cevap yapabilir.

Projemizin mimarisi dört ana bölümden oluşuyor. İlk bölüm frontend tarafı. Frontend, kullanıcının giriş yaptığı, doküman yüklediği ve sohbet ekranını kullandığı arayüzdür. İkinci bölüm backend tarafıdır. Backend; kullanıcı doğrulama, yetkilendirme, doküman yönetimi ve sohbet işlemlerinden sorumludur.

Üçüncü bölüm ai-service katmanıdır. Bu servis PDF içeriklerini işler, metni parçalara ayırır, embedding oluşturur, Qdrant üzerinde benzerlik araması yapar ve RAG mantığıyla cevap üretir. Dördüncü bölüm ise veri katmanıdır. MySQL kullanıcı, doküman, sohbet ve mesaj verilerini tutar. Qdrant ise doküman parçalarının vektörlerini saklayarak anlamsal arama yapılmasını sağlar.

Bu projede MCP’yi frontend ile kullanıcı arasındaki sohbet için değil, backend ile ai-service arasındaki iletişimi standartlaştırmak için kullandık. Backend, ai-service’e doğrudan klasik REST mantığıyla gitmek yerine MCP tool çağrıları yapar. AI service tarafında document.index, retrieval.search ve rag.answer gibi tool’lar tanımlıdır.

RAG akışı ise şu şekilde çalışır: Kullanıcı PDF yüklediğinde PDF metni okunur, sayfa bazlı parçalara ayrılır ve her parça embedding’e dönüştürülerek Qdrant’a kaydedilir. Kullanıcı soru sorduğunda soru da embedding’e çevrilir, Qdrant’ta en ilgili doküman parçaları bulunur ve LLM yalnızca bu bağlamı kullanarak cevap üretir.

Bu mimaride frontend sadece arayüz görevini üstlenir. Backend kullanıcı ve iş akışını yönetir. AI service ise yapay zeka ve doküman zekası işlemlerinden sorumludur. MCP de backend ile AI service arasında standart, genişletilebilir ve tool tabanlı bir iletişim katmanı sağlar.

Kısaca projemiz, PDF dokümanları üzerinden kaynak destekli cevap üreten, RAG mimarisi kullanan ve AI servis iletişimini MCP ile standartlaştıran bir web uygulamasıdır.

Projemizde kullanıcı oturumu access token ve refresh token yapısıyla yönetilmektedir. Access token kısa ömürlü bir JWT’dir ve API isteklerinde Authorization header içinde kullanılır. Refresh token ise daha uzun süreli olarak veritabanında saklanır ve access token süresi dolduğunda yeni token üretmek için kullanılır. Refresh işleminde eski refresh token iptal edilip yenisi üretildiği için refresh token rotation uygulanmaktadır. Logout ve şifre değiştirme işlemlerinde refresh token’lar revoke edilerek oturum güvenliği sağlanır.