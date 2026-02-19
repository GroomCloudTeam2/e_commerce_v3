#!/usr/bin/env bash
set -euo pipefail

# Required
TOKEN="${TOKEN:-}"

# Ingress base URL (example: https://api-dev.example.com)
INGRESS_BASE_URL="${INGRESS_BASE_URL:-}"

# Per-service path prefixes behind ingress.
# Examples:
#   PRODUCT_PREFIX="/product"
#   CART_PREFIX="/cart"
#   ORDER_PREFIX="/order"
#   PAYMENT_PREFIX="/payment"
PRODUCT_PREFIX="${PRODUCT_PREFIX:-}"
CART_PREFIX="${CART_PREFIX:-}"
ORDER_PREFIX="${ORDER_PREFIX:-}"
PAYMENT_PREFIX="${PAYMENT_PREFIX:-}"

# Optional direct per-service base URLs (fallback when ingress is not used)
PRODUCT_BASE_URL="${PRODUCT_BASE_URL:-}"
CART_BASE_URL="${CART_BASE_URL:-}"
ORDER_BASE_URL="${ORDER_BASE_URL:-}"
PAYMENT_BASE_URL="${PAYMENT_BASE_URL:-}"

# Optional per-service Host header
# (useful when all services share one gateway address and route by Host)
PRODUCT_HOST="${PRODUCT_HOST:-}"
CART_HOST="${CART_HOST:-}"
ORDER_HOST="${ORDER_HOST:-}"
PAYMENT_HOST="${PAYMENT_HOST:-}"

# Optional: real paymentKey from Toss success callback
PAYMENT_KEY="${PAYMENT_KEY:-}"

if [[ -z "$TOKEN" ]]; then
  echo "ERROR: TOKEN is required"
  exit 1
fi

normalize_prefix() {
  local p="${1:-}"
  if [[ -z "$p" ]]; then
    echo ""
    return
  fi
  [[ "$p" == /* ]] || p="/$p"
  p="${p%/}"
  echo "$p"
}

build_base_url() {
  local ingress="$1"
  local prefix="$2"
  local direct="$3"
  if [[ -n "$direct" ]]; then
    echo "${direct%/}"
    return
  fi
  if [[ -z "$ingress" ]]; then
    echo ""
    return
  fi
  echo "${ingress%/}${prefix}"
}

PRODUCT_PREFIX="$(normalize_prefix "$PRODUCT_PREFIX")"
CART_PREFIX="$(normalize_prefix "$CART_PREFIX")"
ORDER_PREFIX="$(normalize_prefix "$ORDER_PREFIX")"
PAYMENT_PREFIX="$(normalize_prefix "$PAYMENT_PREFIX")"

PRODUCT_BASE_URL="$(build_base_url "$INGRESS_BASE_URL" "$PRODUCT_PREFIX" "$PRODUCT_BASE_URL")"
CART_BASE_URL="$(build_base_url "$INGRESS_BASE_URL" "$CART_PREFIX" "$CART_BASE_URL")"
ORDER_BASE_URL="$(build_base_url "$INGRESS_BASE_URL" "$ORDER_PREFIX" "$ORDER_BASE_URL")"
PAYMENT_BASE_URL="$(build_base_url "$INGRESS_BASE_URL" "$PAYMENT_PREFIX" "$PAYMENT_BASE_URL")"

for name in PRODUCT_BASE_URL CART_BASE_URL ORDER_BASE_URL PAYMENT_BASE_URL; do
  if [[ -z "${!name}" ]]; then
    echo "ERROR: $name is empty. Set INGRESS_BASE_URL + prefix or direct base URL."
    exit 1
  fi
done

AUTH_HEADER="Authorization: Bearer ${TOKEN}"
CT_HEADER="Content-Type: application/json"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
LAST_CODE=""
LAST_BODY_FILE="$TMP_DIR/body.json"

call() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local host_header="${4:-}"
  local extra_host_args=()
  if [[ -n "$host_header" ]]; then
    extra_host_args=(-H "Host: ${host_header}")
  fi

  if [[ -n "$body" ]]; then
    LAST_CODE="$(curl -sS -o "$LAST_BODY_FILE" -w "%{http_code}" -X "$method" "$url" \
      -H "$AUTH_HEADER" -H "$CT_HEADER" "${extra_host_args[@]}" -d "$body")"
  else
    LAST_CODE="$(curl -sS -o "$LAST_BODY_FILE" -w "%{http_code}" -X "$method" "$url" \
      -H "$AUTH_HEADER" "${extra_host_args[@]}")"
  fi

  echo "[$method] $url -> $LAST_CODE"
  if [[ -n "$host_header" ]]; then
    echo "Host: $host_header"
  fi
  cat "$LAST_BODY_FILE" || true
  echo
}

json_get() {
  local expr="$1"
  jq -r "$expr" "$LAST_BODY_FILE"
}

echo "=== E2E API Flow Start ==="
echo "PRODUCT_BASE_URL=$PRODUCT_BASE_URL"
echo "CART_BASE_URL=$CART_BASE_URL"
echo "ORDER_BASE_URL=$ORDER_BASE_URL"
echo "PAYMENT_BASE_URL=$PAYMENT_BASE_URL"
echo

echo "1) 상품 목록 조회"
call GET "${PRODUCT_BASE_URL}/api/v2/products?page=1&size=20" "" "$PRODUCT_HOST"
[[ "$LAST_CODE" == "200" ]] || exit 1
PRODUCT_ID="$(json_get '.content[0].productId // empty')"
[[ -n "$PRODUCT_ID" ]] || { echo "ERROR: 상품이 없습니다."; exit 1; }

echo "2) 상품 상세 조회"
call GET "${PRODUCT_BASE_URL}/api/v2/products/${PRODUCT_ID}" "" "$PRODUCT_HOST"
[[ "$LAST_CODE" == "200" ]] || exit 1
VARIANT_ID="$(json_get '.variants[0].variantId // empty')"
UNIT_PRICE="$(json_get '.variants[0].price // .price // 0')"
TITLE="$(json_get '.title // ""')"
THUMB="$(json_get '.thumbnailUrl // ""')"
OPTION_NAME="$(json_get '.variants[0].optionName // ""')"
[[ "$UNIT_PRICE" != "0" ]] || { echo "ERROR: unitPrice를 찾지 못했습니다."; exit 1; }
echo "selected productId=$PRODUCT_ID variantId=$VARIANT_ID unitPrice=$UNIT_PRICE"
echo

echo "3) 장바구니 담기"
ADD_BODY="$(jq -nc --arg p "$PRODUCT_ID" --arg v "$VARIANT_ID" \
  '{productId:$p, variantId:(if $v=="" then null else $v end), quantity:1}')"
call POST "${CART_BASE_URL}/api/v2/cart" "$ADD_BODY" "$CART_HOST"
[[ "$LAST_CODE" == "201" ]] || exit 1

echo "4) 장바구니 조회"
call GET "${CART_BASE_URL}/api/v2/cart" "" "$CART_HOST"
[[ "$LAST_CODE" == "200" ]] || exit 1
CART_SNAPSHOT_FILE="$TMP_DIR/cart_snapshot.json"
cp "$LAST_BODY_FILE" "$CART_SNAPSHOT_FILE"
echo "cart count=$(jq 'length' "$CART_SNAPSHOT_FILE")"
echo

echo "5) 주문 생성"
ORDER_BODY="$(jq -nc \
  --arg p "$PRODUCT_ID" \
  --arg v "$VARIANT_ID" \
  --arg t "$TITLE" \
  --arg th "$THUMB" \
  --arg on "$OPTION_NAME" \
  --argjson up "$UNIT_PRICE" \
  '{addressId:null,totalAmount:$up,items:[{productId:$p,variantId:(if $v=="" then null else $v end),quantity:1,productTitle:$t,productThumbnail:$th,optionName:$on,unitPrice:$up}]}' )"
call POST "${ORDER_BASE_URL}/api/v2/orders" "$ORDER_BODY" "$ORDER_HOST"
[[ "$LAST_CODE" == "200" ]] || exit 1
ORDER_ID="$(json_get '.orderId // empty')"
[[ -n "$ORDER_ID" ]] || { echo "ERROR: orderId를 받지 못했습니다."; exit 1; }
echo "orderId=$ORDER_ID"
echo

echo "6) 결제 ready (이벤트 전파 대기)"
READY_BODY="$(jq -nc --arg oid "$ORDER_ID" --argjson amt "$UNIT_PRICE" '{orderId:$oid,amount:$amt}')"
READY_OK=0
for _ in {1..10}; do
  call POST "${PAYMENT_BASE_URL}/api/v2/payments/ready" "$READY_BODY" "$PAYMENT_HOST"
  if [[ "$LAST_CODE" == "200" ]]; then
    READY_OK=1
    break
  fi
  sleep 1
done
[[ "$READY_OK" == "1" ]] || { echo "ERROR: payments/ready 실패"; exit 1; }
echo

echo "7) 결제 confirm"
if [[ -n "$PAYMENT_KEY" ]]; then
  CONFIRM_BODY="$(jq -nc --arg pk "$PAYMENT_KEY" --arg oid "$ORDER_ID" --argjson amt "$UNIT_PRICE" \
    '{paymentKey:$pk,orderId:$oid,amount:$amt}')"
  call POST "${PAYMENT_BASE_URL}/api/v2/payments/confirm" "$CONFIRM_BODY" "$PAYMENT_HOST"
  [[ "$LAST_CODE" == "200" ]] || exit 1
else
  echo "PAYMENT_KEY 미설정: confirm은 스킵합니다."
  echo
fi

echo "8) 장바구니 비우기"
DELETE_BODY="$(jq '[.[] | {productId, variantId}]' "$CART_SNAPSHOT_FILE")"
call DELETE "${CART_BASE_URL}/api/v2/cart/items/bulk" "$DELETE_BODY" "$CART_HOST"
[[ "$LAST_CODE" == "204" ]] || exit 1

echo "9) 장바구니 비우기 확인"
call GET "${CART_BASE_URL}/api/v2/cart" "" "$CART_HOST"
[[ "$LAST_CODE" == "200" ]] || exit 1
echo "final cart count=$(jq 'length' "$LAST_BODY_FILE")"
echo
echo "=== E2E API Flow Done ==="
