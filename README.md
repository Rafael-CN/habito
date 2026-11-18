# Hábito

App Android minimalista de acompanhamento de hábito único, com mapa de calor anual e widgets interativos na tela inicial.

## Stack

- **Kotlin 2.0** · **Jetpack Compose** (Material 3)
- **Jetpack Glance** (`GlanceAppWidget` completo com grade anual e variante compacta semanal)
- **DataStore Preferences** & **WorkManager** (persistência local, lembretes e backup automático)
- **Baseline Profiles** (`:baselineprofile`) · Min SDK 31 · Target/Compile SDK 34

## Estrutura

- `app/` — código-fonte do app, widgets Glance, temas, feedback sonoro/háptico e testes unitários
- `baselineprofile/` — módulo gerador de Baseline e Startup Profiles
- `licenses/` — licenças de fontes embutidas (`Archivo`)

## Build e testes

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```
