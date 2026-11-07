# BagFee (ICT602)

Android app (Java) that estimates the excess baggage fee for air travel.

- Inputs: baggage weight (kg), ticket class (Economy 20 kg / Business 40 kg), rate per kg (RM)
- Outputs: excess weight, excess baggage charge, total payable (charge + 8% service tax)
- Toolbar: Share (shares the app URL) and About
- About page: developer details, copyright, clickable website URL

## Test cases
| Weight | Class | Rate | Excess | Charge | Total |
|---|---|---|---|---|---|
| 25 | Economy | 15 | 5 kg | RM 75.00 | RM 81.00 |
| 18 | Economy | 15 | 0 kg | RM 0.00 | RM 0.00 |
| 55 | Business | 12 | 15 kg | RM 180.00 | RM 194.40 |
| 42.5 | Business | 12 | 2.5 kg | RM 30.00 | RM 32.40 |

Author: Muhammad Ammar bin Abd Razak - UiTM
