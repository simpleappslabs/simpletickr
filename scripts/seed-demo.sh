#!/usr/bin/env bash
set -euo pipefail

API="${API:-http://localhost:8080}"

# d N  →  date N days before today
d() { date -d "$1 days ago" +%Y-%m-%d; }

post() {
    curl -sf -X POST "$API$1" -H "Content-Type: application/json" -d "$2"
}

echo "Creating assets..."

# Create an asset and return its first listing's id.
# If an asset with the same ticker already exists, return its listing id.
make_asset() {
    local ticker
    ticker=$(echo "$1" | jq -r '.listings[0].ticker')
    local existing
    existing=$(curl -sf "$API/assets" | jq -r --arg t "$ticker" '.[] | .listings[] | select(.ticker == $t) | .id' | head -1)
    if [[ -n "$existing" ]]; then
        echo "$existing"
    else
        post /assets "$1" | jq -r '.listings[0].id'
    fi
}

# Create a portfolio and return its id.
# If a portfolio with the same name already exists, return its id.
make_portfolio() {
    local name="$1" body="$2"
    local existing
    existing=$(curl -sf "$API/portfolios" | jq -r --arg n "$name" '.[] | select(.name == $n) | .id' | head -1)
    if [[ -n "$existing" ]]; then
        echo "$existing"
    else
        post /portfolios "$body" | jq -r '.id'
    fi
}

# Create an account and return its id.
# If an account with the same name already exists, return its id.
make_account() {
    local name="$1" body="$2"
    local existing
    existing=$(curl -sf "$API/accounts" | jq -r --arg n "$name" '.[] | select(.name == $n) | .id' | head -1)
    if [[ -n "$existing" ]]; then
        echo "$existing"
    else
        post /accounts "$body" | jq -r '.id'
    fi
}

AAPL=$(make_asset '{"name":"Apple Inc.","type":"STOCK","listings":[{"exchange":"NASDAQ","ticker":"AAPL","currency":"USD","priceMappings":[{"provider":"YAHOO","externalId":"AAPL"}]}]}')
MSFT=$(make_asset '{"name":"Microsoft Corporation","type":"STOCK","listings":[{"exchange":"NASDAQ","ticker":"MSFT","currency":"USD","priceMappings":[{"provider":"YAHOO","externalId":"MSFT"}]}]}')
GOOGL=$(make_asset '{"name":"Alphabet Inc.","type":"STOCK","listings":[{"exchange":"NASDAQ","ticker":"GOOGL","currency":"USD","priceMappings":[{"provider":"YAHOO","externalId":"GOOGL"}]}]}')
AMZN=$(make_asset '{"name":"Amazon.com Inc.","type":"STOCK","listings":[{"exchange":"NASDAQ","ticker":"AMZN","currency":"USD","priceMappings":[{"provider":"YAHOO","externalId":"AMZN"}]}]}')
NVDA=$(make_asset '{"name":"NVIDIA Corporation","type":"STOCK","listings":[{"exchange":"NASDAQ","ticker":"NVDA","currency":"USD","priceMappings":[{"provider":"YAHOO","externalId":"NVDA"}]}]}')
META=$(make_asset '{"name":"Meta Platforms Inc.","type":"STOCK","listings":[{"exchange":"NASDAQ","ticker":"META","currency":"USD","priceMappings":[{"provider":"YAHOO","externalId":"META"}]}]}')
SPY=$(make_asset '{"name":"SPDR S&P 500 ETF Trust","type":"ETF","listings":[{"exchange":"NYSE Arca","ticker":"SPY","currency":"USD","priceMappings":[{"provider":"YAHOO","externalId":"SPY"}]}]}')
QQQ=$(make_asset '{"name":"Invesco QQQ Trust","type":"ETF","listings":[{"exchange":"NASDAQ","ticker":"QQQ","currency":"USD","priceMappings":[{"provider":"YAHOO","externalId":"QQQ"}]}]}')
VTI=$(make_asset '{"name":"Vanguard Total Stock Market ETF","type":"ETF","listings":[{"exchange":"NYSE Arca","ticker":"VTI","currency":"USD","priceMappings":[{"provider":"YAHOO","externalId":"VTI"}]}]}')
VWCE=$(make_asset '{"name":"Vanguard FTSE All-World UCITS ETF","type":"ETF","listings":[{"exchange":"Euronext Amsterdam","ticker":"VWCE","currency":"EUR","priceMappings":[{"provider":"YAHOO","externalId":"VWCE.AS"}]}]}')
BTC=$(make_asset '{"name":"Bitcoin","type":"CRYPTO","listings":[{"ticker":"BTC","currency":"EUR","priceMappings":[{"provider":"YAHOO","externalId":"BTC-EUR"}]}]}')
ETH=$(make_asset '{"name":"Ethereum","type":"CRYPTO","listings":[{"ticker":"ETH","currency":"EUR","priceMappings":[{"provider":"YAHOO","externalId":"ETH-EUR"}]}]}')

echo "Creating accounts..."
ACC_DEFAULT=$(make_account "Default" '{"name":"Default","accountType":"BROKERAGE"}')
ACC_BINANCE=$(make_account "Binance" '{"name":"Binance","accountType":"CRYPTO","broker":"Binance"}')
ACC_LEDGER=$(make_account "Ledger Wallet" '{"name":"Ledger Wallet","accountType":"CRYPTO"}')

txn() {
    local portfolio_id=$1 listing_id=$2 type=$3 qty=$4 price=$5 date=$6 fx_rate=${7:-} account_id=${8:-$ACC_DEFAULT}
    local body="{\"listingId\":$listing_id,\"type\":\"$type\",\"quantity\":$qty,\"price\":$price,\"date\":\"$date\",\"accountId\":$account_id"
    [[ -n "$fx_rate" ]] && body="$body,\"fxRate\":$fx_rate"
    body="$body}"
    post "/portfolios/$portfolio_id/transactions" "$body" > /dev/null
    echo "    $type $qty @ $price on $date${fx_rate:+ (fx=$fx_rate)}"
}

trade() {
    local portfolio_id=$1 sell_id=$2 sell_qty=$3 sell_price=$4 buy_id=$5 buy_qty=$6 buy_price=$7 date=$8 fees=${9:-} account_id=${10:-$ACC_BINANCE}
    local body="{\"sellListingId\":$sell_id,\"sellQuantity\":$sell_qty,\"sellPrice\":$sell_price,\"buyListingId\":$buy_id,\"buyQuantity\":$buy_qty,\"buyPrice\":$buy_price,\"date\":\"$date\",\"accountId\":$account_id"
    [[ -n "$fees" ]] && body="$body,\"fees\":$fees"
    body="$body}"
    post "/portfolios/$portfolio_id/transactions/trade" "$body" > /dev/null
    echo "    SWAP $sell_qty (sell @ $sell_price) → $buy_qty (buy @ $buy_price) on $date"
}

# Move a quantity of an asset from one account to another (optionally across portfolios),
# preserving cost basis rather than recognizing a disposal. fee_qty is deducted, in the
# transferred asset itself, from the quantity landing at the destination (e.g. network gas).
transfer() {
    local portfolio_id=$1 listing_id=$2 qty=$3 date=$4 from_account=$5 to_account=$6 fee_qty=${7:-} dest_portfolio_id=${8:-$1}
    local body="{\"listingId\":$listing_id,\"quantity\":$qty,\"date\":\"$date\",\"sourceAccountId\":$from_account,\"destinationAccountId\":$to_account,\"destinationPortfolioId\":$dest_portfolio_id"
    [[ -n "$fee_qty" ]] && body="$body,\"assetFeeQuantity\":$fee_qty"
    body="$body}"
    post "/portfolios/$portfolio_id/transactions/transfer" "$body" > /dev/null
    echo "    TRANSFER $qty on $date${fee_qty:+ (fee=$fee_qty)}"
}

# ─── Portfolio 1: Bogleheads Three-Fund ──────────────────────────────────────
echo ""
echo "Creating Bogleheads Three-Fund portfolio..."
P_BH=$(make_portfolio "Bogleheads Three-Fund (demo)" '{"name":"Bogleheads Three-Fund (demo)"}')
echo "  id=$P_BH"

echo "  VTI — quarterly DCA"
#                                                                    EUR/USD
txn "$P_BH" "$VTI" BUY 10 180.00 "$(d 1259)"  # ~3.5yr ago         1.0762
txn "$P_BH" "$VTI" BUY 10 192.00 "$(d 1169)"  # ~3yr 2mo           1.0986
txn "$P_BH" "$VTI" BUY 10 198.00 "$(d 1078)"  # ~3yr               1.1241
txn "$P_BH" "$VTI" BUY 10 205.00 "$(d  986)"  # ~2yr 9mo           1.0593
txn "$P_BH" "$VTI" BUY 10 215.00 "$(d  894)"  # ~2yr 6mo           1.0949
txn "$P_BH" "$VTI" BUY 10 222.00 "$(d  803)"  # ~2yr 2mo           1.0642
txn "$P_BH" "$VTI" BUY 10 230.00 "$(d  712)"  # ~2yr               1.0821
txn "$P_BH" "$VTI" BUY 10 238.00 "$(d  620)"  # ~20mo              1.0934
txn "$P_BH" "$VTI" BUY 10 245.00 "$(d  528)"  # ~17mo              1.0298
txn "$P_BH" "$VTI" BUY 10 252.00 "$(d  438)"  # ~14mo              1.0567
txn "$P_BH" "$VTI" BUY 10 258.00 "$(d  347)"  # ~11mo              1.0721
txn "$P_BH" "$VTI" BUY 10 262.00 "$(d  255)"  # ~8mo               1.0831
txn "$P_BH" "$VTI" BUY 10 268.00 "$(d  165)"  # ~5mo               1.0412
txn "$P_BH" "$VTI" BUY 10 245.00 "$(d   80)"  # ~3mo tariff dip    1.0952
txn "$P_BH" "$VTI" BUY 10 274.00 "$(d   10)"  # last week          1.0821

echo "  VWCE — semi-annual DCA"
txn "$P_BH" "$VWCE" BUY 25  82.00 "$(d 1259)"  # ~3.5yr ago
txn "$P_BH" "$VWCE" BUY 25  90.00 "$(d 1078)"  # ~3yr
txn "$P_BH" "$VWCE" BUY 25  94.00 "$(d  894)"  # ~2yr 6mo
txn "$P_BH" "$VWCE" BUY 25  98.00 "$(d  712)"  # ~2yr
txn "$P_BH" "$VWCE" BUY 25 102.00 "$(d  528)"  # ~17mo
txn "$P_BH" "$VWCE" BUY 25 108.00 "$(d  347)"  # ~11mo
txn "$P_BH" "$VWCE" BUY 25 114.00 "$(d  165)"  # ~5mo
txn "$P_BH" "$VWCE" BUY 25 118.00 "$(d   10)"  # last week

echo "  SPY — annual top-ups"
txn "$P_BH" "$SPY" BUY 5 395.00 "$(d 1259)"  # ~3.5yr ago          1.0762
txn "$P_BH" "$SPY" BUY 5 440.00 "$(d  894)"  # ~2yr 6mo            1.0949
txn "$P_BH" "$SPY" BUY 5 480.00 "$(d  528)"  # ~17mo               1.0298
txn "$P_BH" "$SPY" BUY 5 518.00 "$(d  165)"  # ~5mo                1.0412

echo "  Done."

# ─── Portfolio 2: Active Trader ───────────────────────────────────────────────
echo ""
echo "Creating Active Trader portfolio..."
P_AT=$(make_portfolio "Active Trader (demo)" '{"name":"Active Trader (demo)"}')
echo "  id=$P_AT"

echo "  NVDA — post-split prices from ~2yr ago onwards"
#                                                                    EUR/USD
txn "$P_AT" "$NVDA" BUY  10  150.00 "$(d 1264)"  # ~3.5yr           1.0762
txn "$P_AT" "$NVDA" BUY  10  265.00 "$(d 1124)"  # ~3yr 1mo         1.0741
txn "$P_AT" "$NVDA" SELL  5  435.00 "$(d 1037)"  # ~2yr 10mo        1.0823
txn "$P_AT" "$NVDA" BUY   5  450.00 "$(d  989)"  # ~2yr 9mo         1.0593
txn "$P_AT" "$NVDA" SELL  5  615.00 "$(d  850)"  # ~2yr 4mo         1.0823
txn "$P_AT" "$NVDA" BUY  10  800.00 "$(d  796)"  # ~2yr 2mo         1.0642
txn "$P_AT" "$NVDA" SELL 10  900.00 "$(d  752)"  # ~2yr             1.0821
txn "$P_AT" "$NVDA" BUY  25  116.00 "$(d  707)"  # ~23mo            1.0901
txn "$P_AT" "$NVDA" BUY  25  126.00 "$(d  630)"  # ~21mo            1.0934
txn "$P_AT" "$NVDA" SELL 15  148.00 "$(d  497)"  # ~16mo            1.0412
txn "$P_AT" "$NVDA" BUY  20  108.00 "$(d  443)"  # ~15mo            1.0891
txn "$P_AT" "$NVDA" BUY  15  118.00 "$(d  377)"  # ~12mo            1.1283
txn "$P_AT" "$NVDA" SELL 10  135.00 "$(d  311)"  # ~10mo            1.1082
txn "$P_AT" "$NVDA" BUY  20  142.00 "$(d  269)"  # ~9mo             1.0981
txn "$P_AT" "$NVDA" SELL 10  138.00 "$(d  224)"  # ~7mo             1.0562
txn "$P_AT" "$NVDA" BUY  15  128.00 "$(d  199)"  # ~6mo             1.0541
txn "$P_AT" "$NVDA" SELL 10  122.00 "$(d  158)"  # ~5mo             1.0412
txn "$P_AT" "$NVDA" BUY  20  112.00 "$(d  119)"  # ~4mo             1.0481
txn "$P_AT" "$NVDA" BUY  15   88.00 "$(d   80)"  # ~3mo tariff dip  1.0952
txn "$P_AT" "$NVDA" SELL 10  115.00 "$(d   43)"  # ~6wk             1.1281
txn "$P_AT" "$NVDA" BUY  10  128.00 "$(d    7)"  # last week        1.1354

echo "  AAPL"
txn "$P_AT" "$AAPL" BUY  15  130.00 "$(d 1254)"  # ~3.5yr           1.0762
txn "$P_AT" "$AAPL" BUY  10  185.00 "$(d 1108)"  # ~3yr             1.1241
txn "$P_AT" "$AAPL" SELL  5  190.00 "$(d 1065)"  # ~2yr 11mo        1.0931
txn "$P_AT" "$AAPL" BUY  10  170.00 "$(d  996)"  # ~2yr 9mo         1.0593
txn "$P_AT" "$AAPL" BUY  10  185.00 "$(d  877)"  # ~2yr 5mo         1.0844
txn "$P_AT" "$AAPL" SELL 15  215.00 "$(d  722)"  # ~2yr             1.0821
txn "$P_AT" "$AAPL" BUY  10  205.00 "$(d  676)"  # ~22mo            1.0901
txn "$P_AT" "$AAPL" BUY   5  235.00 "$(d  528)"  # ~17mo            1.0298
txn "$P_AT" "$AAPL" SELL  5  240.00 "$(d  382)"  # ~12mo            1.1287
txn "$P_AT" "$AAPL" BUY  10  215.00 "$(d  342)"  # ~11mo            1.1143
txn "$P_AT" "$AAPL" SELL  5  228.00 "$(d  285)"  # ~9mo             1.0934
txn "$P_AT" "$AAPL" BUY  10  232.00 "$(d  245)"  # ~8mo             1.0981
txn "$P_AT" "$AAPL" SELL  5  248.00 "$(d  208)"  # ~7mo             1.0541
txn "$P_AT" "$AAPL" BUY   5  238.00 "$(d  163)"  # ~5mo             1.0412
txn "$P_AT" "$AAPL" SELL  5  245.00 "$(d  132)"  # ~4mo             1.0481
txn "$P_AT" "$AAPL" BUY  10  218.00 "$(d  109)"  # ~3.5mo           1.0821
txn "$P_AT" "$AAPL" BUY  10  188.00 "$(d   80)"  # ~3mo tariff dip  1.0952
txn "$P_AT" "$AAPL" SELL  5  210.00 "$(d   38)"  # ~5wk             1.1281
txn "$P_AT" "$AAPL" BUY   5  205.00 "$(d   12)"  # ~2wk             1.1354

echo "  META — bought the dip, sold the rip"
txn "$P_AT" "$META" BUY  15  120.00 "$(d 1269)"  # ~3.5yr           1.0762
txn "$P_AT" "$META" BUY  10  175.00 "$(d 1169)"  # ~3yr 2mo         1.0986
txn "$P_AT" "$META" SELL 10  330.00 "$(d 1000)"  # ~2yr 9mo         1.0593
txn "$P_AT" "$META" BUY   8  355.00 "$(d  889)"  # ~2yr 5mo         1.0949
txn "$P_AT" "$META" SELL  8  495.00 "$(d  788)"  # ~2yr 2mo         1.0642
txn "$P_AT" "$META" BUY  10  480.00 "$(d  660)"  # ~22mo            1.0812
txn "$P_AT" "$META" SELL  5  620.00 "$(d  511)"  # ~17mo            1.0412
txn "$P_AT" "$META" BUY   8  520.00 "$(d  443)"  # ~15mo tariff dip 1.0891
txn "$P_AT" "$META" SELL  5  615.00 "$(d  387)"  # ~13mo            1.1283
txn "$P_AT" "$META" BUY  10  635.00 "$(d  330)"  # ~11mo            1.1082
txn "$P_AT" "$META" SELL  5  660.00 "$(d  269)"  # ~9mo             1.0981
txn "$P_AT" "$META" BUY   8  648.00 "$(d  219)"  # ~7mo             1.0562
txn "$P_AT" "$META" SELL  5  685.00 "$(d  168)"  # ~5mo             1.0412
txn "$P_AT" "$META" BUY  10  650.00 "$(d  127)"  # ~4mo             1.0481
txn "$P_AT" "$META" BUY  10  530.00 "$(d   80)"  # ~3mo tariff dip  1.0952
txn "$P_AT" "$META" SELL  8  650.00 "$(d   33)"  # ~5wk             1.1281
txn "$P_AT" "$META" BUY   5  658.00 "$(d    7)"  # last week        1.1354

echo "  GOOGL"
txn "$P_AT" "$GOOGL" BUY  15  89.00 "$(d 1259)"  # ~3.5yr           1.0762
txn "$P_AT" "$GOOGL" BUY  10 124.00 "$(d 1092)"  # ~3yr             1.1241
txn "$P_AT" "$GOOGL" SELL  8 140.00 "$(d  894)"  # ~2yr 6mo         1.0949
txn "$P_AT" "$GOOGL" BUY  10 160.00 "$(d  793)"  # ~2yr 2mo         1.0642
txn "$P_AT" "$GOOGL" BUY  10 175.00 "$(d  533)"  # ~17mo            1.0298
txn "$P_AT" "$GOOGL" SELL  5 195.00 "$(d  299)"  # ~10mo            1.0934
txn "$P_AT" "$GOOGL" BUY  10 180.00 "$(d  255)"  # ~8mo             1.0981
txn "$P_AT" "$GOOGL" SELL  5 198.00 "$(d  204)"  # ~7mo             1.0541
txn "$P_AT" "$GOOGL" BUY   8 192.00 "$(d  158)"  # ~5mo             1.0412
txn "$P_AT" "$GOOGL" SELL  5 198.00 "$(d  137)"  # ~4.5mo           1.0481
txn "$P_AT" "$GOOGL" BUY  10 162.00 "$(d  104)"  # ~3.5mo           1.0821
txn "$P_AT" "$GOOGL" BUY  10 148.00 "$(d   80)"  # ~3mo tariff dip  1.0952
txn "$P_AT" "$GOOGL" SELL  5 172.00 "$(d   38)"  # ~5wk             1.1281
txn "$P_AT" "$GOOGL" BUY  10 175.00 "$(d   12)"  # ~2wk             1.1354

echo "  AMZN"
txn "$P_AT" "$AMZN" BUY  15  94.00 "$(d 1242)"  # ~3.5yr            1.0762
txn "$P_AT" "$AMZN" BUY  10 130.00 "$(d 1047)"  # ~2yr 11mo         1.0823
txn "$P_AT" "$AMZN" SELL 10 180.00 "$(d  863)"  # ~2yr 5mo          1.0844
txn "$P_AT" "$AMZN" BUY  10 183.00 "$(d  787)"  # ~2yr 2mo          1.0751
txn "$P_AT" "$AMZN" SELL 10 225.00 "$(d  483)"  # ~16mo             1.0734
txn "$P_AT" "$AMZN" BUY  10 195.00 "$(d  413)"  # ~14mo             1.1289
txn "$P_AT" "$AMZN" SELL  5 218.00 "$(d  347)"  # ~11mo             1.1143
txn "$P_AT" "$AMZN" BUY  10 210.00 "$(d  280)"  # ~9mo              1.0934
txn "$P_AT" "$AMZN" SELL  5 225.00 "$(d  238)"  # ~8mo              1.0562
txn "$P_AT" "$AMZN" BUY  10 220.00 "$(d  189)"  # ~6mo              1.0541
txn "$P_AT" "$AMZN" SELL  5 228.00 "$(d  153)"  # ~5mo              1.0412
txn "$P_AT" "$AMZN" BUY  10 195.00 "$(d  118)"  # ~4mo              1.0821
txn "$P_AT" "$AMZN" BUY  15 175.00 "$(d   80)"  # ~3mo tariff dip   1.0952
txn "$P_AT" "$AMZN" SELL  5 210.00 "$(d   38)"  # ~5wk              1.1281
txn "$P_AT" "$AMZN" BUY   5 218.00 "$(d    7)"  # last week         1.1354

echo "  QQQ — broad index exposure"
txn "$P_AT" "$QQQ" BUY   5 280.00 "$(d 1269)"  # ~3.5yr             1.0762
txn "$P_AT" "$QQQ" BUY   5 355.00 "$(d 1088)"  # ~3yr               1.1241
txn "$P_AT" "$QQQ" BUY   5 410.00 "$(d  813)"  # ~2yr 3mo           1.0642
txn "$P_AT" "$QQQ" SELL  5 480.00 "$(d  569)"  # ~19mo              1.0534
txn "$P_AT" "$QQQ" BUY  10 460.00 "$(d  448)"  # ~15mo              1.0891
txn "$P_AT" "$QQQ" SELL  5 498.00 "$(d  382)"  # ~12mo              1.1283
txn "$P_AT" "$QQQ" BUY  10 502.00 "$(d  316)"  # ~10mo              1.1082
txn "$P_AT" "$QQQ" SELL  5 495.00 "$(d  260)"  # ~8mo               1.0981
txn "$P_AT" "$QQQ" BUY  10 510.00 "$(d  194)"  # ~6mo               1.0541
txn "$P_AT" "$QQQ" SELL  5 495.00 "$(d  168)"  # ~5mo               1.0412
txn "$P_AT" "$QQQ" BUY   5 488.00 "$(d  146)"  # ~5mo               1.0481
txn "$P_AT" "$QQQ" SELL  5 472.00 "$(d   99)"  # ~3mo               1.0821
txn "$P_AT" "$QQQ" BUY  15 428.00 "$(d   80)"  # ~3mo tariff dip    1.0952
txn "$P_AT" "$QQQ" SELL  5 498.00 "$(d   43)"  # ~6wk               1.1281
txn "$P_AT" "$QQQ" BUY   5 512.00 "$(d    7)"  # last week          1.1354

echo "  MSFT"
txn "$P_AT" "$MSFT" BUY  10 240.00 "$(d 1214)"  # ~3.5yr            1.0762
txn "$P_AT" "$MSFT" BUY   5 320.00 "$(d 1030)"  # ~2yr 10mo         1.0756
txn "$P_AT" "$MSFT" SELL  5 380.00 "$(d  884)"  # ~2yr 5mo          1.0949
txn "$P_AT" "$MSFT" BUY  10 410.00 "$(d  742)"  # ~2yr              1.0821
txn "$P_AT" "$MSFT" SELL  5 440.00 "$(d  523)"  # ~17mo             1.0298
txn "$P_AT" "$MSFT" BUY  10 388.00 "$(d  469)"  # ~15mo             1.0734
txn "$P_AT" "$MSFT" SELL  5 425.00 "$(d  403)"  # ~13mo             1.1289
txn "$P_AT" "$MSFT" BUY   8 458.00 "$(d  342)"  # ~11mo             1.1143
txn "$P_AT" "$MSFT" SELL  5 465.00 "$(d  290)"  # ~9mo              1.0934
txn "$P_AT" "$MSFT" BUY  10 448.00 "$(d  224)"  # ~7mo              1.0562
txn "$P_AT" "$MSFT" SELL  5 428.00 "$(d  168)"  # ~5mo              1.0412
txn "$P_AT" "$MSFT" BUY   5 402.00 "$(d  127)"  # ~4mo              1.0481
txn "$P_AT" "$MSFT" BUY  10 372.00 "$(d   80)"  # ~3mo tariff dip   1.0952
txn "$P_AT" "$MSFT" SELL  5 455.00 "$(d   33)"  # ~5wk              1.1281
txn "$P_AT" "$MSFT" BUY   5 462.00 "$(d   12)"  # ~2wk              1.1354

echo "  Done."

# ─── Portfolio 3: Crypto DCA ──────────────────────────────────────────────────
echo ""
echo "Creating Crypto DCA portfolio..."
P_CR=$(make_portfolio "Crypto DCA (demo)" '{"name":"Crypto DCA (demo)"}')
echo "  id=$P_CR"

echo "  BTC — systematic DCA with selective profit-taking"
txn "$P_CR" "$BTC" BUY   0.2  17000 "$(d 1264)" "" "$ACC_BINANCE"  # ~3.5yr           1.0762
txn "$P_CR" "$BTC" BUY   0.2  28000 "$(d 1174)" "" "$ACC_BINANCE"  # ~3yr 2mo         1.0986
txn "$P_CR" "$BTC" BUY   0.1  29500 "$(d 1083)" "" "$ACC_BINANCE"  # ~3yr             1.1241
txn "$P_CR" "$BTC" BUY   0.2  27000 "$(d  991)" "" "$ACC_BINANCE"  # ~2yr 9mo         1.0593
txn "$P_CR" "$BTC" BUY   0.1  42000 "$(d  894)" "" "$ACC_BINANCE"  # ~2yr 6mo         1.0949
txn "$P_CR" "$BTC" BUY   0.1  65000 "$(d  808)" "" "$ACC_BINANCE"  # ~2yr 2mo         1.0642
txn "$P_CR" "$BTC" SELL  0.3  68000 "$(d  768)" "" "$ACC_BINANCE"  # ~2yr             1.0821
txn "$P_CR" "$BTC" BUY   0.2  57000 "$(d  686)" "" "$ACC_BINANCE"  # ~22mo            1.0901
txn "$P_CR" "$BTC" BUY   0.1  95000 "$(d  594)" "" "$ACC_BINANCE"  # ~19mo            1.0534
txn "$P_CR" "$BTC" SELL  0.2  98000 "$(d  569)" "" "$ACC_BINANCE"  # ~18mo            1.0423
txn "$P_CR" "$BTC" BUY   0.2  85000 "$(d  502)" "" "$ACC_BINANCE"  # ~16mo            1.0412
txn "$P_CR" "$BTC" BUY   0.1  78000 "$(d  413)" "" "$ACC_BINANCE"  # ~13mo            1.1289
txn "$P_CR" "$BTC" BUY   0.1  82000 "$(d  347)" "" "$ACC_BINANCE"  # ~11mo            1.1143
txn "$P_CR" "$BTC" BUY   0.1  91000 "$(d  255)" "" "$ACC_BINANCE"  # ~8mo             1.0831
txn "$P_CR" "$BTC" SELL  0.2  96000 "$(d  200)" "" "$ACC_BINANCE"  # ~6.5mo           1.0541
txn "$P_CR" "$BTC" BUY   0.1  88000 "$(d  165)" "" "$ACC_BINANCE"  # ~5mo             1.0412
txn "$P_CR" "$BTC" BUY   0.3  72000 "$(d   80)" "" "$ACC_BINANCE"  # ~3mo tariff dip  1.0952
txn "$P_CR" "$BTC" BUY   0.1 102000 "$(d   15)" "" "$ACC_BINANCE"  # ~2wk             1.0821
txn "$P_CR" "$BTC" SELL  0.2 106000 "$(d   28)" "" "$ACC_BINANCE"  # ~1mo             1.1354

echo "  ETH"
txn "$P_CR" "$ETH" BUY   2.0  1200 "$(d 1264)" "" "$ACC_BINANCE"  # ~3.5yr            1.0762
txn "$P_CR" "$ETH" BUY   1.0  1800 "$(d 1174)" "" "$ACC_BINANCE"  # ~3yr 2mo          1.0986
txn "$P_CR" "$ETH" BUY   1.0  1900 "$(d 1083)" "" "$ACC_BINANCE"  # ~3yr              1.1241
txn "$P_CR" "$ETH" BUY   1.0  1600 "$(d  991)" "" "$ACC_BINANCE"  # ~2yr 9mo          1.0593
txn "$P_CR" "$ETH" BUY   0.5  2500 "$(d  899)" "" "$ACC_BINANCE"  # ~2yr 6mo          1.0949
txn "$P_CR" "$ETH" SELL  2.0  3500 "$(d  834)" "" "$ACC_BINANCE"  # ~2yr 4mo          1.0921
txn "$P_CR" "$ETH" BUY   1.0  3100 "$(d  747)" "" "$ACC_BINANCE"  # ~2yr              1.0821
txn "$P_CR" "$ETH" BUY   1.0  2400 "$(d  655)" "" "$ACC_BINANCE"  # ~21mo             1.0901
txn "$P_CR" "$ETH" SELL  1.0  3800 "$(d  584)" "" "$ACC_BINANCE"  # ~19mo             1.0534
txn "$P_CR" "$ETH" BUY   2.0  2800 "$(d  502)" "" "$ACC_BINANCE"  # ~16mo             1.0412
txn "$P_CR" "$ETH" BUY   1.0  2600 "$(d  347)" "" "$ACC_BINANCE"  # ~11mo             1.1143
txn "$P_CR" "$ETH" BUY   1.0  3200 "$(d  255)" "" "$ACC_BINANCE"  # ~8mo              1.0831
txn "$P_CR" "$ETH" SELL  2.0  3600 "$(d  200)" "" "$ACC_BINANCE"  # ~6.5mo            1.0541
txn "$P_CR" "$ETH" BUY   1.0  2900 "$(d  165)" "" "$ACC_BINANCE"  # ~5mo              1.0412
txn "$P_CR" "$ETH" BUY   3.0  1600 "$(d   80)" "" "$ACC_BINANCE"  # ~3mo tariff dip   1.0952
txn "$P_CR" "$ETH" BUY   1.0  2500 "$(d   15)" "" "$ACC_BINANCE"  # ~2wk              1.0821

echo "  BTC↔ETH swaps"
# ~2yr ago: rotate 0.1 BTC into ETH (ETH looked cheap vs BTC at ~16.8x ratio)
trade "$P_CR" "$BTC" 0.1  42000 "$ETH"  1.7  2500 "$(d  730)"
# ~16mo ago: swap 2 ETH back to BTC (BTC/ETH ratio tightened to ~19x)
trade "$P_CR" "$ETH" 2.0   3400 "$BTC"  0.1 68000 "$(d  480)"  5
# ~6mo ago: rotate 0.1 BTC into ETH (ratio widened to ~28x, ETH lagging)
trade "$P_CR" "$BTC" 0.1  88000 "$ETH"  2.8  3100 "$(d  180)"
# ~2mo ago: swap 3 ETH back to BTC after ETH drawdown (ratio ~48x, BTC dominance peak)
trade "$P_CR" "$ETH" 3.0   1700 "$BTC"  0.063 81000 "$(d   60)"  3

echo "  Account transfers — moving crypto off the exchange into self-custody"
# ~9mo ago: move 0.4 BTC to cold storage, paying the on-chain network fee in BTC
transfer "$P_CR" "$BTC" 0.4 "$(d  270)" "$ACC_BINANCE" "$ACC_LEDGER" 0.0004
# ~1mo ago: move 3 ETH to cold storage, paying gas in ETH
transfer "$P_CR" "$ETH" 3.0 "$(d   30)" "$ACC_BINANCE" "$ACC_LEDGER" 0.01

echo "  Done."

echo ""
echo "All portfolios seeded:"
echo "  $P_BH  — Bogleheads Three-Fund"
echo "  $P_AT  — Active Trader"
echo "  $P_CR  — Crypto DCA"
