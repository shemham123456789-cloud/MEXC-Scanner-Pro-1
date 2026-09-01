# D.5 IA v12 architecture

Market data -> MTF context -> regime -> zones/location -> raw engines -> adaptive selector -> flexible decision -> entry/SL/TP -> paper/OOS outcome -> error classifier -> bounded memory -> future weighting.

### Anti-overfit governor
1. Minimum sample threshold.
2. Jeffreys-style smoothing.
3. Bounded context adjustment (-15% to +15%).
4. Strategy reliability bounded to 15%-85%.
5. Causal walk-forward folds with purge gap.
6. OOS profit factor and stability checks.
7. Monte Carlo distribution.
8. No current-bar self-learning.

### Decision philosophy
D.5 IA does not require every indicator to agree. It requires a strong directional engine plus meaningful location, MTF evidence or order-flow evidence, a minimum decision margin, and acceptable R:R.
