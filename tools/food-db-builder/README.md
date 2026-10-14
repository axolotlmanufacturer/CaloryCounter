# food-db-builder

Builds `foods.db`, the read-only food database of the app (PROMPT.md §5.1), from

* **BLS 4.0** (Bundeslebensmittelschlüssel, Max Rubner-Institut, 2025; free under CC BY 4.0, no registration):
  7,140 generic German foods. Download: <https://blsdb.de/download> (`BLS_4_0_2025_DE.zip`), put the unpacked
  folder into `data/raw/bls/`. Cite as: *Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0 –
  Deutsche Nährstoffdatenbank. Karlsruhe. DOI 10.25826/Data20251217-134202-0.*
* **Open Food Facts**, Germany (products sold in Germany, ODbL): branded products with barcodes. The Parquet export on
  Hugging Face is not reachable from the cloud environment, so the pipeline uses OFF's official S3 exports:
  * `en.openfoodfacts.org.products.csv.gz` (1.28 GB): tab separated, nutrients per 100 g/ml, countries, scan counts
  * `openfoodfacts-mobile-dump-products.tsv.gz` (152 MB): per-language product names (the CSV has no `product_name_de`)

  Download (into `data/raw/off/`, resumable):

  ```bash
  cd data/raw/off
  B=https://openfoodfacts-ds.s3.eu-west-3.amazonaws.com
  curl -L -C - -O $B/en.openfoodfacts.org.products.csv.gz
  curl -L -C - -O $B/openfoodfacts-mobile-dump-products.tsv.gz
  echo 2026-10-13 > export_date.txt        # date of the export, shown in "Über die App"
  ```

## Rebuilding

```bash
cd tools/food-db-builder
uv venv && uv pip install -r requirements.txt
.venv/bin/python build_food_db.py --sample 3000   # small development database  -> out/foods-sample.db (committed)
.venv/bin/python build_food_db.py                 # the full database            -> out/foods-full.db   (not committed)
.venv/bin/pytest                                  # builder tests
```

Both runs write a report (`out/*.report.txt`: rows in and out per filter step, counts, size per table). The build
takes about 90 seconds. The app build copies the chosen file into the APK: `./gradlew assembleDebug` uses the
sample, `./gradlew assembleRelease -PfoodDb=full` the full database.

The tables are created from the `createSql` of Room's exported schema (`app/schemas/…/FoodDatabase/<version>.json`),
including Room's identity hash, so `createFromAsset` accepts the file. **Changing an entity in `data/fooddb/` means:
bump `FoodDatabase.VERSION`, rebuild, commit the new schema file and the sample database.** The search index is built with
`INSERT INTO foods_fts(foods_fts) VALUES('rebuild')`, then `ANALYZE` and `VACUUM`.

## Database

`foods` (one row per food, nutrients per 100 g or 100 ml, unknown = NULL), `foods_fts` (FTS4 over `search_text`, prefix
indexes for 2-3 letters), `food_portions`, `food_db_meta` (build date, sources, counts). Ids: BLS foods first, then Open
Food Facts foods by falling scan count (then shorter name, then barcode), so `ORDER BY id` is the relevance order and needs
no sorting. `source_id` is the stable key (BLS code / barcode) that the diary and favorites refer to, not the id.

`search_text` = `normalize(name + " " + brands)`. `normalize.py` and `TextNormalizer` in `:domain` implement the same rules
(PROMPT.md §5.2) and are tested against the shared `normalize_cases.json`.

### BLS column mapping (all per 100 g)

| BLS code | Meaning | Column |
|---|---|---|
| `ENERCC` | Energie (kcal) | `kcal` |
| `PROT625` | Protein (N × 6,25) | `protein` |
| `CHO` | Kohlenhydrate, verfügbar | `carbs` |
| `SUGAR` | Zucker (Mono- und Disaccharide), gesamt | `sugar` |
| `FAT` | Fett | `fat` |
| `FIBT` | Ballaststoffe, gesamt | `fiber` |
| `NACL` | Salz (Natriumchlorid) | `salt` (if missing: `NA`, Natrium in mg, × 2,5 / 1000) |

Cells: a number is used as is; `-` (not determined) becomes NULL; `<LOD`, `<LOQ`, `<LOD or <LOQ` (below detection) and
`TR` (trace) become 0. BLS foods are per 100 g (the BLS gives no density for drinks).

### Open Food Facts mapping and filters

Columns: `energy-kcal_100g` (else `energy-kj_100g` / 4,184), `proteins_100g`, `carbohydrates_100g`, `sugars_100g`,
`fat_100g`, `fiber_100g`, `salt_100g` (else `sodium_100g` × 2,5), `brands` (the first brand is shown, all are searchable),
`serving_quantity` / `serving_size` (portion), `unique_scans_n` (popularity). Name: the German name from the mobile dump,
else the CSV's `product_name`. Drinks (quantity in ml, cl, dl or l) are per 100 ml, everything else per 100 g.

Filters, in this order, each counted in the report (numbers of the 2026-10-13 export):

| Step | Rows after |
|---|---:|
| `countries_tags` contains `en:germany` (of 4,535,553) | 408,727 |
| valid barcode: 8, 12, 13 or 14 digits with a correct check digit | 402,433 |
| has a name (at least one letter) | 387,041 |
| has an energy value | 226,206 |
| sane values: kcal 0–900, every nutrient 0–100 g, protein + carbs + fat ≤ 105 g | 225,705 |
| kcal and kJ do not contradict each other (more than 50 kcal and 30 % apart: often kJ typed into the kcal field) | 222,187 |
| kcal fits 4/4/9 kcal per g (+ 7 per g alcohol); only egregious mismatches (> 80 kcal and > 50 %) are dropped | 221,731 |
| one row per barcode (leading zeros ignored), the most recently modified wins | 221,731 |

Result: **228,871 foods (7,140 BLS + 221,731 OFF), 59 MB** (budget: 120 MB). Rows with an energy value but without macros are
kept; their missing values stay NULL and show as "–".

## Typical portions (`portions_de.csv`) – estimates, please review

`food_portions` holds typical portion weights ("Stück (mittel)", "Scheibe", "Esslöffel", "Glas (0,2 l)" …). They are **my
estimates** for common BLS foods, not data from a source, and every row is stored with `estimated = 1`; the app shows them
with "ca.". Correct a weight in `portions_de.csv`, rebuild, done. The portion of an Open Food Facts product is not an
estimate: it comes from the product's own `serving_quantity`.

| BLS-Code | Lebensmittel (BLS) | Portion | Gramm (geschätzt) |
|---|---|---|---:|
| F110100 | Apfel roh | Stück (klein) | 100 |
| F110100 | Apfel roh | Stück (mittel) | 150 |
| F110100 | Apfel roh | Stück (groß) | 200 |
| F503100 | Banane roh | Stück (mittel) | 120 |
| F130100 | Birne roh | Stück (mittel) | 170 |
| F603100 | Orange roh | Stück (mittel) | 160 |
| F606100 | Mandarine roh | Stück (mittel) | 70 |
| F514100 | Kiwi roh | Stück (mittel) | 75 |
| F301100 | Erdbeere roh | Stück (mittel) | 15 |
| F310100 | Weintraube roh | Handvoll | 80 |
| F203100 | Pfirsich roh | Stück (mittel) | 140 |
| F220100 | Pflaume roh | Stück (mittel) | 40 |
| F211100 | Süßkirsche roh | Handvoll | 80 |
| F535100 | Wassermelone roh | Scheibe | 250 |
| F501100 | Ananas roh | Scheibe | 80 |
| F502100 | Avocado roh | Stück (mittel) | 150 |
| F304100 | Heidelbeere roh | Handvoll | 50 |
| F302100 | Himbeere roh | Handvoll | 50 |
| G561100 | Tomate roh | Stück (mittel) | 100 |
| G561100 | Tomate roh | Stück (klein / Cocktailtomate) | 15 |
| G520100 | Gurke roh | Stück (mittel) | 300 |
| G620100 | Karotte/Möhre, roh | Stück (mittel) | 70 |
| G480100 | Speisezwiebel roh | Stück (mittel) | 60 |
| K110100 | Kartoffel geschält, roh | Stück (mittel) | 90 |
| G582100 | Zucchini roh | Stück (mittel) | 250 |
| G510100 | Aubergine roh | Stück (mittel) | 250 |
| K701100 | Champignon roh | Stück (mittel) | 15 |
| G490100 | Knoblauch roh | Zehe | 3 |
| G312100 | Broccoli roh | Röschen | 25 |
| E111100 | Hühnerei roh | Stück (Größe M) | 55 |
| E111132 | Hühnerei gekocht | Stück (Größe M) | 50 |
| Y710142 | Spiegelei gebraten | Stück | 60 |
| M111300 | Vollmilch frisch, 3,5 % Fett, pasteurisiert | Glas (0,2 l) | 200 |
| M111300 | Vollmilch frisch, 3,5 % Fett, pasteurisiert | Tasse | 150 |
| M113300 | H-Vollmilch 3,5 % Fett, ultrahocherhitzt | Glas (0,2 l) | 200 |
| M111200 | Milch fettarm, frisch, 1,5 % Fett, pasteurisiert | Glas (0,2 l) | 200 |
| M141300 | Joghurt mild, mind. 3,5 % Fett | Becher | 150 |
| M141300 | Joghurt mild, mind. 3,5 % Fett | Esslöffel | 15 |
| M713100 | Speisequark Magerstufe, Magerquark < 10 % Fett i. Tr. | Esslöffel | 20 |
| M713100 | Speisequark Magerstufe, Magerquark < 10 % Fett i. Tr. | Becher | 250 |
| Q611000 | Butter mild gesäuert | Portion (aufs Brot) | 10 |
| Q611000 | Butter mild gesäuert | Teelöffel | 5 |
| M402600 | Gouda 48 % Fett i. Tr. | Scheibe | 25 |
| M401600 | Edamer mind. 45 % Fett i. Tr. | Scheibe | 25 |
| M602700 | Camembert mind. 50 % Fett i. Tr. | Ecke | 30 |
| M032100 | Mozzarella mind. 45 % Fett i. Tr. | Kugel | 125 |
| M710700 | Frischkäsezubereitung Natur, mind. 50 % Fett i. Tr. | Portion (aufs Brot) | 20 |
| M173900 | Schlagsahne mind. 36 % Fett | Esslöffel | 15 |
| M171500 | Kaffeesahne mind. 10 % Fett | Portion (Kaffeesahne) | 10 |
| B511000 | Weizenbrötchen | Stück | 50 |
| B401000 | Vollkornbrötchen | Stück | 60 |
| B271000 | Roggenmischbrot | Scheibe | 45 |
| B121000 | Roggenvollkornbrot | Scheibe | 45 |
| B311000 | Weizenbrot/Weißbrot | Scheibe | 30 |
| B314000 | Weizentoastbrot/Buttertoastbrot | Scheibe | 25 |
| B6A2100 | Roggenvollkornknäckebrot | Scheibe | 10 |
| D771600 | Croissant (Hefeblätterteig/Plunderteig) | Stück | 60 |
| C133000 | Hafer Flocken | Esslöffel | 10 |
| C133000 | Hafer Flocken | Portion (Müsli) | 40 |
| C512300 | Müslimischung mit Trockenfrüchten, ungesüßt | Portion | 50 |
| C515400 | Cornflakes ungesüßt | Portion | 30 |
| C352000 | Reis poliert, roh | Portion (roh) | 75 |
| X820162 | Reis gekocht | Portion (gekocht) | 180 |
| E401000 | Teigwaren eifrei, roh | Portion (roh) | 80 |
| E401032 | Teigwaren eifrei, gekocht | Portion (gekocht) | 200 |
| C214100 | Weizen Mehl, Type 405 | Esslöffel | 12 |
| S111000 | Zucker weiß (Raffinadezucker/Weißzucker) | Teelöffel | 5 |
| S111000 | Zucker weiß (Raffinadezucker/Weißzucker) | Esslöffel | 15 |
| S120000 | Honig | Teelöffel | 7 |
| S120000 | Honig | Esslöffel | 20 |
| S132000 | Konfitüre extra | Portion (aufs Brot) | 20 |
| S145000 | Nuss-Nougat-Creme | Portion (aufs Brot) | 20 |
| Q120000 | Olivenöl | Esslöffel | 10 |
| Q120000 | Olivenöl | Teelöffel | 4 |
| Q320000 | Sonnenblumenöl | Esslöffel | 10 |
| S539900 | Vollmilchschokolade | Stück (Rippe) | 5 |
| S539900 | Vollmilchschokolade | Tafel | 100 |
| S560000 | Zartbitter-/Halbbitterschokolade | Stück (Rippe) | 5 |
| S560000 | Zartbitter-/Halbbitterschokolade | Tafel | 100 |
| D630400 | Butterkekse/Butterplätzchen (Mürbeteig) | Stück | 4 |
| K280100 | Kartoffelchips/Stapelchips, diverse Sorten | Portion | 30 |
| S241000 | Vanilleeis | Kugel | 50 |
| H110600 | Erdnuss geröstet | Handvoll | 30 |
| H210100 | Mandel süß | Handvoll | 30 |
| H120100 | Walnuss | Handvoll | 30 |
| H130100 | Haselnuss | Handvoll | 30 |
| H170100 | Cashewkern | Handvoll | 30 |
| W140000 | Salami | Scheibe | 6 |
| W424000 | Schwein Kochschinken, Kochpökelware | Scheibe | 20 |
| W211200 | Wiener Würstchen | Stück | 50 |
| W310000 | Delikatess Leberwurst/Feine Leberwurst/Gutsleberwurst | Portion (aufs Brot) | 20 |
| V416100 | Hähnchen Brustfilet, roh | Stück (Filet) | 150 |
| T410100 | Lachs roh | Stück (Filet) | 150 |
| H861000 | Tofu | Portion | 100 |
| N330000 | Colagetränk koffeinhaltig | Glas (0,2 l) | 200 |
| N330000 | Colagetränk koffeinhaltig | Dose (0,33 l) | 330 |
| N330000 | Colagetränk koffeinhaltig | Flasche (0,5 l) | 500 |
| P163000 | Pilsner Bier | Glas (0,3 l) | 300 |
| P163000 | Pilsner Bier | Flasche (0,33 l) | 330 |
| P163000 | Pilsner Bier | Flasche (0,5 l) | 500 |
| P2A3000 | Rotwein trocken | Glas (0,125 l) | 125 |
| P210000 | Weißwein trocken | Glas (0,125 l) | 125 |
| F110600 | Apfelsaft | Glas (0,2 l) | 200 |
| F603600 | Orangensaft | Glas (0,2 l) | 200 |
| N410100 | Kaffee (Getränk) | Tasse | 150 |
| N414000 | Cappuccino (Getränk) mit Milch 3,5 % Fett | Tasse | 150 |
| N411100 | Espresso (Getränk) | Tasse (Espresso) | 30 |
| N630000 | Schwarztee (Getränk) | Tasse | 200 |
