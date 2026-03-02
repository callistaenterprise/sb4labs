#!/usr/bin/env bash

set -e
# set -x

IMPL=sequential ./test-one-client.bash
IMPL=interface-client ./test-one-client.bash
IMPL=rest-client ./test-one-client.bash
