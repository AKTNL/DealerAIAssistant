# Sample Workbook Missing-Value Profile

## Source

- Workbook: `backend/src/main/resources/Sample Data.xlsx`
- Analysis method: read-only import with the bundled spreadsheet runtime; blank counts were computed from populated rows after header detection.

## Findings

| Sheet | Rows | Important missing fields |
|---|---:|---|
| AE Target Data | 5,088 | `AaKTarget__c`: 3; `DealerGroupName__c`: 60 |
| Opportunity | 6,198 | `LeadSource`: 12; `Model__c`: 4; campaign linkage fields: 2,588 |
| Lead | 1,898 | dealer identity: 438; `LeadSource`: 18; vehicle/model fields: 518-866; `CreatedDate`: 115 |
| Task | 57,582 | `Subject`: 3 |
| Campaign | 715 | `Target_Opportunity_Amount__c`: 421; `Target_Order_Amount__c`: 421; `NewCustomerCount__c`: 559; `CampaignType__c`: 2; dealer identity: 3; `Product_Model__c`: 520 |

## Implications

- The three target rows missing `AaKTarget__c` still contain valid observed create/win counts. Retain them, but exclude them from target-achievement rate cohorts so the denominator is never fabricated and observed totals are not lost.
- Campaign target gaps are too common to reject whole campaign rows. Campaigns must remain available for counts and dimensions, while each rate calculation excludes records whose required denominator is unavailable.
- Missing opportunity/lead model and source fields are optional categorical gaps and should map to an explicit unknown bucket.
- Missing dealer identity on leads/campaigns can remain unassigned for global analysis but must not participate in dealer rankings.
- Missing optional dates must be excluded from analyses that require those dates rather than filled with a guessed date. The workbook's opportunity expected-close date is unavailable, so rejecting on that field drops all 6,198 otherwise usable opportunities and breaks task-to-dealer linkage.

## Recommended Representation

- Required analytical rows: reject with reason when the primary identifier or core observed fact is missing. Optional metric-specific denominators remain nullable.
- Optional categorical gaps: normalize to `未知` or `未分配` and count the normalization.
- Campaign target denominators: retain nullable values and make all campaign aggregation paths null-safe; exclude unavailable denominators from attainment samples.
- Observed count gaps: use zero only for fields whose source contract explicitly defines blank as no observed event; otherwise retain unavailable state.
- Comparable-rate cohorts: sum a numerator only from rows where its paired denominator is available; report broader observed totals separately.
- Import report: expose source mode, fallback status, sheet totals, imported rows, normalized fields, rejected rows, and reason counts.

## Corrected Accuracy Baselines

The regression workbook previously paired all observed actuals with only the rows that had target denominators. The corrected comparable-cohort values are:

| Question | Observed total | Comparable cohort | Corrected rate |
|---|---:|---:|---:|
| Highest target achievement dealer (AG) | 81 wins | 79 / 47 | 168.1% |
| Aurora S target achievement | 4,175 wins | 4,173 / 3,708 | 112.5% |
| All campaign opportunity attainment | 3,146 actual | 282 / 4,746 | 5.9% |
| All campaign order attainment | 293 wins | 10 / 348 | 2.9% |
| Dealer AI campaign opportunity attainment | 1,855 actual | 29 / 355 | 8.2% |
| Dealer C campaign opportunity attainment | 939 actual | 53 / 259 | 20.5% |

The user-facing response and metrics API must expose both observed totals and comparable numerators whenever they differ, so a displayed rate can be audited without implying that the broader observed total was divided by a partial denominator.
