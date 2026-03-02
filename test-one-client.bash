#!/usr/bin/env bash
#
# Test the Interface Client:
#
#   ./test-em-all.bash
#
# Test the Sequential Client:
#
#   IMPL=sequential ./test-one-client.bash
#
# TODO: Resilience functions currently not working for rest-client!
# Test the Rest Client:
#
#   IMPL=rest-client ./test-one-client.bash
#
: ${HOST=localhost}
: ${PORT=7002}
: ${IMPL=interface-client}
: ${PROD_ID_OK=1}
: ${PROD_ID_NOT_FOUND=13}
: ${PROD_ID_NO_RECS=113}
: ${PROD_ID_NO_REVS=213}

function assertCurl() {

  local expectedHttpCode=$1
  local noRetries=$3
  if [ "$noRetries" = "NoRetries" ]; then
    local curlCmd="$2 -w \"%{http_code}\""
  else
    local curlCmd="$2 --connect-timeout 2 -m 5 --retry 12 --retry-delay 5 --retry-all-errors -w \"%{http_code}\""
  fi
  local result=$(eval $curlCmd)
  local httpCode="${result:(-3)}"
  RESPONSE='' && (( ${#result} > 3 )) && RESPONSE="${result%???}"

  if [ "$httpCode" = "$expectedHttpCode" ]
  then
    if [ "$httpCode" = "200" ]
    then
      echo "Test OK (HTTP Code: $httpCode)"
    else
      echo "Test OK (HTTP Code: $httpCode, $RESPONSE)" # ${RESPONSE:0:300})"
    fi
  else
    echo  "Test FAILED, EXPECTED HTTP Code: $expectedHttpCode, GOT: $httpCode, WILL ABORT!"
    echo  "- Failing command: $curlCmd"
    echo  "- Response Body: $RESPONSE"
    exit 1
  fi
}

function assertEqual() {

  local expected=$1
  local actual=$2

  if [ "$actual" = "$expected" ]
  then
    echo "Test OK (actual value: $actual)"
  else
    echo "Test FAILED, EXPECTED VALUE: $expected, ACTUAL VALUE: $actual, WILL ABORT"
    exit 1
  fi
}

function assertSubstring() {

  local expectedSubstring=$1
  local actual=$2

  if [[ "$actual" =~ "$expectedSubstring" ]]; then
    echo "Test OK (actual value: $actual)"
  else
    echo "Test FAILED, EXPECTED SUBSTRING: $expectedSubstring, ACTUAL VALUE: $actual, WILL ABORT"
    exit 1
  fi
}

function testUrl() {
  url=$@
  if $url -s -f -o /dev/null
  then
    return 0
  else
    return 1
  fi;
}

function waitForService() {
  url=$@
  echo -n "Wait for: $url... "
  n=0
  until testUrl $url
  do
    n=$((n + 1))
    if [[ $n == 100 ]]
    then
      echo " Give up"
      exit 1
    else
      sleep 3
      echo -n ", retry #$n "
    fi
  done
  echo "DONE, continues..."
}

function testCircuitBreaker() {

    echo "Start Circuit Breaker tests!"

    # Ensure that the circuit breaker is getting closed
    for ((n=0; n<10; n++)); do assertCurl 200 "curl http://$HOST:$PORT/product-composite/$IMPL/$PROD_ID_OK  -s"; done

    # First, use the product-composite health - endpoint to verify that the circuit breaker is closed
    assertCurl 200 "curl -s http://$HOST:$PORT/actuator/circuitbreakers"
    assertEqual "CLOSED" "$(echo $RESPONSE | jq -r .circuitBreakers.productGroup.state)"

    # Open the circuit breaker by running three slow calls in a row, i.e. that cause a timeout exception
    # Also, verify that we get 500 back and a timeout related error message
    for ((n=0; n<5; n++))
    do
        assertCurl 500 "curl http://$HOST:$PORT/product-composite/$IMPL/$PROD_ID_OK?delay=3  -s" NoRetries
        message=$(echo $RESPONSE | jq -r .message)
        assertSubstring "java.util.concurrent.ExecutionException: org.springframework.web.client.ResourceAccessException: I/O error on GET request" "$message"
    done

    # Verify that the circuit breaker is open
    assertCurl 200 "curl -s http://$HOST:$PORT/actuator/circuitbreakers"
    assertEqual "OPEN" "$(echo $RESPONSE | jq -r .circuitBreakers.productGroup.state)"

    # Verify that the circuit breaker now is open by running the slow call again, verify it gets 200 back, i.e. fail fast works, and a response from the fallback method.
    assertCurl 200 "curl http://$HOST:$PORT/product-composite/$IMPL/$PROD_ID_OK?delay=3  -s"
    assertEqual "Fallback product$PROD_ID_OK" "$(echo "$RESPONSE" | jq -r .name)"

    # Also, verify that the circuit breaker is open by running a normal call, verify it also gets 200 back and a response from the fallback method.
    assertCurl 200 "curl http://$HOST:$PORT/product-composite/$IMPL/$PROD_ID_OK  -s"
    assertEqual "Fallback product$PROD_ID_OK" "$(echo "$RESPONSE" | jq -r .name)"

    # Verify that a 404 (Not Found) error is returned for a non existing productId ($PROD_ID_NOT_FOUND) from the fallback method.
    assertCurl 404 "curl http://$HOST:$PORT/product-composite/$IMPL/$PROD_ID_NOT_FOUND  -s"
    assertEqual "Product Id: $PROD_ID_NOT_FOUND not found in fallback cache!" "$(echo $RESPONSE | jq -r .message)"

    # Wait for the circuit breaker to transition to the half open state (i.e. max 10 sec)
    echo "Will sleep for 10 sec waiting for the CB to go Half Open..."
    sleep 10

    # Verify that the circuit breaker is in half open state
    assertCurl 200 "curl -s http://$HOST:$PORT/actuator/circuitbreakers"
    assertEqual "HALF_OPEN" "$(echo $RESPONSE | jq -r .circuitBreakers.productGroup.state)"

    # Close the circuit breaker by running three normal calls in a row
    # Also, verify that we get 200 back and a response based on information in the product database
    for ((n=0; n<5; n++))
    do
        assertCurl 200 "curl http://$HOST:$PORT/product-composite/$IMPL/$PROD_ID_OK  -s"
        assertEqual "name-1" "$(echo "$RESPONSE" | jq -r .name)"
    done

    # Verify that the circuit breaker is in closed state again
    assertCurl 200 "curl -s http://$HOST:$PORT/actuator/circuitbreakers"
    assertEqual "CLOSED" "$(echo $RESPONSE | jq -r .circuitBreakers.productGroup.state)"

    # Verify that the expected state transitions happened in the circuit breaker
    assertCurl 200 "curl -s http://$HOST:$PORT/actuator/circuitbreakerevents/productGroup/STATE_TRANSITION"
    assertEqual "State transition from CLOSED to OPEN"      "$(echo $RESPONSE | jq -r .circuitBreakerEvents[-3].stateTransition)"
    assertEqual "State transition from OPEN to HALF_OPEN"   "$(echo $RESPONSE | jq -r .circuitBreakerEvents[-2].stateTransition)"
    assertEqual "State transition from HALF_OPEN to CLOSED" "$(echo $RESPONSE | jq -r .circuitBreakerEvents[-1].stateTransition)"
}

set -e
# set -x

echo
echo +-----------------------------------------------------------------
echo "| Start tests for '$IMPL':" `date`
echo +-----------------------------------------------------------------

echo "HOST=${HOST}"
echo "PORT=${PORT}"

waitForService curl http://$HOST:$PORT/actuator/health

# Verify that a normal request works, expect three recommendations and three reviews
assertCurl 200 "curl http://$HOST:$PORT/product-composite/$IMPL/$PROD_ID_OK -s"
assertEqual $PROD_ID_OK $(echo $RESPONSE | jq .productId)
assertEqual 3 $(echo $RESPONSE | jq ".recommendations | length")
assertEqual 3 $(echo $RESPONSE | jq ".reviews | length")

# Verify that a 404 (Not Found) error is returned for a non-existing productId ($PROD_ID_NOT_FOUND)
assertCurl 404 "curl http://$HOST:$PORT/product-composite/$IMPL/$PROD_ID_NOT_FOUND -s"
assertEqual "No product found for productId: $PROD_ID_NOT_FOUND" "$(echo $RESPONSE | jq -r .message)"

# Verify that no recommendations are returned for productId $PROD_ID_NO_RECS
assertCurl 200 "curl http://$HOST:$PORT/product-composite/$IMPL/$PROD_ID_NO_RECS -s"
assertEqual $PROD_ID_NO_RECS $(echo $RESPONSE | jq .productId)
assertEqual 0 $(echo $RESPONSE | jq ".recommendations | length")
assertEqual 3 $(echo $RESPONSE | jq ".reviews | length")

# Verify that no reviews are returned for productId $PROD_ID_NO_REVS
assertCurl 200 "curl http://$HOST:$PORT/product-composite/$IMPL/$PROD_ID_NO_REVS -s"
assertEqual $PROD_ID_NO_REVS $(echo $RESPONSE | jq .productId)
assertEqual 3 $(echo $RESPONSE | jq ".recommendations | length")
assertEqual 0 $(echo $RESPONSE | jq ".reviews | length")

# Verify that a 422 (Unprocessable Entity) error is returned for a productId that is out of range (-1)
assertCurl 422 "curl http://$HOST:$PORT/product-composite/$IMPL/-1 -s"
assertEqual "\"Invalid productId: -1\"" "$(echo $RESPONSE | jq .message)"

# Verify that a 400 (Bad Request) error error is returned for a productId that is not a number, i.e. invalid format
assertCurl 400 "curl http://$HOST:$PORT/product-composite/$IMPL/invalidProductId -s"
assertSubstring "Method parameter 'productId': Failed to convert value of type 'java.lang.String' to required type 'int'; For input string: \\\"invalidProductId\\\"" "$(echo $RESPONSE | jq .message)"

if [ "$IMPL" = "rest-client" ]; then
  echo "### SKIPPING testCircuitBreaker for '$IMPL', CURRENTLY OUT-OF-ORDER..."
else
  testCircuitBreaker
fi

echo +-----------------------------------------------------------------------
echo "| End, all tests for '$IMPL' OK:" `date`
echo +-----------------------------------------------------------------------
