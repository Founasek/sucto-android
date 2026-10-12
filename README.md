# sÚčto Android

Nativní Android verze iOS aplikace sÚčto. Kotlin, Jetpack Compose a Material 3.

## Stav

Založený projekt, barevné téma, přihlášení, výběr firmy a dashboard se třemi záložkami.
Přehled obsahuje roční a měsíční součty s grafem. Faktury mají oba směry, hledání,
rychlé i podrobné filtry, stránkování, detail, platební QR a sdílení.
Nové/duplikované faktury mají formulář, číselníky, výběr partnera a výpočty položek. Pokladna má oba směry, hledání,
stránkování a základní detail. Další funkce viz docs/IOS_PARITY.md. Relace je pro první etapu pouze v paměti;
trvalé přihlášení bude doplněno přes Android Keystore společně s biometrickým zámkem.
Přihlašovací údaje nejsou předvyplněné ani uložené v repozitáři.

## Otevření

V Android Studiu otevřete tuto složku a nechte IDE synchronizovat Gradle.
SDK 36, JDK 17 nebo novější (dostupné JBR z Android Studia), prozatím minSdk 26.
Application ID `cz.sucto.android` je pracovní návrh před publikací.

## Předloha

Sousední repozitář `../sucto-iOS`, výchozí revize
`0e22fa74f59af541371cf017bb7cea6ba5985a14`.
Mapa funkcí a důležitých odlišností API je v `docs/IOS_PARITY.md`.
Průběh práce je v `WORKLOG.md`.

## Ověřování

Na žádost zadavatele se nespouští testy ani aplikace a nevolá se API z vývojového prostředí.
Sestavení a chování zatím nejsou ověřené. Testování provede zadavatel na telefonu.

GitHub origin: https://github.com/Founasek/sucto-android.git. Aktuální vývojová fáze není ověřená sestavením ani testy.
