# Arquitectura JARVIS PRO

## Pipeline de decisión
Datos MEXC -> normalización -> velas MTF -> indicadores -> zonas/estructura -> 6 motores -> clasificador de régimen -> ensemble ponderado -> filtro MTF -> risk mode -> Entry/SL/TP/RR -> estado de señal.

## Antisobreajuste
- No hay pesos neuronales aleatorios usados para producir una confianza falsa.
- El backtest usa solo datos anteriores a cada señal.
- El resultado se separa en tramo de entrenamiento y OOS.
- Los empates SL/TP en una misma vela cuentan como pérdida por criterio conservador.
- Monte Carlo reordena rendimientos observados para estimar la dispersión de resultados.
- El detector de sobreajuste marca degradación fuerte de OOS respecto al tramo inicial.

## Escalabilidad
El scanner no depende de un enum fijo para descubrir mercados: consulta el catálogo de contratos Futures y construye el universo dinámico USDT.
