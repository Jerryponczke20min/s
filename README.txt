Radar Graczy - mod kliencki Fabric 1.21.4

BUDOWANIE
  Wymagane: JDK 21 i Gradle 8.11+ (w folderze: gradle wrapper, potem ./gradlew build).
  Gotowy jar: build/libs/playerradar-1.0.0.jar -> do folderu mods (razem z Fabric API).
  Latwiej: wygeneruj szablon na https://fabricmc.net/develop/template (1.21.4),
  i podmien w nim folder src/ oraz plik fabric.mod.json.

UZYCIE
  R               - otwiera GUI (klawisz zmienisz w Sterowanie > Radar Graczy)
  /radar          - otwiera GUI
  /radar debug    - wypisuje surowy nametag i odczytane liczby (do diagnozy parsera)
  /radar trace <nick> | /radar trace off
  Config: .minecraft/config/playerradar.json
