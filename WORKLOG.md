# Průběh práce

## 2026-10-12

- Dokončeno: inventura iOS obrazovek, modelů, navigace a API klienta.
- Dokončeno: rozbor relace, oprávnění, cache, zámku, nastavení a feature flags.
- Zjištění: API https://www.sucto.cz/api/, autentizace hlavičkou Auth-Token.
- Zjištění: skeny jsou ve výchozí iOS verzi vypnuté.
- Zjištění: úhrada vydané faktury je mutující GET /pay; nesmí se cachovat.
- Založeno: Android Gradle projekt, Kotlin/Compose/Material 3, SDK 36, minSdk 26.
- Převzato: značkové logo, zelená paleta a rozměry karet z iOS.
- Rozpracováno: první etapa přihlášení a načtení firem.
- Pokyn zadavatele: nespouštět testování; ověření proběhne na telefonu u zadavatele.
- Dokončeno: zdrojové soubory přihlašovací obrazovky, API klienta, ViewModelu a seznamu firem (bez spuštění).
- Dokončeno: docs/IOS_PARITY.md s mapou funkcí a zvláštností API.
- Zbývá: dashboard a navazující funkce, zabezpečené trvalé uložení relace, ověření na telefonu.

- Etapa 2: doplněny modely faktur, peněžní částky BigDecimal, datumy a rychlé filtry.
- Etapa 2: API pro seznam/detail faktur a přehled z účetního deníku; při 403 fallback na faktury, měny odděleně.

- Etapa 2: výběr firmy otevře dashboard, systémové Back vrací do firem nebo ze detailu.
- Etapa 2: přehled s rokem, součty po měnách a měsíčním grafem, vydané/přijaté faktury, hledání, rychlé filtry, další stránky a čtení detailu.
- Etapa 2: změny hledání mají 400 ms prodlevu; starší odpovědi se ignorují a odchod z dashboardu zruší úlohy.

- Etapa 2: doplněna Pokladna s příjmovými/výdajovými doklady, hledáním, stránkováním a detailem.
- Etapa 2: OkHttp požadavky jsou asynchronní a ruší se se zrušením coroutine.
- Etapa 2: dashboard/pokladna mají vlastní ViewModelStore, který se při změně firmy či odhlášení vyčistí.
- Etapa 2 dokončena ve zdrojích; bez sestavení, spuštění, testů a volání API dle pokynu zadavatele.
- Další etapa: podrobné filtry, úplné detaily a platební QR, formulář nové/duplikované faktury a akce dokladů.

- Upřesnění z iOS InvoiceInitResponse: datumy odpovědi API jsou dd. MM. yyyy. Android parser podporuje český i ISO formát; použito pro splatnosti a měsíční součty.

- Etapa 3: rozebrána tvorba/duplikace faktury a SPAYD QR v iOS.
- Etapa 3: model formuláře, výpočty položek, reference pro partnery/účty/měny/DPH a API vytvoření faktury připraveny ve zdrojích.

- GitHub: nastaven origin https://github.com/Founasek/sucto-android.git dle zadavatele; nic nebylo odesláno.

- Etapa 3: formulář propojen s tlačítkem nové faktury a duplikací z detailu; datumy a číslo z /new, číselníky ze serveru.
- Etapa 3: podrobné filtry propojeny se seznamem; stav má přednost a datum po splatnosti se omezuje na včerejšek.
- Etapa 3: platební QR se generuje lokálně, používá zbývající dluh, kontrolu IBAN a sdílení PNG přes Android FileProvider.
- Etapa 3: oprávnění create načítána pro oba směry; při neznámých oprávněních rozhoduje server jako v iOS.

- Etapa 3: relace firmy a formuláře drží ViewModelStore v rodičovském ViewModelu, aby otočení telefonu nesmazalo rozepsanou fakturu. Při změně firmy/odhlášení se úložiště čistí.
- Etapa 3: opuštění formuláře vyžaduje potvrzení zahození; během odesílání je návrat zablokovaný.

- Etapa 3: doplněno potvrzení úspěšného vytvoření, detaily platebního účtu a patička faktury.
- Etapa 3 dokončena ve zdrojích: nové/duplikované faktury, podrobné filtry, platební QR a sdílení.
- Podle pokynu zadavatele: bez sestavení, spuštění, testů a volání sÚčto API. GitHub origin nastaven, bez push.
- Další etapa: úhrady a e-maily, úpravy existujících položek, partneři/ARES a exporty; poté offline data, biometrie a widget.

- Zadavatel požádal o commit a push aktuální fáze na origin/main. Sestavení ani testy se pro tento snapshot nespouštějí.

## 2026-10-14

- Převzato z iOS: záložka Firma (údaje o firmě, účty s detailem, přepínač firem i opakovaným klepnutím na záložku), z menu odebráno „Změnit firmu“.
- Převzato z iOS: zámek aplikace přes BiometricPrompt (biometrie/kód zařízení) bez možnosti vypnutí, jedno automatické ověření na zamčení, skrytý obsah v přepínači aplikací.
- Nepřevedeno, protože v Androidu zatím neexistuje: partneři, upozornění, widget, nastavení.
- Bez sestavení, spuštění, testů a volání API dle pokynu zadavatele; nové závislosti androidx.biometric 1.1.0 a androidx.fragment-ktx 1.8.3 nejsou ověřeny sestavením.
- Etapa 4: trvalá relace (token šifrovaný klíčem z Android Keystore), obnova po startu se zámkem, paměť poslední firmy.
- Etapa 4: partneři (seznam, hledání, detail, ARES, smazání), úhrada vydané faktury s potvrzením, e-mail, upomínky (výchozí vypnuté), CSV export, Nastavení, šifrovaná offline cache a štítek.
- Zbývá z důležitých funkcí: přehled (stáří pohledávek, prognóza 30 dní, žebříček partnerů, více měn), upozornění na splatnost (WorkManager, neutrální text), widget, úpravy položek faktury, ruční formulář partnera, Pohoda XML, rychlé přihlášení.
- Stále bez sestavení, spuštění, testů a volání API dle pokynu zadavatele.
- Design (2026-10-14): sdílené prvky v ui/Components.kt a ui/theme (karta 20, hero 28 s přechodem, kapslový přepínač, filtrové kapsle, odznak stavu, tlačítka s přechodem, prázdné/chybové/načítací stavy), flat záhlaví na barvě pozadí, spodní lišta s indikátorem, plovoucí kapsle „Nová faktura“, přehled s hero kartou a grafem se zaoblenými sloupci, detail faktury s hero kartou a sekcemi, automatické dočítání seznamů. Obrazovky Přehled/Faktury/Pokladna rozděleny do samostatných souborů. Bez sestavení a bez vizuální kontroly na telefonu.
- Přehled: stáří po splatnosti a prognóza na 30 dní (po měnách, česká koruna první), nejbližší splatnosti s odkazem na detail. Upozornění na splatnost přes WorkManager (výchozí neutrální). Logo firmy: sleduje přesměrování, zvedá http na https a skládá relativní adresy. Zbývá: widget, žebříček partnerů a srovnání roků, úpravy položek faktury, ruční formulář partnera, Pohoda XML, rychlé přihlášení.
- Přihlášení: tlačítko pro zobrazení hesla, biometrické přihlášení uloženým heslem (Keystore, per-use biometrie). Přehled: grafy po měsících pro více měn v pageru. Spodní lišta: vlastní vektorové ikony (přehled, faktury, pokladna, firma).
- Barva aplikace (2026-10-17): sedm předvoleb (ui/theme/AppAccent.kt, ThemeState), volba v Nastavení > Vzhled, ukládá se v AppSettings. Přebarvuje prvky, přechody, tmavé plochy, výnosy/vydané; náklady/přijaté mají protiváhu (u oranžové modrou). Stavy faktur zůstávají ve svých barvách. Sestavení (compileDebugKotlin) projde, vizuální kontrola na telefonu zbývá.
