# D.5 IA — ADAPTIVE TRADING INTELLIGENCE PRO v12

Aplicación Android de análisis y señales para MEXC USDT-M Futures. Esta versión evoluciona la lógica de confianza artificial por un ensemble determinista, auditable y calibrable.

## Lo que realmente hace
- MTF 4H -> 1H -> 15M -> 5M, con 1M para datos de alta resolución.
- Seis motores independientes y activables: Trend, Breakout, Liquidity Sweep, SMC, Mean Reversion y Momentum.
- Clasificador de régimen: tendencia, rango, choppy, alta/baja volatilidad.
- Entry, SL, TP1, TP2 y R:R dinámicos en función de ATR, estructura y zonas.
- Modos PASSIVE / BALANCED / AGGRESSIVE para adaptar la toma de riesgo al contexto.
- Scanner dinámico de MEXC USDT Futures para buscar oportunidades en más mercados, no solo BTC/ETH/XAU.
- Gráfico táctil con zoom, navegación histórica, velas, zonas, VWAP y líneas de ENTRY/SL/TP.
- Paper trading integrado: simula entradas sin enviar órdenes.
- Quant Lab con backtest causal, walk-forward/OOS, profit factor, drawdown, Sharpe aproximado y Monte Carlo.
- Detector simple de posible sobreajuste comparando tramo de entrenamiento contra OOS.
- Jarvis local explicable para responder por qué hay señal, dónde entrar y qué invalida.
- Datos históricos comprimidos por activo/timeframe.
- Sin claves/API secretas dentro del código y sin ejecución real habilitada.

## Precisión
No se fija una promesa de 90% de acierto. 90/100 es calidad de setup, no una garantía de ganar. El laboratorio muestra el rendimiento observado y fuera de muestra, que es lo que debe usarse para decidir si un motor merece entrar en producción.

## MEXC
El cliente usa `https://api.mexc.com` para Futures. La selección dinámica parte de `api/v1/contract/detail` y puede consultar tickers y velas del universo de contratos USDT.

## Compilar en Termux
1. Instala Java 21 y Gradle desde los paquetes disponibles de tu Termux.
2. Ejecuta `./build_termux.sh`.
3. El APK de debug quedará en `app/build/outputs/apk/debug/app-debug.apk`.

## Nota de producto
La API de MEXC conecta al entorno live y no debe considerarse sandbox. Por eso este build mantiene la ejecución real fuera de la aplicación y prioriza Paper + Quant Lab.


## v12
Consulta `D5_IA_V12_CHANGELOG.md` y `ARCHITECTURE_V12.md` para la capa de memoria contextual, aprendizaje acotado, clasificación de errores y validación walk-forward.

## v13
- MEXC Futures WebSocket live stream for ticker, trades, depth and 1m kline.
- Microstructure/order-flow engine.
- Probability calibration from completed Paper outcomes.
- Pattern fingerprints and stronger error memory.
- Learning Governor to reject unstable self-modifications.
