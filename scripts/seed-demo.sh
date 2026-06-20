#!/usr/bin/env bash
set -euo pipefail

API="${API:-http://localhost:8080}"

post() {
    curl -sf -X POST "$API$1" -H "Content-Type: application/json" -d "$2"
}

echo "Creating demo portfolio..."
PORTFOLIO_ID=$(post /portfolios '{"name":"Demo Portfolio"}' | jq -r '.id')
echo "  id=$PORTFOLIO_ID"

echo "Fetching assets..."
ASSETS=$(curl -sf "$API/assets")

listing_id() {
    echo "$ASSETS" | jq -r --arg t "$1" '[.[] | .listings[] | select(.ticker == $t)][0].id'
}

AAPL=$(listing_id AAPL)
VWCE=$(listing_id VWCE)
BTC=$(listing_id BTC)
NVDA=$(listing_id NVDA)

txn() {
    local listing_id=$1 type=$2 qty=$3 price=$4 date=$5 fx_rate=${6:-}
    local body="{\"listingId\":$listing_id,\"type\":\"$type\",\"quantity\":$qty,\"price\":$price,\"date\":\"$date\""
    [[ -n "$fx_rate" ]] && body="$body,\"fxRate\":$fx_rate"
    body="$body}"
    post "/portfolios/$PORTFOLIO_ID/transactions" "$body" > /dev/null
    echo "  $type $qty @ $price on $date${fx_rate:+ (fx=$fx_rate)}"
}

echo "Recording transactions..."
#                                                         EUR/USD rate
txn "$AAPL" BUY  10    150.00 2025-01-15  1.0298
txn "$VWCE" BUY  20     90.00 2025-02-20
txn "$BTC"  BUY   0.5 25000   2025-03-01  1.0812
txn "$AAPL" BUY   5    175.00 2025-06-10  1.1287
txn "$VWCE" BUY  10     95.00 2025-09-05
txn "$AAPL" SELL  3    190.00 2026-01-10  1.0241
txn "$NVDA" BUY   8    480.00 2026-01-20  1.0412

echo "Done — demo portfolio seeded."
