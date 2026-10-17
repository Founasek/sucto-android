# Funkční shoda s iOS

Předloha: Founasek/sucto-iOS, revize 0e22fa74f59af541371cf017bb7cea6ba5985a14.
Toto je mapa zdrojového kódu, nikoli potvrzení shody při běhu. iOS změny sledovat před každou etapou.

| Oblast | Chování v iOS | Android stav |
| --- | --- | --- |
| Přihlášení | POST sessions/create, email/password, authentication_token | První implementace, neověřeno |
| Firmy | GET companies, výběr aktivní firmy | Načtení a výběr firmy, změna firmy z menu |
| Přehled | roky/měsíce, výsledky, grafy, měny, srovnání, aging, forecast, top partneři, systémová zpráva | Roční/měsíční součty a graf, fallback na faktury, stáří po splatnosti, prognóza na 30 dní s nejbližšími splatnostmi (klepnutím detail), pageru po měnách; žebříček největších odběratelů a dodavatelů (jen v přehledu z faktur, jako v iOS); srovnání s minulým rokem a systémová zpráva zbývají |
| Faktury | Vydané/přijaté, stránkování, hledání, rychlé a podrobné filtry | Oba směry, hledání, rychlé i podrobné filtry a další stránky; neověřeno za běhu |
| Detail faktury | stavy, položky, partner, účet, data, kopírování, platební QR | Čtení detailu, stav, datumy, VS, účet, položky a QR včetně sdílení; kopírování jednotlivých polí zbývá |
| Akce faktury | úhrada vydané faktury, e-mail, ruční upomínka | Úhrada (s potvrzením, GET /pay), odeslání e-mailem (PATCH send_to_email) a upomínka s upravitelným textem implementovány; upomínky jsou ve výchozím stavu vypnuté |
| Nová faktura | oba směry, číselníky, položky, DPH, součty, duplikace | Formulář obou směrů, partneři, číselníky, položky, výpočty, validace a duplikace implementovány; neověřeno za běhu |
| Úpravy položek | přidání, úprava a mazání řádků existující faktury | Zbývá |
| Pokladna | příjmové/výdajové, hledání, stránkování, detail, odeslání | Oba směry, hledání, další stránky a základní detail; odesílání zbývá |
| Partneři | hledání, detail, obratové grafy, formulář, úprava/mazání, ARES | Seznam s hledáním, detail, přidání z ARES a smazání (vstup ze záložky Firma); obratové grafy a ruční formulář/úprava zbývají |
| Firma | záložka Firma: údaje (GET companies/{id}), účty, partneři, přepnutí firmy (tlačítko, opakované klepnutí na záložku) | Údaje, účty a přepnutí firmy (tlačítko i opakované klepnutí) implementovány; partneři zbývají; DIČ API nevrací |
| Účty | seznam, detail, měna | Seznam a detail účtů v záložce Firma (kopírování čísla účtu, IBAN, SWIFT); měna zůstatku zbývá |
| Export | CSV aktuálního filtru, Pohoda XML jednotlivě/období a sdílení | CSV aktuálního hledání a filtrů (UTF-8 s BOM, `;`) a sdílení; Pohoda XML zbývá |
| Skeny | kamera/soubory, upload, polling, náhled, předvyplnění přijaté faktury | V iOS vypnuto; Android zachová přepínač |
| Relace | Keychain token, odhlášení na 401, mazání cache při odhlášení | Token šifrovaný klíčem z Android Keystore (AES-GCM), obnova po startu se zámkem, 401 i odhlášení mažou token a cache |
| Oprávnění | create/read/update/destroy pro ActuarialsIn/Out a Partner | create u faktur implementováno; ostatní zdroje/akce zbývají |
| Offline | uložená GET data, datum cache, platnost 30 dní, šifrování | Šifrovaná cache GET odpovědí (Keystore), platnost 30 dní, vynechává `_cont`, `/new`, `/pay`, štítek „Offline – data z …“ |
| Zámek | biometrie/kód zařízení, vždy zapnutý (nejde vypnout), studený start, prodleva 0/60/300 s | BiometricPrompt s kódem zařízení jako zálohou, nejde vypnout, prodleva pevně 60 s, jedno automatické ověření na zamčení, skryté okno v přepínači aplikací (FLAG_SECURE); volba prodlevy a zámek po studeném startu čekají na trvalou relaci |
| Uložené přihlášení | volitelné, zabezpečené, biometrické ověření | Nabídka po ručním přihlášení (Uložit / Teď ne / Nikdy), e-mail a heslo zašifrované klíčem z Keystore, který jde použít jen po biometrii (silná biometrie, klíč se zneplatní při změně biometrie); přihlášení tlačítkem na přihlašovací stránce, smazání v Nastavení i na přihlašovací stránce |
| Upozornění | splatnosti denně 9:00, ve výchozím stavu neutrální text bez firmy a počtů, podrobnosti jen na výslovné zapnutí | WorkManager denně kolem 9:00, úloha si sama načte nezaplacené faktury naposledy otevřené firmy; výchozí neutrální text, podrobnosti na zapnutí a na zamčené obrazovce vždy jen veřejná neutrální verze; klepnutí otevře aplikaci (bez odkazu na seznam po splatnosti) |
| Widget | snapshot splatností firmy, obnova po otevření, soukromí (jména i částky skryté na zamčené obrazovce) | Zbývá, Android AppWidget; skrývat jména i částky na zamčeném zařízení |
| Nastavení | zámek, rychlé přihlášení, upozornění, upomínky, widget, cache, verze | Prodleva zámku, upomínky (zapnutí a text), verze, odhlášení; upozornění, widget a rychlé přihlášení zbývají |

## Smlouva API a významné zvláštnosti

- Base URL https://www.sucto.cz/api/, autorizace `Auth-Token`, běžný timeout 30 s, upload 120 s.
- API klient: 401 = odhlášení; 403 = oprávnění; 400/422 = serverová validace.
  `errors` může být text, seznam nebo mapa polí na seznamy. Některé akce vracejí prázdné tělo.
- Endpointy faktur: companies/{id}/actuarials_outs a actuarials_ins.
- Úhrada vydané faktury je **GET .../{invoiceId}/pay**, přesto vytváří pokladní doklad.
  Nikdy ji neprovádět při přednačítání a nikdy ji neobsluhovat z offline cache.
- Cache v iOS vynechává hledání `_cont`, `/new` a `/pay`; fallback nastává jen při výpadku sítě.
- Hledaný text s číslicí se bere jako číslo dokladu, jinak jako jméno partnera.
- Filtr po splatnosti používá nejvýše včerejší due_date; klient vyřazuje zaplacené/stornované doklady.
  Podrobný stav má přednost před rychlým filtrem; paid=8, concept=1.
- Částky mají v části modelů tolerantní dekódování text/číslo; port nesmí předpokládat jednotný JSON typ.
- Pole status pokladního dokladu se záměrně ignoruje: iOS popisuje, že server vrací nabídku akce storno,
  nikoliv skutečný stav dokladu.
- Řádek: základ = množství × jednotková cena × (1 − sleva/100); DPH = základ × sazba/100.
  Při portu zachovat chování a ověřit zaokrouhlení na telefonu proti serveru.
- Oprávnění v iOS při nedostupných údajích akce nezablokují, konečné rozhodnutí nechávají serveru.
- Upomínky se neposílají automaticky. Uživatel potvrzuje každý e-mail a může upravit text.

## Vizuální a platformní pravidla

- Brand #55B560, ink #0E2A1C, inkSoft #17402B, gradient #2F9E55 → #1B6B3D.
- Rozestupy 4/8/12/16/24/32, radius karty 20, ovládací prvky 14, login karta 28, logo 88.
- Přihlášení v tmavém vzhledu; ostatní obrazovky respektují systémový světlý/tmavý režim.
- Zachovat obsah, hierarchii, pořadí sekcí a české texty.
- SwiftUI Form/sheet/picker/menu/tab bar převést na Material 3 komponenty,
  systémové sdílení, Android výběr souborů, biometrické ověření a systémové Back.
- Dashboard má Přehled / Faktury / Pokladna. Faktury přepínají Vydané / Přijaté.
- Skutečná vizuální shoda se bude posuzovat na telefonu; ze SwiftUI zdrojů nelze potvrdit pixelovou shodu.

## Otevřená rozhodnutí

- Nejstarší podporovaný Android: pracovní návrh 8.0 (API 26), čeká na volbu zadavatele.
- Finální applicationId: pracovní návrh cz.sucto.android, potvrdit před vydáním.
- Produkční API bez testovacího prostředí; zadavatel provede testy na telefonu.
- Publikace, signing a Google Play účet se řeší až před distribucí.
## Upřesnění datumů
Odpovědi API mají český formát dd. MM. yyyy (iOS String.toDate). Parametry dotazů používají yyyy-MM-dd. Android parser čte oba formáty.

## Etapa 3

- Nová vydaná/přijatá faktura a duplikace podle iOS, číslo/datumy z `/new`.
- Partner se hledá podle názvu/IČ/DIČ/adresy; při výběru se doplní splatnost a případně měna.
- Rozepsaná faktura držena v paměti ViewModelu přes otočení telefonu; po ukončení procesu se záměrně neukládá.
- Tvorba faktury se provede až klepnutím uživatele na Vytvořit fakturu. Žádný zápis nebyl proveden vývojovým nástrojem.
- Síťové požadavky se automaticky neopakují; při nejasném výsledku vytvoření UI upozorní na kontrolu seznamu.
- QR normativní podklad: https://qr-platba.cz/pro-vyvojare/specifikace-formatu/.
  SPAYD je generován lokálně bez sdílení finančních údajů s generátorem třetí strany.
  AM se omezí na podporovaných 9 999 999,99; nad tímto limitem se QR nenabízí.
- Nic nebylo sestaveno, spuštěno ani testováno podle pokynu zadavatele.
## Shoda s iOS 2026-10-14 (Firma, zámek, soukromí)

- Záložka Firma: Přehled / Faktury / Pokladna / Firma. „Změnit firmu“ z menu je pryč, firmu lze přepnout tlačítkem na záložce
  nebo opakovaným klepnutím na záložku Firma. Přepnutí čistí view modely původní firmy, takže faktury a přehled patří nové firmě.
- Zámek aplikace nejde vypnout. Bez kódu nebo biometrie na zařízení se zamknout nedá.
- Android nemá obdobu iOS Privacy Manifestu. Odpovídá mu formulář Data safety v Google Play Console; při publikaci uvést e-mail,
  finanční údaje a obsah uživatele (jen funkce aplikace, bez sdílení třetím stranám a bez sledování).
- Vstupní animace karet, které se v iOS odstranily, Android nikdy neměl; pozadí všech záložek je `MaterialTheme.colorScheme.background`.
- Beze změny: partneři, upozornění a widget v Androidu zatím neexistují, proto se tam nic neupravovalo.
